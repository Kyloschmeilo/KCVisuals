package com.kcvisuals.module.visual;

import com.kcvisuals.module.Module;
import com.kcvisuals.module.setting.NumberSetting;
import com.kcvisuals.render.ScreenMath;

/**
 * Stretched-resolution look: the world and hand projections are scaled horizontally by the factor
 * (GameRendererProjectionMixin). The window, GUI and HUD are not changed.
 */
public class AspectRatio extends Module {
    private static AspectRatio instance;

    public final NumberSetting stretch = add(new NumberSetting("stretch", "Streckung", 1.33, 1.0, 1.6, 0.01));

    public AspectRatio() {
        super("aspect_ratio", "Seitenverhältnis", "Streckt das Bild horizontal wie eine gestreckte Auflösung.");
        instance = this;
    }

    public static AspectRatio instance() {
        return instance;
    }

    /** Current factor, 1 while disabled. */
    public static float factor() {
        AspectRatio a = instance;
        return a != null && a.enabled() ? ScreenMath.clampStretch(a.stretch.get()) : 1f;
    }
}
