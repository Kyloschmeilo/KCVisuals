package com.kcvisuals.preview;

import java.util.Map;

/**
 * One cuboid of a Java block/item model, coordinates in model pixels (0..16 is one block).
 *
 * @param rotation single-axis element rotation, or null
 * @param shade false for {@code "shade": false} (no per-face darkening)
 */
public record ModelElement(float[] from, float[] to, Rotation rotation, boolean shade, Map<Direction, Face> faces) {
    public ModelElement {
        faces = Map.copyOf(faces);
    }

    /** @param axis 0 = x, 1 = y, 2 = z */
    public record Rotation(float[] origin, int axis, float angle, boolean rescale) {}

    /**
     * @param uv {u1, v1, u2, v2} in 0..16, or null for the default derived from the element bounds
     * @param texture a {@code #ref} into the model's textures, or a texture id
     * @param rotation 0, 90, 180 or 270
     */
    public record Face(float[] uv, String texture, int rotation) {}
}
