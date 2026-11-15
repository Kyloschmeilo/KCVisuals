package com.kcvisuals.gui;

public final class Theme {
    /** Accent colour (ARGB); mutable so a later settings tab can change it. */
    public static int ACCENT = 0xFF9B5CFF;
    public static final int BG = 0xC0101018;
    public static final int PANEL = 0xE0181824;
    public static final int TEXT = 0xFFEDEDF5;

    /** Secondary text (labels, counts, hints). */
    public static final int TEXT_DIM = 0xFF8E8EA6;
    /** Faint text (section headers, placeholders). */
    public static final int TEXT_FAINT = 0xFF5E5E74;
    /** Raised surfaces inside the panel (cards, inputs, list rows). */
    public static final int SURFACE = 0xFF20202E;
    /** Hovered surface. */
    public static final int SURFACE_HOVER = 0xFF2A2A3C;
    /** Hairline borders and separators. */
    public static final int BORDER = 0xFF2E2E42;
    public static final int DANGER = 0xFFE5484D;
    public static final int SUCCESS = 0xFF46C37B;
    public static final int WARNING = 0xFFF5A524;

    private Theme() {}

    /** Multiplies the alpha channel of an ARGB colour by {@code factor} (0..1). */
    public static int withAlpha(int argb, float factor) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, factor)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** Linear blend of two ARGB colours, {@code t} = 0 gives {@code a}, 1 gives {@code b}. */
    public static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int ca = (a >>> shift) & 0xFF;
            int cb = (b >>> shift) & 0xFF;
            r |= (Math.round(ca + (cb - ca) * t) & 0xFF) << shift;
        }
        return r;
    }
}
