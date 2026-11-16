package com.kcvisuals.module.visual;

import com.kcvisuals.module.Module;
import com.kcvisuals.module.setting.BoolSetting;
import com.kcvisuals.module.setting.NumberSetting;
import com.kcvisuals.render.ParticleBudget;
import com.kcvisuals.render.ScreenMath;

/**
 * Small screen tweaks: lower/hide the first-person fire overlay, shrink the totem pop item, hide the carved pumpkin
 * overlay, disable the hurt-cam tilt and thin out explosion/crystal particles. Status effect visuals (blindness,
 * darkness, nausea) are not touched.
 */
public class RenderTweaks extends Module {
    private static RenderTweaks instance;

    public final BoolSetting fireOff = add(new BoolSetting("fire_off", "Feuer-Overlay aus", false));
    public final NumberSetting fireHeight = add(new NumberSetting("fire_height", "Feuer-Höhe (%)", 40, 0, 100, 5));
    public final NumberSetting totemScale = add(new NumberSetting("totem_scale", "Totem-Animation (%)", 50, 0, 100, 5));
    public final BoolSetting pumpkinOff = add(new BoolSetting("pumpkin_off", "Kürbis-Overlay aus", true));
    public final BoolSetting hurtCamOff = add(new BoolSetting("hurt_cam_off", "Wackeln bei Schaden aus", true));
    public final NumberSetting explosionParticles = add(new NumberSetting("explosion_particles", "Explosionspartikel (%)", 50, 0, 100, 5));

    private final ParticleBudget explosionBudget = new ParticleBudget();
    private final ParticleBudget blockBudget = new ParticleBudget();

    public RenderTweaks() {
        super("render_tweaks", "Render-Tweaks", "Feuer, Totem, Kürbis, Wackeln bei Schaden und Explosionspartikel.");
        instance = this;
    }

    public static RenderTweaks instance() {
        return instance;
    }

    private static RenderTweaks on() {
        RenderTweaks r = instance;
        return r != null && r.enabled() ? r : null;
    }

    /** Fire overlay: true = draw nothing. */
    public static boolean hideFire() {
        RenderTweaks r = on();
        return r != null && (r.fireOff.get() || r.fireHeight.get() <= 0);
    }

    /** Vertical offset for the fire overlay (0 while disabled). */
    public static float fireDrop() {
        RenderTweaks r = on();
        return r == null ? 0f : ScreenMath.fireDrop(r.fireHeight.get() / 100.0);
    }

    /** Totem pop item scale, 1 while disabled. */
    public static float totemScale() {
        RenderTweaks r = on();
        return r == null ? 1f : (float) (r.totemScale.get() / 100.0);
    }

    public static boolean hidePumpkin() {
        RenderTweaks r = on();
        return r != null && r.pumpkinOff.get();
    }

    public static boolean hurtCamOff() {
        RenderTweaks r = on();
        return r != null && r.hurtCamOff.get();
    }

    private double particleMultiplier() {
        return explosionParticles.get() / 100.0;
    }

    /** Whether one explosion particle may spawn. */
    public static boolean allowExplosionParticle() {
        RenderTweaks r = on();
        return r == null || r.explosionBudget.allow(r.particleMultiplier());
    }

    /** Scaled count of explosion block particles. */
    public static int explosionBlockParticles(int count) {
        RenderTweaks r = on();
        return r == null ? count : r.blockBudget.take(count, r.particleMultiplier());
    }

    @Override
    public void onDisable() {
        explosionBudget.reset();
        blockBudget.reset();
    }
}
