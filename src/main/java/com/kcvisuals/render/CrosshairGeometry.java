package com.kcvisuals.render;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure crosshair geometry in screen pixels, relative to the centre pixel block. A line of thickness {@code t} covers
 * the pixel span {@code [lo, lo + t)} with {@code lo = -(t / 2)}, so every shape is symmetric around the same centre
 * for odd and even thickness. Rects are half-open: {@code [x0, x1) × [y0, y1)}.
 */
public final class CrosshairGeometry {
    public static final String CROSS = "Kreuz";
    public static final String DOT = "Punkt";
    public static final String CIRCLE = "Kreis";
    public static final String PLUS_DOT = "Plus-Punkt";

    public record Rect(int x0, int y0, int x1, int y1) {
        public Rect grow(int by) {
            return new Rect(x0 - by, y0 - by, x1 + by, y1 + by);
        }
    }

    private CrosshairGeometry() {}

    /** Low edge of a centred span of the given thickness. */
    static int lo(int thickness) {
        return -(thickness / 2);
    }

    /**
     * Shapes for a mode. {@code size} = arm length (cross) or ring radius beyond the gap (circle); {@code gap} = empty
     * space between the centre span and the arms / inner ring; {@code thickness} = line width (≥ 1).
     */
    public static List<Rect> build(String mode, int size, int gap, int thickness) {
        int t = Math.max(1, thickness);
        int s = Math.max(1, size);
        int g = Math.max(0, gap);
        List<Rect> out = new ArrayList<>();
        switch (mode) {
            case DOT -> out.add(dot(t));
            case CIRCLE -> ring(out, s, g, t);
            case PLUS_DOT -> {
                cross(out, s, g, t);
                out.add(dot(t));
            }
            default -> cross(out, s, g, t);
        }
        return out;
    }

    /** Square centre dot as wide as a line. */
    static Rect dot(int t) {
        int lo = lo(t), hi = lo + t;
        return new Rect(lo, lo, hi, hi);
    }

    static void cross(List<Rect> out, int s, int g, int t) {
        int lo = lo(t), hi = lo + t;
        out.add(new Rect(lo - g - s, lo, lo - g, hi)); // left
        out.add(new Rect(hi + g, lo, hi + g + s, hi)); // right
        out.add(new Rect(lo, lo - g - s, hi, lo - g)); // top
        out.add(new Rect(lo, hi + g, hi, hi + g + s)); // bottom
    }

    /**
     * Ring around the centre point: outer radius {@code g + s}, width {@code t}. Each pixel whose centre lies in the
     * annulus is covered; pixels are merged into horizontal runs per row.
     */
    static void ring(List<Rect> out, int s, int g, int t) {
        int lo = lo(t), hi = lo + t;
        double c = (lo + hi) / 2.0;
        double ro = g + s;
        double ri = Math.max(0.0, ro - t);
        int min = (int) Math.floor(c - ro) - 1, max = (int) Math.ceil(c + ro) + 1;
        for (int y = min; y <= max; y++) {
            double dy = y + 0.5 - c;
            int runStart = Integer.MIN_VALUE;
            for (int x = min; x <= max + 1; x++) {
                double dx = x + 0.5 - c;
                double d2 = dx * dx + dy * dy;
                boolean in = x <= max && d2 < ro * ro && d2 >= ri * ri;
                if (in && runStart == Integer.MIN_VALUE) {
                    runStart = x;
                } else if (!in && runStart != Integer.MIN_VALUE) {
                    out.add(new Rect(runStart, y, x, y + 1));
                    runStart = Integer.MIN_VALUE;
                }
            }
        }
    }
}
