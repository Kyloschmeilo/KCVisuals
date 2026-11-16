package com.kcvisuals.render;

/** Pure maths for the camera/screen modules (aspect stretch, fire/totem tweaks, swing speed, fullbright gamma). */
public final class ScreenMath {
    /** Gamma (brightness factor) that saturates the lightmap at every light level. */
    public static final float FULLBRIGHT_GAMMA = 16.0f;
    /** Fire overlay is lowered by this much (view units) at 0 % height. */
    public static final float FIRE_MAX_DROP = 0.5f;

    private ScreenMath() {}

    /** Clamps the horizontal stretch factor to the supported 1.0–1.6 range (NaN → 1). */
    public static float clampStretch(double factor) {
        if (Double.isNaN(factor)) {
            return 1f;
        }
        return (float) Math.max(1.0, Math.min(1.6, factor));
    }

    /**
     * Projection x scale for a stretch factor is {@link #clampStretch}: multiplying the x row of a perspective matrix
     * by it widens everything horizontally (the "stretched resolution" look), like dividing the aspect ratio by it.
     *
     * <p>
     * Width to pass to an aspect-ratio based projection so that {@code width / height} becomes {@code aspect / factor}
     * (same result as scaling x by {@code factor}). At least 1.
     */
    public static int stretchedWidth(int width, double factor) {
        return Math.max(1, Math.round(width / clampStretch(factor)));
    }

    /** Vertical offset of the fire overlay for a height of 0–1 (1 = vanilla, 0 = fully dropped). */
    public static float fireDrop(double height) {
        double h = Math.max(0.0, Math.min(1.0, height));
        return (float) (-(1.0 - h) * FIRE_MAX_DROP);
    }

    /** Hand swing duration in ticks for a speed multiplier (2 = twice as fast); never below 1. */
    public static int swingDuration(int vanillaTicks, double speed) {
        if (!(speed > 0)) {
            return Math.max(1, vanillaTicks);
        }
        return Math.max(1, (int) Math.round(vanillaTicks / speed));
    }

    /**
     * Brightness factor sent to the lightmap. {@code vanillaArg} is vanilla's {@code userGamma - darkness}; the boost
     * fades out as the Darkness effect fades in, so the effect keeps working with fullbright on.
     *
     * @param strength 0–1 share of the way from the user's gamma to {@link #FULLBRIGHT_GAMMA}
     */
    public static float fullbrightGamma(float vanillaArg, float userGamma, double strength) {
        float darkness = Math.max(0f, Math.min(1f, userGamma - vanillaArg));
        float s = (float) Math.max(0.0, Math.min(1.0, strength));
        float boosted = userGamma + (FULLBRIGHT_GAMMA - userGamma) * s - darkness;
        return boosted + (vanillaArg - boosted) * darkness;
    }

    /** Hurt-cam strength actually applied: 0 when the tweak is on, otherwise vanilla's option value. */
    public static double hurtTilt(double vanillaStrength, boolean hurtCamOff) {
        return hurtCamOff ? 0.0 : vanillaStrength;
    }
}
