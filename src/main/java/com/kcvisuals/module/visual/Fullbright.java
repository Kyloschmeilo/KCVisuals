package com.kcvisuals.module.visual;

import com.kcvisuals.module.Module;
import com.kcvisuals.module.setting.NumberSetting;
import com.kcvisuals.render.ScreenMath;

/**
 * Full brightness without touching the gamma option: LightmapMixin replaces only the brightness factor the lightmap
 * shader receives while this module is on. The option value (and with it options.txt) is never written, so there is
 * nothing to restore on disable, on crash or after a restart. The Darkness effect still fades the boost out.
 */
public class Fullbright extends Module {
    private static Fullbright instance;

    public final NumberSetting strength = add(new NumberSetting("strength", "Stärke (%)", 100, 10, 100, 5));

    public Fullbright() {
        super("fullbright", "Fullbright", "Volle Helligkeit überall, ohne die Gamma-Einstellung zu verändern.");
        instance = this;
    }

    public static Fullbright instance() {
        return instance;
    }

    /** Brightness factor for the lightmap; {@code vanillaArg} is returned unchanged while disabled. */
    public static float brightness(float vanillaArg, float userGamma) {
        Fullbright f = instance;
        if (f == null || !f.enabled()) {
            return vanillaArg;
        }
        return ScreenMath.fullbrightGamma(vanillaArg, userGamma, f.strength.get() / 100.0);
    }
}
