package com.kcvisuals.module.visual;

import com.kcvisuals.gui.Theme;
import com.kcvisuals.module.Module;
import com.kcvisuals.module.setting.BoolSetting;
import com.kcvisuals.module.setting.ColorSetting;
import com.kcvisuals.module.setting.ModeSetting;
import com.kcvisuals.module.setting.NumberSetting;
import com.kcvisuals.render.Shapes;
import com.kcvisuals.render.WorldRender;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.player.PlayerEntity;

/** Expanding, fading gradient rings at the feet when a player jumps or lands. */
public class JumpCircles extends Module implements WorldRender.Renderer, WorldRender.Ticker {
    public static final int MAX_CIRCLES = 32;
    public static final String MODE_OWN = "Nur eigene";
    public static final String MODE_ALL = "Alle Spieler";
    public static final int PINK = 0xFFFF5CC8;

    public final ColorSetting color1 = add(new ColorSetting("color1", "Farbe 1", Theme.ACCENT));
    public final ColorSetting color2 = add(new ColorSetting("color2", "Farbe 2", PINK));
    public final NumberSetting radius = add(new NumberSetting("radius", "Radius", 1.2, 0.4, 3.0, 0.1));
    public final NumberSetting duration = add(new NumberSetting("duration", "Dauer (s)", 0.6, 0.2, 2.0, 0.1));
    public final BoolSetting onLand = add(new BoolSetting("on_land", "Auch beim Landen", true));
    public final ModeSetting mode = add(new ModeSetting("mode", "Spieler", MODE_OWN, MODE_OWN, MODE_ALL));

    // Ring buffer of active circles (no per-frame allocation).
    private final double[] cx = new double[MAX_CIRCLES], cy = new double[MAX_CIRCLES], cz = new double[MAX_CIRCLES];
    private final long[] start = new long[MAX_CIRCLES];
    private int next;
    private final Map<UUID, Boolean> lastOnGround = new HashMap<>();
    private Object lastWorld;

    private void clearCircles() {
        java.util.Arrays.fill(start, 0L);
        lastOnGround.clear();
    }

    public JumpCircles() {
        super("jump_circles", "Sprungkreise", "Leuchtende Ringe an den Füßen beim Springen und Landen.");
    }

    @Override
    public void onDisable() {
        clearCircles();
        lastWorld = null;
    }

    /** Adds a circle at the given feet position (also used for previews). */
    public void spawn(double x, double y, double z) {
        int i = next;
        next = (next + 1) % MAX_CIRCLES;
        cx[i] = x;
        cy[i] = y;
        cz[i] = z;
        start[i] = System.nanoTime();
    }

    @Override
    public void tick(MinecraftClient mc) {
        if (mc.world != lastWorld) {
            // disconnect, respawn into another dimension or server switch: drop circles from the old world
            lastWorld = mc.world;
            clearCircles();
        }
        if (mc.world == null || mc.player == null) {
            lastOnGround.clear();
            return;
        }
        if (MODE_ALL.equals(mode.get())) {
            for (PlayerEntity p : mc.world.getPlayers()) {
                // Only players the client renders anyway (not invisible to us); drawn depth-tested.
                if (p == mc.player || !p.isInvisibleTo(mc.player)) {
                    observe(p);
                }
            }
            if (lastOnGround.size() > 256) {
                lastOnGround.clear();
            }
        } else {
            observe(mc.player);
        }
    }

    private void observe(PlayerEntity p) {
        boolean ground = p.isOnGround();
        Boolean was = lastOnGround.put(p.getUuid(), ground);
        if (was == null || p.hasVehicle() || p.getAbilities().flying) {
            return;
        }
        if (was && !ground && p.getVelocity().y > 0.1) {
            spawn(p.getX(), p.getY(), p.getZ());
        } else if (!was && ground && onLand.get()) {
            spawn(p.getX(), p.getY(), p.getZ());
        }
    }

    @Override
    public void renderWorld(WorldRender.Frame f) {
        long now = System.nanoTime();
        double durNs = duration.get() * 1e9;
        float maxR = radius.getFloat();
        int a = color1.get(), b = color2.get();
        VertexConsumer vc = null;
        for (int i = 0; i < MAX_CIRCLES; i++) {
            if (start[i] == 0L) {
                continue;
            }
            double t = (now - start[i]) / durNs;
            if (t >= 1.0) {
                start[i] = 0L;
                continue;
            }
            if (vc == null) {
                vc = f.consumers.getBuffer(Shapes.quads());
            }
            float e = (float) (1 - Math.pow(1 - t, 3)); // ease-out cubic
            float fade = (float) (1 - t);
            float r = Math.max(0.05f, maxR * e);
            float w = 0.09f + 0.05f * fade;
            float x = f.rx(cx[i]), y = f.ry(cy[i] + 0.02), z = f.rz(cz[i]);
            // soft glow outside, bright core, soft glow inside
            Shapes.ring(vc, x, y, z, r, r + w * 2.2f, a, b, fade * 0.55f, 0f);
            Shapes.ring(vc, x, y, z, r - w, r, a, b, fade, fade * 0.55f);
            Shapes.ring(vc, x, y, z, Math.max(0f, r - w * 3f), r - w, a, b, 0f, fade);
        }
    }
}
