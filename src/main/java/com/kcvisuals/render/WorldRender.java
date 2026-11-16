package com.kcvisuals.render;

import com.kcvisuals.KCVisuals;
import com.kcvisuals.module.Module;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import java.util.List;

/**
 * Dispatches world rendering to visual modules. Hooked into {@link WorldRenderEvents#AFTER_ENTITIES}: the shared
 * entity {@link VertexConsumerProvider} is flushed by the world renderer right after this event, so everything
 * drawn here is depth tested against terrain and entities (nothing is visible through walls).
 */
public final class WorldRender {
    /** Implemented by modules that draw into the world. */
    public interface Renderer {
        void renderWorld(Frame frame);
    }

    /** Implemented by modules that need a client tick while enabled. */
    public interface Ticker {
        void tick(MinecraftClient mc);
    }

    /** Per-frame state; one mutable instance is reused every frame. */
    public static final class Frame {
        public MinecraftClient mc;
        public MatrixStack matrices;
        public VertexConsumerProvider consumers;
        public double camX, camY, camZ;
        public float tickDelta;

        /** Camera-relative float coordinate helpers. */
        public float rx(double x) {
            return (float) (x - camX);
        }

        public float ry(double y) {
            return (float) (y - camY);
        }

        public float rz(double z) {
            return (float) (z - camZ);
        }
    }

    private static final Frame FRAME = new Frame();
    private static final java.util.Set<Module> FAILED = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    /** Snapshot of the registered modules (registration is complete before {@link #register()}); avoids per-frame copies. */
    private static List<Module> modules = List.of();

    private WorldRender() {}

    public static void register() {
        modules = KCVisuals.modules().all();
        WorldRenderEvents.AFTER_ENTITIES.register(WorldRender::render);
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            for (Module m : modules) {
                if (m instanceof Ticker t && (m.enabled() || alwaysTicks(m) && !FAILED.contains(m))) {
                    try {
                        t.tick(mc);
                        if (m.enabled()) {
                            FAILED.remove(m); // re-enabled by the user and working again
                        }
                    } catch (RuntimeException e) {
                        KCVisuals.LOGGER.error("Fehler im Tick von {}", m.id(), e);
                        FAILED.add(m); // AlwaysTicks modules would otherwise keep failing every tick
                        m.setEnabled(false);
                    }
                }
            }
        });
    }

    /** Modules that must keep observing state while disabled (e.g. the death marker remembers deaths). */
    private static boolean alwaysTicks(Module m) {
        return m instanceof AlwaysTicks;
    }

    /** Marker: {@link Ticker#tick} is called even while the module is disabled. */
    public interface AlwaysTicks {}

    private static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            return;
        }
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        Frame f = FRAME;
        f.mc = mc;
        f.matrices = ctx.matrices();
        f.consumers = ctx.consumers();
        f.camX = cam.x;
        f.camY = cam.y;
        f.camZ = cam.z;
        f.tickDelta = mc.getRenderTickCounter().getTickProgress(true);
        for (Module m : modules) {
            if (m.enabled() && m instanceof Renderer r) {
                try {
                    r.renderWorld(f);
                } catch (RuntimeException e) {
                    KCVisuals.LOGGER.error("Fehler beim Rendern von {}", m.id(), e);
                    m.setEnabled(false);
                }
            }
        }
    }
}
