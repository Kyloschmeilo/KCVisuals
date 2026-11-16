package com.kcvisuals.gui.packs;

import com.kcvisuals.pack.Category;
import com.kcvisuals.pack.PackPath;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Chooses which entry of a pack to show as the preview for a category, and whether that texture is a 3D-model atlas.
 * No Minecraft classes (only Gson for the model JSON), unit tested.
 */
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

    /** Preview textures whose name is not the item's: the item whose model shows the category. */
    private static final java.util.Map<String, String> ITEM_ALIASES = java.util.Map.of(
            "crossbow_standby", "crossbow",
            "respawn_anchor_top", "respawn_anchor");

    /**
     * The item whose inventory model previews the category, derived from {@code previewPath}
     * ({@code textures/item/<name>.png} or {@code textures/block/<name>.png}); null for categories that are not an item
     * (particles, GUI, sky...). Whether the item really exists is decided by the model lookup.
     */
    public static String previewItem(Category c) {
        String p = c.previewPath();
        String name = null;
        for (String dir : new String[] {"textures/item/", "textures/block/"}) {
            if (p.startsWith(dir) && p.endsWith(".png") && p.indexOf('/', dir.length()) < 0) {
                name = p.substring(dir.length(), p.length() - ".png".length());
            }
        }
        if (name == null || name.isEmpty()) {
            return null;
        }
        return ITEM_ALIASES.getOrDefault(name, name);
    }

    /**
     * Other items of the category whose inventory model the pack defines, to preview a pack that changes a category
     * member other than {@link #previewItem}: names from {@code items/<name>.json} first, then from
     * {@code models/item/<name>.json} (legacy names mapped), each list sorted, without duplicates and without the
     * preview item itself. Only {@code minecraft} entries directly in those folders count.
     */
    public static List<String> representativeItems(Set<String> entries, Category c) {
        String skip = previewItem(c);
        java.util.TreeSet<String> defs = new java.util.TreeSet<>();
        java.util.TreeSet<String> models = new java.util.TreeSet<>();
        for (String e : entries) {
            PackPath p = PackPath.of(e);
            if (p == null || !p.modern().endsWith(".json") || !p.in(c)) {
                continue;
            }
            for (String dir : new String[] {"items/", "models/item/"}) {
                String m = p.modern();
                if (m.startsWith(dir) && m.indexOf('/', dir.length()) < 0) {
                    String name = m.substring(dir.length(), m.length() - ".json".length());
                    if (!name.isEmpty() && !name.equals(skip)) {
                        (dir.equals("items/") ? defs : models).add(name);
                    }
                }
            }
        }
        List<String> out = new ArrayList<>(defs);
        for (String m : models) {
            if (!defs.contains(m)) {
                out.add(m);
            }
        }
        return out;
    }

    /** Largest decoded image (pixels) a preview reads; a small PNG can still declare a huge size. */
    public static final long MAX_PIXELS = 4096L * 4096L;

    /** Width times height from a PNG's IHDR chunk, or -1 if {@code data} does not start like a PNG. */
    public static long pngPixels(byte[] data) {
        byte[] sig = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        if (data == null || data.length < 24) {
            return -1;
        }
        for (int i = 0; i < 8; i++) {
            if (data[i] != sig[i]) {
                return -1;
            }
        }
        if (data[12] != 'I' || data[13] != 'H' || data[14] != 'D' || data[15] != 'R') {
            return -1;
        }
        long w = ((data[16] & 0xFFL) << 24) | ((data[17] & 0xFF) << 16) | ((data[18] & 0xFF) << 8) | (data[19] & 0xFF);
        long h = ((data[20] & 0xFFL) << 24) | ((data[21] & 0xFF) << 16) | ((data[22] & 0xFF) << 8) | (data[23] & 0xFF);
        return w * h;
    }

    /** Upper bound for a preview PNG read from a pack; bigger entries get no preview (zip-bomb guard). */
    public static final int MAX_IMAGE_BYTES = 8 << 20;
    /** Upper bound for an item model / item definition JSON read for the atlas check. */
    public static final int MAX_JSON_BYTES = 256 << 10;

    /**
     * Reads at most {@code max} bytes; returns null (instead of a truncated array) if the stream holds more, so an
     * oversized or decompression-bomb entry never gets buffered in full.
     */
    public static byte[] readCapped(InputStream in, int max) throws IOException {
        byte[] b = in.readNBytes(max + 1);
        return b.length > max ? null : b;
    }

    /** Largest frame side (GUI-icon sized textures are at most this) before a texture counts as a UV atlas. */
    static final int ICON_MAX = 32;

    /**
     * True if the preview should not be shown as-is: the frame is larger than an icon ({@value #ICON_MAX} px on a
     * side) and the pack gives that item a custom, non-flat model, i.e. the texture is a UV atlas for a 3D model.
     * A large texture whose item model is a flat icon ({@code item/generated} or {@code item/handheld} parent with a
     * {@code layer0}) is an HD icon and is not an atlas. The item definition {@code items/<name>.json} takes precedence
     * (all models it references must be flat icons in the pack); {@code models/item/<name>.json} is only consulted when
     * there is no item definition. {@code read} returns an entry's text, or null.
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
        // Since 1.21.4 the item definition (items/<name>.json) decides which model is used; models/item/<name>.json
        // only matters when the pack has no item definition for this item.
        String itemEntry = findEntry(entries, "items/" + name + ".json");
        if (itemEntry != null) {
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
        String modelEntry = findEntry(entries, "models/item/" + name + ".json");
        return modelEntry != null && !isFlatIconModel(read.apply(modelEntry));
    }

    /**
     * Entry whose path below {@code assets/minecraft/} equals {@code relative}. Deterministic: an entry already at
     * that (modern) path wins over one that only matches after legacy mapping; ties go to the smallest entry name.
     */
    static String findEntry(Set<String> entries, String relative) {
        String exact = null;
        String legacy = null;
        for (String e : entries) {
            PackPath q = PackPath.of(e);
            if (q == null) {
                continue;
            }
            if (q.relative().equals(relative)) {
                if (exact == null || e.compareTo(exact) < 0) {
                    exact = e;
                }
            } else if (q.modern().equals(relative)) {
                if (legacy == null || e.compareTo(legacy) < 0) {
                    legacy = e;
                }
            }
        }
        return exact != null ? exact : legacy;
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
