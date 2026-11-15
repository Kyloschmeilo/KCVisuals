package com.kcvisuals.pack;

import java.util.List;

/**
 * One composable texture category. Paths are relative to {@code assets/minecraft/}; a trailing {@code *} makes a
 * pattern a prefix glob (so {@code textures/item/mace*} also covers the {@code .png.mcmeta} sibling).
 */
public record Category(String id, String group, String displayName, String previewPath, List<String> paths) {
    public Category {
        paths = List.copyOf(paths);
    }

    /** True if the given path (relative to {@code assets/minecraft/}) belongs to this category. */
    public boolean matches(String path) {
        for (String pattern : paths) {
            if (pathMatches(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    public static boolean pathMatches(String pattern, String path) {
        if (pattern.endsWith("*")) {
            return path.regionMatches(0, pattern, 0, pattern.length() - 1);
        }
        return pattern.equals(path);
    }
}
