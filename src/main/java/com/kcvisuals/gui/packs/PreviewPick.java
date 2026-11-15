package com.kcvisuals.gui.packs;

import com.kcvisuals.pack.Category;
import com.kcvisuals.pack.PackPath;
import java.util.Set;

/** Chooses which entry of a pack to show as the preview for a category. Pure Java, unit tested. */
public final class PreviewPick {
    private PreviewPick() {}

    /**
     * The entry whose normalised path equals the category's {@code previewPath} (legacy names included). If the pack
     * does not contain that file, the first PNG belonging to the category is used, preferring item textures. Returns
     * null if the pack has no PNG for the category.
     */
    public static String find(Set<String> entries, Category c) {
        String want = c.previewPath();
        String fallback = null;
        int fallbackRank = Integer.MAX_VALUE;
        for (String e : entries) {
            PackPath p = PackPath.of(e);
            if (p == null || !p.relative().endsWith(".png")) {
                continue;
            }
            if (p.relative().equals(want) || p.modern().equals(want)) {
                return e;
            }
            if (p.in(c)) {
                int rank = rank(p.modern());
                if (rank < fallbackRank || (rank == fallbackRank && e.compareTo(fallback) < 0)) {
                    fallback = e;
                    fallbackRank = rank;
                }
            }
        }
        return fallback;
    }

    /** Largest frame side (GUI-icon sized textures are at most this) before a texture counts as a UV atlas. */
    static final int ICON_MAX = 32;

    /**
     * True if the preview should not be shown as-is: the frame is larger than an icon ({@value #ICON_MAX} px on a
     * side) and the pack ships its own model for that item ({@code models/item/<name>.json} or
     * {@code items/<name>.json}), i.e. the texture is a UV atlas for a 3D model and would look like noise.
     */
    public static boolean isModelAtlas(Set<String> entries, String previewEntry, int frameW, int frameH) {
        if (frameW <= ICON_MAX && frameH <= ICON_MAX) {
            return false;
        }
        PackPath p = previewEntry == null ? null : PackPath.of(previewEntry);
        if (p == null || !p.modern().startsWith("textures/item/") || !p.modern().endsWith(".png")) {
            return false;
        }
        String name = p.modern().substring("textures/item/".length(), p.modern().length() - ".png".length());
        String model = "models/item/" + name + ".json";
        String item = "items/" + name + ".json";
        for (String e : entries) {
            PackPath q = PackPath.of(e);
            if (q != null && (q.modern().equals(model) || q.modern().equals(item)
                    || q.relative().equals(model) || q.relative().equals(item))) {
                return true;
            }
        }
        return false;
    }

    private static int rank(String path) {
        if (path.startsWith("textures/item/")) {
            return 0;
        }
        if (path.startsWith("textures/block/")) {
            return 1;
        }
        if (path.startsWith("textures/gui/")) {
            return 2;
        }
        return 3;
    }

    /**
     * Region {x, y, w, h} of the first animation frame: a vertical strip whose height is a multiple of its width is
     * treated as an animation and only its top square is used.
     */
    public static int[] firstFrame(int width, int height) {
        if (width > 0 && height > width && height % width == 0) {
            return new int[] {0, 0, width, width};
        }
        return new int[] {0, 0, width, height};
    }

    /** Size after shrinking (never enlarging) so that the longer side is at most {@code max}, keeping the aspect. */
    public static int[] fitWithin(int width, int height, int max) {
        int longer = Math.max(width, height);
        if (longer <= max) {
            return new int[] {width, height};
        }
        double f = max / (double) longer;
        return new int[] {Math.max(1, (int) Math.round(width * f)), Math.max(1, (int) Math.round(height * f))};
    }
}
