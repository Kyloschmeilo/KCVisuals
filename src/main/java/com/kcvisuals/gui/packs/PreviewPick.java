package com.kcvisuals.gui.packs;

import com.kcvisuals.pack.Category;
import com.kcvisuals.pack.PackPath;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

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
     * side) and the pack gives that item a custom, non-flat model, i.e. the texture is a UV atlas for a 3D model.
     * A large texture whose item model is a flat icon ({@code item/generated} or {@code item/handheld} parent with a
     * {@code layer0}) is an HD icon and is not an atlas. {@code read} returns an entry's text, or null.
     */
    public static boolean isModelAtlas(Set<String> entries, String previewEntry, int frameW, int frameH,
            Function<String, String> read) {
        if (frameW <= ICON_MAX && frameH <= ICON_MAX) {
            return false;
        }
        PackPath p = previewEntry == null ? null : PackPath.of(previewEntry);
        if (p == null || !p.modern().startsWith("textures/item/") || !p.modern().endsWith(".png")) {
            return false;
        }
        String name = p.modern().substring("textures/item/".length(), p.modern().length() - ".png".length());
        String modelEntry = findEntry(entries, "models/item/" + name + ".json");
        if (modelEntry != null) {
            return !isFlatIconModel(read.apply(modelEntry));
        }
        String itemEntry = findEntry(entries, "items/" + name + ".json");
        if (itemEntry == null) {
            return false;
        }
        List<String> refs = itemModelRefs(read.apply(itemEntry));
        if (refs.isEmpty()) {
            return true;
        }
        for (String ref : refs) {
            String refEntry = findEntry(entries, "models/" + stripNamespace(ref) + ".json");
            if (refEntry == null || !isFlatIconModel(read.apply(refEntry))) {
                return true;
            }
        }
        return false;
    }

    /** Entry whose path below {@code assets/minecraft/} (as found or legacy-mapped) equals {@code relative}. */
    static String findEntry(Set<String> entries, String relative) {
        for (String e : entries) {
            PackPath q = PackPath.of(e);
            if (q != null && (q.relative().equals(relative) || q.modern().equals(relative))) {
                return e;
            }
        }
        return null;
    }

    private static String stripNamespace(String id) {
        String s = id.toLowerCase(Locale.ROOT);
        return s.startsWith("minecraft:") ? s.substring("minecraft:".length()) : s;
    }

    /** A model that renders as a flat item icon: generated/handheld parent and a {@code layer0} texture. */
    static boolean isFlatIconModel(String json) {
        if (json == null) {
            return false;
        }
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return false;
            }
            JsonObject o = root.getAsJsonObject();
            if (!o.has("parent") || !o.get("parent").isJsonPrimitive()) {
                return false;
            }
            String parent = stripNamespace(o.get("parent").getAsString());
            if (parent.startsWith("items/")) {
                parent = "item/" + parent.substring("items/".length()); // pre-1.13 folder name
            }
            boolean flatParent = parent.equals("item/generated") || parent.equals("item/handheld")
                    || parent.equals("builtin/generated");
            JsonObject textures = o.has("textures") && o.get("textures").isJsonObject() ? o.getAsJsonObject("textures") : null;
            return flatParent && textures != null && textures.has("layer0");
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Model ids referenced by an item definition ({@code items/<name>.json}): every {@code "model"} string of a
     * {@code minecraft:model} entry, searched recursively through selects, conditions and the like.
     */
    static List<String> itemModelRefs(String json) {
        List<String> out = new ArrayList<>();
        if (json == null) {
            return out;
        }
        try {
            collectModelRefs(JsonParser.parseString(json), out);
        } catch (RuntimeException e) {
            out.clear();
        }
        return out;
    }

    private static void collectModelRefs(JsonElement el, List<String> out) {
        if (el.isJsonArray()) {
            for (JsonElement e : el.getAsJsonArray()) {
                collectModelRefs(e, out);
            }
        } else if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            JsonElement type = o.get("type");
            JsonElement model = o.get("model");
            if (type != null && type.isJsonPrimitive() && stripNamespace(type.getAsString()).equals("model")
                    && model != null && model.isJsonPrimitive()) {
                out.add(model.getAsString());
            }
            for (var e : o.entrySet()) {
                if (!(e.getKey().equals("model") && e.getValue().isJsonPrimitive())) {
                    collectModelRefs(e.getValue(), out);
                }
            }
        }
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
