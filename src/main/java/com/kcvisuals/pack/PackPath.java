package com.kcvisuals.pack;

import java.util.Locale;

/**
 * The single normalisation used for both category matching (scanner) and output paths (builder): the
 * {@code assets/minecraft/} prefix is matched case-insensitively, the rest is lowercased and legacy names are mapped
 * via {@link LegacyPaths}. Source entries must still be opened by their original name.
 *
 * @param relative lowercased path relative to {@code assets/minecraft/}, as found in the pack
 * @param modern {@code relative} after {@link LegacyPaths#normalise}; the path written to the generated pack
 */
public record PackPath(String relative, String modern) {
    public static final String MC_ASSETS = "assets/minecraft/";

    /** Returns null if the entry does not live under {@code assets/minecraft/} (case-insensitive). */
    public static PackPath of(String entry) {
        if (!entry.regionMatches(true, 0, MC_ASSETS, 0, MC_ASSETS.length())) {
            return null;
        }
        String rel = entry.substring(MC_ASSETS.length()).toLowerCase(Locale.ROOT);
        return new PackPath(rel, LegacyPaths.normalise(rel));
    }

    public boolean isLegacy() {
        return !modern.equals(relative);
    }

    public boolean in(Category c) {
        return c.matches(relative) || (isLegacy() && c.matches(modern));
    }
}
