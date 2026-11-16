package com.kcvisuals.render;

import java.util.Locale;

/** Pure maths for the hit visuals (damage number text and animation, hurt overlay texel). */
public final class HitMath {
    /** How far a damage number rises over its lifetime, in blocks. */
    public static final float RISE_BLOCKS = 0.8f;
    /** Fraction of the lifetime after which a damage number starts fading out. */
    public static final float FADE_START = 0.5f;
    /** Fraction of the lifetime of the initial scale pop. */
    public static final float POP_END = 0.15f;
    public static final float POP_SCALE = 1.4f;

    private HitMath() {}

    /** "-x.xx" with two decimals, always with a dot. */
    public static String formatDamage(double amount) {
        return "-" + String.format(Locale.ROOT, "%.2f", Math.abs(amount));
    }

    private static float clamp01(float t) {
        return Math.max(0f, Math.min(1f, t));
    }

    /** Height offset at life fraction t (ease-out cubic). */
    public static float rise(float t) {
        float u = 1f - clamp01(t);
        return RISE_BLOCKS * (1f - u * u * u);
    }

    /** Alpha multiplier at life fraction t: opaque, then linear fade to 0. */
    public static float fade(float t) {
        t = clamp01(t);
        return t <= FADE_START ? 1f : 1f - (t - FADE_START) / (1f - FADE_START);
    }

    /** Scale multiplier at life fraction t: starts enlarged and settles to 1. */
    public static float pop(float t) {
        t = clamp01(t);
        if (t >= POP_END) {
            return 1f;
        }
        float u = 1f - t / POP_END;
        return 1f + (POP_SCALE - 1f) * u * u;
    }

    /**
     * Converts a tint colour (ARGB, alpha = tint strength) to the overlay texel the entity shader expects: it computes
     * {@code mix(overlay.rgb, color.rgb, overlay.a)}, so the texel alpha is the share of the original colour kept.
     */
    public static int overlayTexel(int tintArgb) {
        int strength = tintArgb >>> 24;
        return ((255 - strength) << 24) | (tintArgb & 0xFFFFFF);
    }
}
