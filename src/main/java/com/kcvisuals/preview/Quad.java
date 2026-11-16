package com.kcvisuals.preview;

/**
 * A textured quad: 4 corners ({@code pos} = x, y, z per corner) with normalised texture coordinates ({@code uv} = u, v
 * per corner). {@code tex} null draws the missing-texture checker. {@code cull} drops the quad when seen from behind
 * (counter-clockwise corners are the front); {@code translucent} blends partly transparent texels instead of drawing
 * them opaque (cutout).
 */
public record Quad(float[] pos, float[] uv, ArgbImage tex, float shade, boolean cull, boolean translucent) {
    Quad transformed(Mat4 m) {
        float[] p = new float[12];
        for (int i = 0; i < 4; i++) {
            m.transform(pos, i * 3, p, i * 3);
        }
        return new Quad(p, uv, tex, shade, cull, translucent);
    }
}
