package com.kcvisuals.preview;

/**
 * A model's {@code display.gui} transform: rotation in degrees (X, then Y, then Z as in vanilla's
 * {@code rotationXYZ}), translation in 1/16 block, scale. Values are clamped like vanilla does.
 */
public record GuiTransform(float[] rotation, float[] translation, float[] scale) {
    /** Vanilla {@code block/block} gui pose. */
    public static final GuiTransform BLOCK = new GuiTransform(new float[] {30, 225, 0}, new float[3],
            new float[] {0.625f, 0.625f, 0.625f});
    /** What vanilla uses when no model in the chain defines {@code display.gui}. */
    public static final GuiTransform IDENTITY = new GuiTransform(new float[3], new float[3], new float[] {1, 1, 1});

    public GuiTransform {
        rotation = rotation.clone();
        translation = translation.clone();
        scale = scale.clone();
        for (int i = 0; i < 3; i++) {
            rotation[i] = Float.isFinite(rotation[i]) ? rotation[i] : 0;
            translation[i] = clamp(translation[i], -80, 80);
            scale[i] = clamp(scale[i], -4, 4);
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return Float.isFinite(v) ? Math.max(lo, Math.min(hi, v)) : 0;
    }

    /** Matrix taking centred block units ([-0.5, 0.5]) to the gui view (x right, y up, z towards the viewer). */
    Mat4 matrix() {
        return Mat4.translation(translation[0] / 16.0, translation[1] / 16.0, translation[2] / 16.0)
                .mul(Mat4.rotationX(rotation[0]))
                .mul(Mat4.rotationY(rotation[1]))
                .mul(Mat4.rotationZ(rotation[2]))
                .mul(Mat4.scaling(scale[0], scale[1], scale[2]));
    }
}
