package com.kcvisuals.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;

/**
 * Camera-relative world primitives. All coordinates passed in are already relative to the camera (the world render
 * events draw with an identity pose; the view rotation is applied by the pipeline).
 * Filled shapes use {@link RenderLayers#debugQuads()} (POSITION_COLOR, translucent blend, depth test on, no depth
 * write, no culling); lines use {@link RenderLayers#linesTranslucent()} (per-vertex line width, depth tested).
 */
public final class Shapes {
    public static final int SEGMENTS = 64;
    private static final float[] COS = new float[SEGMENTS + 1];
    private static final float[] SIN = new float[SEGMENTS + 1];

    static {
        for (int i = 0; i <= SEGMENTS; i++) {
            double a = Math.PI * 2 * i / SEGMENTS;
            COS[i] = (float) Math.cos(a);
            SIN[i] = (float) Math.sin(a);
        }
    }

    private Shapes() {}

    public static RenderLayer quads() {
        return RenderLayers.debugQuads();
    }

    public static RenderLayer lines() {
        return RenderLayers.linesTranslucent();
    }

    /** Linear ARGB interpolation. */
    public static int lerpColor(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = a >>> 24, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = b >>> 24, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    /** Multiplies the alpha of an ARGB colour. */
    public static int alpha(int argb, float mul) {
        int a = Math.round((argb >>> 24) * Math.max(0f, Math.min(1f, mul)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** Symmetric gradient position for segment i: 0 at angle 0, 1 at the opposite side. */
    private static float gradientT(int i) {
        return 0.5f - 0.5f * COS[i];
    }

    /**
     * Flat horizontal band between two radii, colour running a→b→a around the circle; inner and outer edge alpha
     * multipliers allow soft glow edges.
     */
    public static void ring(VertexConsumer vc, float cx, float cy, float cz, float rIn, float rOut,
                            int colorA, int colorB, float alphaIn, float alphaOut) {
        for (int i = 0; i < SEGMENTS; i++) {
            int c0 = lerpColor(colorA, colorB, gradientT(i));
            int c1 = lerpColor(colorA, colorB, gradientT(i + 1));
            vc.vertex(cx + COS[i] * rIn, cy, cz + SIN[i] * rIn).color(alpha(c0, alphaIn));
            vc.vertex(cx + COS[i] * rOut, cy, cz + SIN[i] * rOut).color(alpha(c0, alphaOut));
            vc.vertex(cx + COS[i + 1] * rOut, cy, cz + SIN[i + 1] * rOut).color(alpha(c1, alphaOut));
            vc.vertex(cx + COS[i + 1] * rIn, cy, cz + SIN[i + 1] * rIn).color(alpha(c1, alphaIn));
        }
    }

    /**
     * Ring lying in the plane spanned by unit vectors u and v (e.g. a block face), centred at c.
     */
    public static void planeRing(VertexConsumer vc, float cx, float cy, float cz,
                                 float ux, float uy, float uz, float vx, float vy, float vz,
                                 float rIn, float rOut, int colorIn, int colorOut) {
        for (int i = 0; i < SEGMENTS; i++) {
            float c0 = COS[i], s0 = SIN[i], c1 = COS[i + 1], s1 = SIN[i + 1];
            vc.vertex(cx + (ux * c0 + vx * s0) * rIn, cy + (uy * c0 + vy * s0) * rIn, cz + (uz * c0 + vz * s0) * rIn).color(colorIn);
            vc.vertex(cx + (ux * c0 + vx * s0) * rOut, cy + (uy * c0 + vy * s0) * rOut, cz + (uz * c0 + vz * s0) * rOut).color(colorOut);
            vc.vertex(cx + (ux * c1 + vx * s1) * rOut, cy + (uy * c1 + vy * s1) * rOut, cz + (uz * c1 + vz * s1) * rOut).color(colorOut);
            vc.vertex(cx + (ux * c1 + vx * s1) * rIn, cy + (uy * c1 + vy * s1) * rIn, cz + (uz * c1 + vz * s1) * rIn).color(colorIn);
        }
    }

    /** Upright cone: apex above the base centre; side triangles as degenerate quads, colour a→b→a around. */
    public static void cone(VertexConsumer vc, float cx, float baseY, float cz, float radius, float height,
                            int colorA, int colorB, float apexAlpha) {
        float ay = baseY + height;
        for (int i = 0; i < SEGMENTS; i++) {
            int c0 = lerpColor(colorA, colorB, gradientT(i));
            int c1 = lerpColor(colorA, colorB, gradientT(i + 1));
            int ca = alpha(lerpColor(colorA, colorB, 0.5f), apexAlpha);
            vc.vertex(cx, ay, cz).color(ca);
            vc.vertex(cx + COS[i] * radius, baseY, cz + SIN[i] * radius).color(c0);
            vc.vertex(cx + COS[i + 1] * radius, baseY, cz + SIN[i + 1] * radius).color(c1);
            vc.vertex(cx, ay, cz).color(ca);
        }
    }

    /** Circle outline (line loop) at height y. */
    public static void circleLine(VertexConsumer vc, float cx, float cy, float cz, float r, int colorA, int colorB, float width) {
        for (int i = 0; i < SEGMENTS; i++) {
            float dx = (COS[i + 1] - COS[i]) * r, dz = (SIN[i + 1] - SIN[i]) * r;
            float len = (float) Math.sqrt(dx * dx + dz * dz);
            float nx = dx / len, nz = dz / len;
            vc.vertex(cx + COS[i] * r, cy, cz + SIN[i] * r).color(lerpColor(colorA, colorB, gradientT(i))).normal(nx, 0, nz).lineWidth(width);
            vc.vertex(cx + COS[i + 1] * r, cy, cz + SIN[i + 1] * r).color(lerpColor(colorA, colorB, gradientT(i + 1))).normal(nx, 0, nz).lineWidth(width);
        }
    }

    /** One line segment. */
    public static void line(VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
                            int c0, int c1, float width) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6f) {
            return;
        }
        dx /= len;
        dy /= len;
        dz /= len;
        vc.vertex(x0, y0, z0).color(c0).normal(dx, dy, dz).lineWidth(width);
        vc.vertex(x1, y1, z1).color(c1).normal(dx, dy, dz).lineWidth(width);
    }

    /**
     * Line strip through camera-relative xyz triples in {@code pts}; colour fades from {@code c0} to {@code c1}
     * along the strip.
     */
    public static void lineStrip(VertexConsumer vc, float[] pts, int count, int c0, int c1, float width) {
        for (int i = 0; i + 1 < count; i++) {
            float t0 = (float) i / Math.max(1, count - 1), t1 = (float) (i + 1) / Math.max(1, count - 1);
            line(vc, pts[i * 3], pts[i * 3 + 1], pts[i * 3 + 2], pts[i * 3 + 3], pts[i * 3 + 4], pts[i * 3 + 5],
                    lerpColor(c0, c1, t0), lerpColor(c0, c1, t1), width);
        }
    }

    /**
     * Vertical square column (four side faces) from y0 to y1 with half-width w, alpha fading from bottom to top.
     */
    public static void beam(VertexConsumer vc, float cx, float y0, float y1, float cz, float w, int bottom, int top) {
        float x0 = cx - w, x1 = cx + w, z0 = cz - w, z1 = cz + w;
        face(vc, x0, z0, x1, z0, y0, y1, bottom, top);
        face(vc, x1, z0, x1, z1, y0, y1, bottom, top);
        face(vc, x1, z1, x0, z1, y0, y1, bottom, top);
        face(vc, x0, z1, x0, z0, y0, y1, bottom, top);
    }

    private static void face(VertexConsumer vc, float ax, float az, float bx, float bz, float y0, float y1, int bottom, int top) {
        vc.vertex(ax, y0, az).color(bottom);
        vc.vertex(bx, y0, bz).color(bottom);
        vc.vertex(bx, y1, bz).color(top);
        vc.vertex(ax, y1, az).color(top);
    }
}
