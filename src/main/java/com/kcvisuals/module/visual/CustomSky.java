package com.kcvisuals.module.visual;

import com.kcvisuals.module.Module;
import com.kcvisuals.module.setting.BoolSetting;
import com.kcvisuals.module.setting.ColorSetting;
import com.kcvisuals.module.setting.NumberSetting;

/**
 * Overworld sky look (SkyRenderingMixin, AtmosphericFogModifierMixin, FogRendererMixin, WorldTimeMixin). Everything
 * here is client-side rendering only: the fixed time replaces the time of day that the client world reports to its
 * own sky/light renderer, the server's time is never touched. "Nebel aus" only removes the atmospheric distance fog of the overworld (Nether and End keep their fog);
 * blindness, darkness, water, lava and powder snow fog are applied by other fog modifiers and stay untouched.
 */
public class CustomSky extends Module {
    /** Deep night blue for the zenith. */
    public static final int DEFAULT_TOP = 0xFF06050F;
    /** Horizon: the accent colour darkened to ~18 %. */
    public static final int DEFAULT_HORIZON = 0xFF1C102E;

    private static CustomSky instance;

    public final ColorSetting top = add(new ColorSetting("top", "Himmel oben", DEFAULT_TOP));
    public final ColorSetting horizon = add(new ColorSetting("horizon", "Horizont", DEFAULT_HORIZON));
    public final BoolSetting stars = add(new BoolSetting("stars", "Sterne", true));
    public final NumberSetting starBrightness = add(new NumberSetting("star_brightness", "Sternhelligkeit", 1.0, 0.0, 1.0, 0.05));
    public final BoolSetting noFog = add(new BoolSetting("no_fog", "Nebel aus (nur Oberwelt)", false));
    public final BoolSetting fixedTime = add(new BoolSetting("fixed_time", "Feste Uhrzeit", true));
    public final NumberSetting time = add(new NumberSetting("time", "Uhrzeit (Ticks)", 18000, 0, 24000, 250));

    /** Set while the overworld atmospheric fog modifier is the active one this frame (not blindness, water, lava, other dimensions). */
    private static boolean atmosphericFog;

    public CustomSky() {
        super("custom_sky", "Eigener Himmel", "Himmelsfarben, Sterne, Nebel und eine feste Uhrzeit – nur optisch.");
        instance = this;
    }

    public static CustomSky instance() {
        return instance;
    }

    public static boolean active() {
        return instance != null && instance.enabled();
    }

    /** Fixed time of day the client renders with, or -1 when the real time is used. */
    public static long fixedTimeOfDay() {
        CustomSky s = instance;
        return s != null && s.enabled() && s.fixedTime.get() ? Math.round(s.time.get()) % 24000L : -1L;
    }

    public static void setAtmosphericFog(boolean on) {
        atmosphericFog = on;
    }

    /** Whether the distance fog of this frame should be removed. */
    public static boolean removeFog() {
        CustomSky s = instance;
        return atmosphericFog && s != null && s.enabled() && s.noFog.get();
    }
}
