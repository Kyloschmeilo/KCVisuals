package com.kcvisuals.preview;

import java.util.Arrays;

/**
 * Software triangle rasterizer for {@link Quad}s already in pixel space (x right, y down, larger z nearer): depth
 * buffer, nearest-neighbour texture sampling, alpha cutout below 0.1, flat per-quad shading.
 */
final class Rasterizer {
    /** Texels with alpha below this (0.1 * 255) are discarded, like vanilla's cutout. */
    static final int CUTOUT_ALPHA = 26;

    private final ArgbImage target;
    private final float[] depth;
    /** Pixel tests left before the frame is given up ({@link Scene.BudgetExceeded}). */
    private long budget;

    Rasterizer(ArgbImage target, long budget) {
        this.target = target;
        this.budget = budget;
        this.depth = new float[target.width() * target.height()];
        Arrays.fill(depth, Float.NEGATIVE_INFINITY);
    }

    void draw(Quad q) {
        float[] p = q.pos();
        if (q.cull()) {
            // pixel space has y down, so a quad that is counter-clockwise in view space has negative area here
            double area = 0;
            for (int i = 0; i < 4; i++) {
                int n = (i + 1) % 4;
                area += p[i * 3] * p[n * 3 + 1] - p[n * 3] * p[i * 3 + 1];
            }
            if (area >= 0) {
                return;
            }
        }
        triangle(q, 0, 1, 2);
        triangle(q, 0, 2, 3);
    }

    private void triangle(Quad q, int a, int b, int c) {
        float[] p = q.pos();
        double ax = p[a * 3], ay = p[a * 3 + 1], az = p[a * 3 + 2];
        double bx = p[b * 3], by = p[b * 3 + 1], bz = p[b * 3 + 2];
        double cx = p[c * 3], cy = p[c * 3 + 1], cz = p[c * 3 + 2];
        double area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
        if (Math.abs(area) < 1e-9) {
            return;
        }
        int w = target.width(), h = target.height();
        int minX = Math.max(0, (int) Math.floor(Math.min(ax, Math.min(bx, cx))));
        int maxX = Math.min(w - 1, (int) Math.ceil(Math.max(ax, Math.max(bx, cx))));
        int minY = Math.max(0, (int) Math.floor(Math.min(ay, Math.min(by, cy))));
        int maxY = Math.min(h - 1, (int) Math.ceil(Math.max(ay, Math.max(by, cy))));
        budget -= (long) Math.max(0, maxX - minX + 1) * Math.max(0, maxY - minY + 1);
        if (budget < 0) {
            throw new Scene.BudgetExceeded("fill budget exceeded");
        }
        float[] uv = q.uv();
        double eps = -1e-7;
        int[] px = target.pixels();
        for (int y = minY; y <= maxY; y++) {
            double sy = y + 0.5;
            for (int x = minX; x <= maxX; x++) {
                double sx = x + 0.5;
                double w0 = ((cx - bx) * (sy - by) - (cy - by) * (sx - bx)) / area;
                double w1 = ((ax - cx) * (sy - cy) - (ay - cy) * (sx - cx)) / area;
                double w2 = 1 - w0 - w1;
                if (w0 < eps || w1 < eps || w2 < eps) {
                    continue;
                }
                double z = w0 * az + w1 * bz + w2 * cz;
                int idx = y * w + x;
                if (z <= depth[idx]) {
                    continue;
                }
                double u = w0 * uv[a * 2] + w1 * uv[b * 2] + w2 * uv[c * 2];
                double v = w0 * uv[a * 2 + 1] + w1 * uv[b * 2 + 1] + w2 * uv[c * 2 + 1];
                int texel = q.tex() == null ? missing(u, v) : q.tex().sample(u, v);
                int alpha = texel >>> 24;
                if (alpha < CUTOUT_ALPHA) {
                    continue;
                }
                int color = shade(texel, q.shade());
                if (q.translucent() && alpha < 255) {
                    color = blend(color, alpha, px[idx]);
                } else {
                    color |= 0xFF000000;
                }
                px[idx] = color;
                depth[idx] = (float) z;
            }
        }
    }

    /** Vanilla's missing texture: magenta and black quarters. */
    private static int missing(double u, double v) {
        int cell = ((int) Math.floor(u * 2) + (int) Math.floor(v * 2)) & 1;
        return cell == 0 ? 0xFFF800F8 : 0xFF000000;
    }

    private static int shade(int argb, float f) {
        if (f >= 1f) {
            return argb & 0x00FFFFFF;
        }
        int r = Math.round(((argb >> 16) & 0xFF) * f);
        int g = Math.round(((argb >> 8) & 0xFF) * f);
        int b = Math.round((argb & 0xFF) * f);
        return r << 16 | g << 8 | b;
    }

    /** Source (rgb, alpha) over destination (argb). */
    private static int blend(int rgb, int alpha, int dst) {
        float sa = alpha / 255f;
        float da = (dst >>> 24) / 255f;
        float oa = sa + da * (1 - sa);
        if (oa <= 0) {
            return 0;
        }
        int r = mix((rgb >> 16) & 0xFF, sa, (dst >> 16) & 0xFF, da, oa);
        int g = mix((rgb >> 8) & 0xFF, sa, (dst >> 8) & 0xFF, da, oa);
        int b = mix(rgb & 0xFF, sa, dst & 0xFF, da, oa);
        return Math.round(oa * 255) << 24 | r << 16 | g << 8 | b;
    }

    private static int mix(int s, float sa, int d, float da, float oa) {
        return Math.min(255, Math.round((s * sa + d * da * (1 - sa)) / oa));
    }
}
