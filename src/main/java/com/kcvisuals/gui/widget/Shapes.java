package com.kcvisuals.gui.widget;

/** Pure geometry for rounded rectangles (no Minecraft types, unit tested). */
public final class Shapes {
    private Shapes() {}

    /**
     * Horizontal inset of a rounded rectangle's outline at pixel row {@code row} (0 = top), for a rectangle of
     * {@code height} rows and corner radius {@code radius}. Rows between the corners have inset 0.
     */
    public static int insetAt(int row, int height, int radius) {
        radius = clampRadius(radius, Integer.MAX_VALUE, height);
        if (radius <= 0 || row < 0 || row >= height) {
            return 0;
        }
        int fromEdge = row < radius ? row : (row >= height - radius ? height - 1 - row : -1);
        if (fromEdge < 0) {
            return 0;
        }
        double dy = radius - fromEdge - 0.5;
        double dx = Math.sqrt(Math.max(0, (double) radius * radius - dy * dy));
        return (int) Math.round(radius - dx);
    }

    /** Radius limited to half the smaller side so corners never overlap. */
    public static int clampRadius(int radius, int width, int height) {
        return Math.max(0, Math.min(radius, Math.min(width, height) / 2));
    }
}
