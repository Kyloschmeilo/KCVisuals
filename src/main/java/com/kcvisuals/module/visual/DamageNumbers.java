package com.kcvisuals.module.visual;

import com.kcvisuals.gui.Theme;
import com.kcvisuals.module.Module;
import com.kcvisuals.module.setting.ColorSetting;
import com.kcvisuals.module.setting.NumberSetting;
import com.kcvisuals.render.HitMath;
import com.kcvisuals.render.Shapes;
import com.kcvisuals.render.WorldRender;
import com.kcvisuals.render.HealthBaseline;
import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Floating "-x.xx" numbers when a nearby living entity loses health. Uses only the health the client already receives
 * (synced entity data), within {@link #RANGE} blocks, and only for entities the player can currently see: not
 * invisible, inside the camera frustum and in line of sight. A hit on an entity that is hidden (behind a wall, out of
 * view) is never shown, also not later (its baseline is dropped while hidden). The text is depth tested.
 */
public class DamageNumbers extends Module implements WorldRender.Renderer, WorldRender.Ticker {
    public static final double RANGE = 32.0;
    public static final int MAX_NUMBERS = 48;
    public static final long LIFETIME_NS = 1_000_000_000L;
    public static final int RED = 0xFFFF3B3B;
    /** Line-of-sight refresh interval per entity, in ticks. */
    public static final int LOS_INTERVAL = 3;

    public final ColorSetting color1 = add(new ColorSetting("color1", "Farbe 1", RED));
    public final ColorSetting color2 = add(new ColorSetting("color2", "Farbe 2", Theme.ACCENT));
    public final NumberSetting size = add(new NumberSetting("size", "Größe", 1.0, 0.5, 2.0, 0.1));

    /** Single-character strings for ASCII, so the per-character gradient does not allocate every frame. */
    private static final String[] CHARS = new String[128];

    static {
        for (int c = 0; c < CHARS.length; c++) {
            CHARS[c] = String.valueOf((char) c);
        }
    }

    private static String charString(char c) {
        return c < CHARS.length ? CHARS[c] : String.valueOf(c);
    }

    // Ring buffer of active numbers.
    private final String[] text = new String[MAX_NUMBERS];
    private final double[] nx = new double[MAX_NUMBERS], ny = new double[MAX_NUMBERS], nz = new double[MAX_NUMBERS];
    private final long[] start = new long[MAX_NUMBERS];
    private int next;

    private final HealthBaseline baseline = new HealthBaseline();
    // Cached line-of-sight results of the last tick (only entities in range and view); swapped every tick.
    private Int2BooleanOpenHashMap los = new Int2BooleanOpenHashMap();
    private Int2BooleanOpenHashMap losNext = new Int2BooleanOpenHashMap();
    private int tickCount;
    /** Culling frustum of the last rendered frame (captured at the end of world extraction). */
    private Frustum frustum;
    private Object lastWorld;

    public DamageNumbers() {
        super("damage_numbers", "Schadenszahlen", "Schwebende Schadenszahlen über getroffenen Wesen.");
        WorldRenderEvents.END_EXTRACTION.register(ctx -> frustum = ctx.frustum());
    }

    @Override
    public void onDisable() {
        java.util.Arrays.fill(start, 0L);
        baseline.clear();
        los.clear();
        lastWorld = null;
        frustum = null;
    }

    /** Adds a number at the given world position (also usable for previews). */
    public void spawn(double x, double y, double z, float amount) {
        int i = next;
        next = (next + 1) % MAX_NUMBERS;
        text[i] = HitMath.formatDamage(amount);
        nx[i] = x;
        ny[i] = y;
        nz[i] = z;
        start[i] = System.nanoTime();
    }

    @Override
    public void tick(MinecraftClient mc) {
        if (mc.world != lastWorld) {
            lastWorld = mc.world;
            java.util.Arrays.fill(start, 0L);
            baseline.clear();
            los.clear();
        }
        if (mc.world == null || mc.player == null) {
            return;
        }
        tickCount++;
        Frustum fr = frustum;
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        EntityRenderManager erm = mc.getEntityRenderDispatcher();
        losNext.clear();
        double r2 = RANGE * RANGE;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le) || e == mc.player || e.squaredDistanceTo(mc.player) > r2) {
                continue;
            }
            int id = e.getId();
            float drop = baseline.observe(id, isVisible(mc, le, id, fr, erm, cam), le.getHealth());
            if (drop > 0f) {
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                double w = e.getWidth() * 0.5;
                spawn(e.getX() + (rnd.nextDouble() - 0.5) * w, e.getY() + e.getHeight() + 0.25,
                        e.getZ() + (rnd.nextDouble() - 0.5) * w, drop);
            }
        }
        baseline.endTick();
        Int2BooleanOpenHashMap t = los;
        los = losNext;
        losNext = t;
    }

    /**
     * Visible to the player right now: not invisible, inside the rendered view (same check the entity renderer uses)
     * and in line of sight. The line-of-sight raycast runs only for entities that pass the cheap checks and is
     * refreshed every {@link #LOS_INTERVAL} ticks per entity (staggered by id).
     */
    private boolean isVisible(MinecraftClient mc, LivingEntity e, int id, Frustum fr, EntityRenderManager erm, Vec3d cam) {
        if (fr == null || e.isInvisibleTo(mc.player) || !erm.shouldRender(e, fr, cam.x, cam.y, cam.z)) {
            return false;
        }
        boolean sees;
        if (los.containsKey(id) && (tickCount + id) % LOS_INTERVAL != 0) {
            sees = los.get(id);
        } else {
            sees = mc.player.canSee(e);
        }
        losNext.put(id, sees);
        return sees;
    }

    @Override
    public void renderWorld(WorldRender.Frame f) {
        long now = System.nanoTime();
        TextRenderer tr = f.mc.textRenderer;
        MatrixStack m = f.matrices;
        int c1 = color1.get(), c2 = color2.get();
        float base = 0.03f * size.getFloat();
        for (int i = 0; i < MAX_NUMBERS; i++) {
            if (start[i] == 0L) {
                continue;
            }
            float t = (float) (now - start[i]) / LIFETIME_NS;
            if (t >= 1f) {
                start[i] = 0L;
                continue;
            }
            float alpha = HitMath.fade(t);
            if (alpha < 0.05f) {
                continue;
            }
            float x = f.rx(nx[i]), y = f.ry(ny[i]) + HitMath.rise(t), z = f.rz(nz[i]);
            float scale = base * HitMath.pop(t);
            m.push();
            m.translate(x, y, z);
            m.multiply(f.mc.gameRenderer.getCamera().getRotation());
            m.scale(scale, -scale, scale);
            Matrix4f pose = m.peek().getPositionMatrix();
            String s = text[i];
            float w = tr.getWidth(s);
            float cx = -w / 2f;
            int n = s.length();
            // Horizontal gradient across the characters, Farbe 1 -> Farbe 2.
            for (int k = 0; k < n; k++) {
                String ch = charString(s.charAt(k));
                int c = Shapes.alpha(Shapes.lerpColor(c1, c2, n == 1 ? 0f : (float) k / (n - 1)), alpha);
                tr.draw(ch, cx, 0f, c, true, pose, f.consumers, TextRenderer.TextLayerType.NORMAL, 0,
                        LightmapTextureManager.MAX_LIGHT_COORDINATE);
                cx += tr.getWidth(ch);
            }
            m.pop();
        }
    }
}
