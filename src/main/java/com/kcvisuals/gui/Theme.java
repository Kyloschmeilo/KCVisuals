package com.kcvisuals.gui;

public final class Theme {
    /** Accent colour (ARGB); mutable so a later settings tab can change it. */
    public static int ACCENT = 0xFF9B5CFF;
    public static final int BG = 0xC0101018;
    public static final int PANEL = 0xE0181824;
    public static final int TEXT = 0xFFEDEDF5;

    private Theme() {}

    /** Multiplies the alpha channel of an ARGB colour by {@code factor} (0..1). */
    public static int withAlpha(int argb, float factor) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, factor)));
        return (a << 24) | (argb & 0xFFFFFF);
    }
}
