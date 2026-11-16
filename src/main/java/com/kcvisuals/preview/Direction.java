package com.kcvisuals.preview;

import java.util.Locale;

/** Face directions of a block model element, with Minecraft's fixed per-face shading. */
public enum Direction {
    DOWN(0.5f), UP(1.0f), NORTH(0.8f), SOUTH(0.8f), WEST(0.6f), EAST(0.6f);

    private final float shade;

    Direction(float shade) {
        this.shade = shade;
    }

    public float shade() {
        return shade;
    }

    /** Direction for a model-json face key ({@code "up"}, {@code "north"}...), or null. */
    public static Direction byName(String name) {
        for (Direction d : values()) {
            if (d.name().toLowerCase(Locale.ROOT).equals(name)) {
                return d;
            }
        }
        return null;
    }
}
