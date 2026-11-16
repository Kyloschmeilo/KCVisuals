package com.kcvisuals.preview;

import java.util.ArrayList;
import java.util.List;

/**
 * The placed end crystal, as built by 1.21.11's {@code EndCrystalEntityModel.getTexturedModelData()} (64x32 texture):
 * {@code base} uv(0,16) box(-6,0,-6, 12,4,12); {@code outer_glass} uv(0,0) box(-4,-4,-4, 8,8,8) at origin (0,24,0);
 * its child {@code inner_glass} (same box, scale 0.875); its child {@code cube} uv(32,0) box(-4,-4,-4, 8,8,8), scale
 * 0.765625. {@code setAngles}: outer = rotY(spin) * rotAxis(60 deg, (sin45, 0, sin45)), inner and cube =
 * rotAxis(60 deg) * rotY(spin) each, and the outer glass bobs by {@code getYOffset(age) * 16 / 2}. The renderer draws
 * it with {@code entityCutoutNoCull} (no back-face culling, alpha cutout). Coordinates here are model pixels, y up.
 */
public final class EndCrystalGeometry {
    public static final int TEX_W = 64, TEX_H = 32;
    /** Glass bob in pixels at the midpoint of the animation ({@code getYOffset} = -1.1). */
    static final float BOB = -1.1f * 16 / 2;
    private static final double S45 = Math.sin(Math.PI / 4);

    /** @param box x, y, z, sizeX, sizeY, sizeZ in model pixels */
    public record Cuboid(String name, int u, int v, float[] box, float scale) {}

    private EndCrystalGeometry() {}

    public static List<Cuboid> cuboids() {
        float[] glass = {-4, -4, -4, 8, 8, 8};
        return List.of(
                new Cuboid("base", 0, 16, new float[] {-6, 0, -6, 12, 4, 12}, 1f),
                new Cuboid("outer_glass", 0, 0, glass, 1f),
                new Cuboid("inner_glass", 0, 0, glass, 0.875f),
                new Cuboid("cube", 32, 0, glass, 0.765625f));
    }

    /** Quads of the crystal with the glass turned by {@code spinDeg} (in game: 3 degrees per tick of age). */
    public static List<Quad> quads(ArgbImage tex, double spinDeg) {
        List<Cuboid> parts = cuboids();
        Mat4 tilt = Mat4.rotation(60, S45, 0, S45);
        Mat4 outer = Mat4.translation(0, 24 + BOB, 0).mul(Mat4.rotationY(spinDeg)).mul(tilt);
        Mat4 inner = outer.mul(tilt.mul(Mat4.rotationY(spinDeg))).mul(Mat4.scaling(0.875, 0.875, 0.875));
        Mat4 cube = inner.mul(tilt.mul(Mat4.rotationY(spinDeg))).mul(Mat4.scaling(0.765625, 0.765625, 0.765625));
        Mat4[] transforms = {Mat4.identity(), outer, inner, cube};
        List<Quad> out = new ArrayList<>(24);
        for (int i = 0; i < parts.size(); i++) {
            for (Quad q : cuboidQuads(parts.get(i), tex)) {
                out.add(q.transformed(transforms[i]));
            }
        }
        return out;
    }

    /** The six faces of a box-UV cuboid exactly as vanilla's {@code ModelPart.Cuboid} lays them out. */
    static List<Quad> cuboidQuads(Cuboid c, ArgbImage tex) {
        float[] b = c.box();
        float f = b[0], g = b[1], h = b[2], i = b[0] + b[3], j = b[1] + b[4], k = b[2] + b[5];
        float[][] v = {
                null,
                {f, g, h}, {i, g, h}, {i, j, h}, {f, j, h},
                {f, g, k}, {i, g, k}, {i, j, k}, {f, j, k}};
        float dx = b[3], dy = b[4], dz = b[5];
        float u = c.u(), vv = c.v();
        float u0 = u, u1 = u + dz, u2 = u + dz + dx, u3 = u + dz + dx + dx, u4 = u + dz + dx + dz, u5 = u + dz + dx + dz + dx;
        float v0 = vv, v1 = vv + dz, v2 = vv + dz + dy;
        List<Quad> out = new ArrayList<>(6);
        out.add(face(tex, Direction.DOWN, new float[][] {v[6], v[5], v[1], v[2]}, u1, v0, u2, v1));
        out.add(face(tex, Direction.UP, new float[][] {v[3], v[4], v[8], v[7]}, u2, v1, u3, v0));
        out.add(face(tex, Direction.WEST, new float[][] {v[1], v[5], v[8], v[4]}, u0, v1, u1, v2));
        out.add(face(tex, Direction.NORTH, new float[][] {v[2], v[1], v[4], v[3]}, u1, v1, u2, v2));
        out.add(face(tex, Direction.EAST, new float[][] {v[6], v[2], v[3], v[7]}, u2, v1, u4, v2));
        out.add(face(tex, Direction.SOUTH, new float[][] {v[5], v[6], v[7], v[8]}, u4, v1, u5, v2));
        return out;
    }

    /** Vanilla {@code ModelPart.Quad}: corner 0 gets (u2, v1), 1 (u1, v1), 2 (u1, v2), 3 (u2, v2). */
    private static Quad face(ArgbImage tex, Direction d, float[][] corners, float u1, float v1, float u2, float v2) {
        float[] pos = new float[12];
        for (int n = 0; n < 4; n++) {
            System.arraycopy(corners[n], 0, pos, n * 3, 3);
        }
        float[] uv = {u2 / TEX_W, v1 / TEX_H, u1 / TEX_W, v1 / TEX_H, u1 / TEX_W, v2 / TEX_H, u2 / TEX_W, v2 / TEX_H};
        return new Quad(pos, uv, tex, d.shade(), false, false);
    }
}
