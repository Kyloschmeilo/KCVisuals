package com.kcvisuals.preview;

/** Tiny immutable 4x4 affine matrix (row-major, column vectors). */
final class Mat4 {
    private final double[] m;

    private Mat4(double[] m) {
        this.m = m;
    }

    static Mat4 identity() {
        return new Mat4(new double[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1});
    }

    static Mat4 translation(double x, double y, double z) {
        return new Mat4(new double[] {1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z, 0, 0, 0, 1});
    }

    static Mat4 scaling(double x, double y, double z) {
        return new Mat4(new double[] {x, 0, 0, 0, 0, y, 0, 0, 0, 0, z, 0, 0, 0, 0, 1});
    }

    static Mat4 rotationX(double deg) {
        double c = Math.cos(Math.toRadians(deg)), s = Math.sin(Math.toRadians(deg));
        return new Mat4(new double[] {1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0, 0, 0, 0, 1});
    }

    static Mat4 rotationY(double deg) {
        double c = Math.cos(Math.toRadians(deg)), s = Math.sin(Math.toRadians(deg));
        return new Mat4(new double[] {c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0, 0, 0, 0, 1});
    }

    static Mat4 rotationZ(double deg) {
        double c = Math.cos(Math.toRadians(deg)), s = Math.sin(Math.toRadians(deg));
        return new Mat4(new double[] {c, -s, 0, 0, s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1});
    }

    /** Right-handed rotation by {@code deg} around the axis (normalised here). */
    static Mat4 rotation(double deg, double ax, double ay, double az) {
        double len = Math.sqrt(ax * ax + ay * ay + az * az);
        double x = ax / len, y = ay / len, z = az / len;
        double c = Math.cos(Math.toRadians(deg)), s = Math.sin(Math.toRadians(deg)), t = 1 - c;
        return new Mat4(new double[] {
                t * x * x + c, t * x * y - s * z, t * x * z + s * y, 0,
                t * x * y + s * z, t * y * y + c, t * y * z - s * x, 0,
                t * x * z - s * y, t * y * z + s * x, t * z * z + c, 0,
                0, 0, 0, 1});
    }

    /** {@code this * o}: {@code o} is applied first. */
    Mat4 mul(Mat4 o) {
        double[] r = new double[16];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                double s = 0;
                for (int k = 0; k < 4; k++) {
                    s += m[i * 4 + k] * o.m[k * 4 + j];
                }
                r[i * 4 + j] = s;
            }
        }
        return new Mat4(r);
    }

    /** Transforms the point at {@code src[off..off+2]} into {@code dst[dOff..dOff+2]}. */
    void transform(float[] src, int off, float[] dst, int dOff) {
        double x = src[off], y = src[off + 1], z = src[off + 2];
        dst[dOff] = (float) (m[0] * x + m[1] * y + m[2] * z + m[3]);
        dst[dOff + 1] = (float) (m[4] * x + m[5] * y + m[6] * z + m[7]);
        dst[dOff + 2] = (float) (m[8] * x + m[9] * y + m[10] * z + m[11]);
    }
}
