package com.kcvisuals.module.visual;

import com.kcvisuals.KCVisuals;
import com.kcvisuals.module.Module;
import com.kcvisuals.module.setting.ColorSetting;
import com.kcvisuals.module.setting.ModeSetting;
import com.kcvisuals.module.setting.NumberSetting;
import com.kcvisuals.render.Shapes;
import com.kcvisuals.render.WorldRender;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

/**
 * Client-only sprite particles (hearts, stars or sparks) around an entity the player attacks. Drawn as billboarded,
 * depth-tested textured quads from the world render hook; nothing is sent to the server.
 */
public class HitParticles extends Module implements WorldRender.Renderer {
    public static final String MODE_HEARTS = "Herzen";
    public static final String MODE_STARS = "Sterne";
    public static final String MODE_SPARKS = "Funken";
    public static final int MAX_PARTICLES = 128;
    private static final Identifier HEART = Identifier.of(KCVisuals.MOD_ID, "textures/particle/heart.png");
    private static final Identifier STAR = Identifier.of(KCVisuals.MOD_ID, "textures/particle/star.png");
    private static final Identifier SPARK = Identifier.of(KCVisuals.MOD_ID, "textures/particle/spark.png");
    private static final float GRAVITY = -2.2f; // blocks/s²

    public final NumberSetting count = add(new NumberSetting("count", "Anzahl", 6, 1, 20, 1));
    public final ModeSetting mode = add(new ModeSetting("mode", "Art", MODE_HEARTS, MODE_HEARTS, MODE_STARS, MODE_SPARKS));
    public final ColorSetting color = add(new ColorSetting("color", "Farbe", JumpCircles.PINK));

    // Ring buffer: start position, velocity (blocks/s), size, lifetime (s), start time.
    private final double[] px = new double[MAX_PARTICLES], py = new double[MAX_PARTICLES], pz = new double[MAX_PARTICLES];
    private final float[] vx = new float[MAX_PARTICLES], vy = new float[MAX_PARTICLES], vz = new float[MAX_PARTICLES];
    private final float[] psize = new float[MAX_PARTICLES], life = new float[MAX_PARTICLES];
    private final long[] start = new long[MAX_PARTICLES];
    private int next;
    private Object lastWorld;

    public HitParticles() {
        super("hit_particles", "Trefferpartikel", "Herzen, Sterne oder Funken um das Ziel, wenn du zuschlägst.");
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (enabled() && world.isClient() && player == mc.player) {
                burst(entity);
            }
            return ActionResult.PASS;
        });
    }

    @Override
    public void onDisable() {
        java.util.Arrays.fill(start, 0L);
    }

    /** Spawns {@link #count} particles around the entity's body. */
    public void burst(Entity target) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        lastWorld = target.getEntityWorld();
        double cx = target.getX(), cz = target.getZ();
        double cy = target.getY() + target.getHeight() * 0.6;
        double r = Math.max(0.3, target.getWidth() * 0.6);
        int n = count.getInt();
        for (int k = 0; k < n; k++) {
            int i = next;
            next = (next + 1) % MAX_PARTICLES;
            double a = rnd.nextDouble() * Math.PI * 2;
            float dx = (float) Math.cos(a), dz = (float) Math.sin(a);
            px[i] = cx + dx * r;
            py[i] = cy + (rnd.nextDouble() - 0.5) * target.getHeight() * 0.5;
            pz[i] = cz + dz * r;
            float speed = 0.6f + rnd.nextFloat() * 0.8f;
            vx[i] = dx * speed;
            vz[i] = dz * speed;
            vy[i] = 1.2f + rnd.nextFloat() * 1.0f;
            psize[i] = 0.12f + rnd.nextFloat() * 0.08f;
            life[i] = 0.7f + rnd.nextFloat() * 0.4f;
            start[i] = System.nanoTime();
        }
    }

    private Identifier texture() {
        return switch (mode.get()) {
            case MODE_STARS -> STAR;
            case MODE_SPARKS -> SPARK;
            default -> HEART;
        };
    }

    @Override
    public void renderWorld(WorldRender.Frame f) {
        if (f.mc.world != lastWorld) {
            java.util.Arrays.fill(start, 0L); // particles never carry over into another world
            lastWorld = f.mc.world;
            return;
        }
        long now = System.nanoTime();
        int c = color.get();
        MatrixStack m = f.matrices;
        VertexConsumer vc = null;
        for (int i = 0; i < MAX_PARTICLES; i++) {
            if (start[i] == 0L) {
                continue;
            }
            float s = (now - start[i]) / 1e9f;
            float t = s / life[i];
            if (t >= 1f) {
                start[i] = 0L;
                continue;
            }
            if (vc == null) {
                vc = f.consumers.getBuffer(RenderLayers.text(texture()));
            }
            float x = f.rx(px[i] + vx[i] * s);
            float y = f.ry(py[i] + vy[i] * s + 0.5 * GRAVITY * s * s);
            float z = f.rz(pz[i] + vz[i] * s);
            float fade = t < 0.6f ? 1f : 1f - (t - 0.6f) / 0.4f;
            float size = psize[i] * (t < 0.1f ? t / 0.1f : 1f);
            int col = Shapes.alpha(c, fade);
            m.push();
            m.translate(x, y, z);
            m.multiply(f.mc.gameRenderer.getCamera().getRotation());
            m.scale(size, -size, size);
            Matrix4f pose = m.peek().getPositionMatrix();
            int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
            // Same orientation and winding as glyph quads drawn with this billboard transform.
            vc.vertex(pose, -0.5f, -0.5f, 0f).color(col).texture(0f, 0f).light(light);
            vc.vertex(pose, -0.5f, 0.5f, 0f).color(col).texture(0f, 1f).light(light);
            vc.vertex(pose, 0.5f, 0.5f, 0f).color(col).texture(1f, 1f).light(light);
            vc.vertex(pose, 0.5f, -0.5f, 0f).color(col).texture(1f, 0f).light(light);
            m.pop();
        }
    }
}
