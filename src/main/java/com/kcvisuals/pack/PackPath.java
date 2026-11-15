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

    /**
     * Output path for any namespace: {@code assets/<ns>/<rest>} lowercased, with {@link LegacyPaths} applied to the
     * {@code minecraft} namespace. Null if the entry is not under {@code assets/<ns>/} (prefix case-insensitive).
     */
    public static String output(String entry) {
        if (!entry.regionMatches(true, 0, "assets/", 0, 7)) {
            return null;
        }
        int slash = entry.indexOf('/', 7);
        if (slash <= 7 || slash == entry.length() - 1) {
            return null;
        }
        String ns = entry.substring(7, slash).toLowerCase(Locale.ROOT);
        String rest = entry.substring(slash + 1).toLowerCase(Locale.ROOT);
        return "assets/" + ns + "/" + (ns.equals("minecraft") ? LegacyPaths.normalise(rest) : rest);
    }

    /** True if {@link #output} renamed the entry beyond lowercasing (a pre-1.13 / pre-1.21.4 path). */
    public static boolean isLegacyEntry(String entry) {
        String out = output(entry);
        return out != null && !out.equals(entry.toLowerCase(Locale.ROOT));
    }

    public boolean isLegacy() {
        return !modern.equals(relative);
    }

    public boolean in(Category c) {
        return c.matches(relative) || (isLegacy() && c.matches(modern));
    }
}
