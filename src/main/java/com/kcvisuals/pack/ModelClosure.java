package com.kcvisuals.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Files a category's files depend on inside the same pack: models referenced by item definitions
 * ({@code items/*.json}), parents of models, and textures (plus {@code .png.mcmeta}) referenced by models. Without
 * them a custom parent such as {@code item/smaller_util} would be missing and the item renders as the missing model.
 * All paths here are full output paths ({@link PackPath#output}), so legacy names and other namespaces resolve the
 * same way the files are written.
 */
final class ModelClosure {
    private ModelClosure() {}

    /** Output path to raw entry for every file of the pack; a modern entry wins over a legacy one mapping to it. */
    static Map<String, String> outputIndex(Collection<String> entries) {
        Map<String, String> index = new TreeMap<>();
        for (String entry : new java.util.TreeSet<>(entries)) {
            String out = PackPath.output(entry);
            if (out == null) {
                continue;
            }
            String prev = index.get(out);
            if (prev == null || (PackPath.isLegacyEntry(prev) && !PackPath.isLegacyEntry(entry))) {
                index.put(out, entry);
            }
        }
        return index;
    }

    /**
     * Dependencies of {@code roots} (output paths already selected) found in {@code src}: output path to raw entry,
     * roots excluded. Breadth-first over a visited set, so reference cycles terminate.
     */
    static Map<String, String> closure(PackSource src, Map<String, String> outputIndex, Collection<String> roots) {
        Map<String, String> found = new LinkedHashMap<>();
        Set<String> visited = new HashSet<>(roots);
        Deque<String> queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            String out = queue.poll();
            String raw = outputIndex.get(out);
            if (raw == null || !out.endsWith(".json")) {
                continue;
            }
            for (String dep : dependencies(out, read(src, raw))) {
                if (outputIndex.containsKey(dep) && visited.add(dep)) {
                    found.put(dep, outputIndex.get(dep));
                    queue.add(dep);
                }
            }
        }
        return found;
    }

    /** Output paths referenced by the json at {@code out} (item definition or model); empty for other files. */
    static List<String> dependencies(String out, JsonElement json) {
        if (json == null) {
            return List.of();
        }
        String kind = kindOf(out);
        List<String> deps = new java.util.ArrayList<>();
        if ("items".equals(kind)) {
            collectItemModels(json, deps);
        } else if ("models".equals(kind) && json.isJsonObject()) {
            JsonObject model = json.getAsJsonObject();
            String parent = string(model.get("parent"));
            if (parent != null && !parent.toLowerCase(Locale.ROOT).startsWith("builtin/")) {
                addModel(parent, deps);
            }
            JsonElement textures = model.get("textures");
            if (textures != null && textures.isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : textures.getAsJsonObject().entrySet()) {
                    String ref = string(e.getValue());
                    if (ref != null && !ref.startsWith("#")) {
                        String tex = resolve(ref, "textures/", ".png");
                        if (tex != null) {
                            deps.add(tex);
                            deps.add(tex + ".mcmeta");
                        }
                    }
                }
            }
            JsonElement overrides = model.get("overrides");
            if (overrides != null && overrides.isJsonArray()) {
                for (JsonElement o : overrides.getAsJsonArray()) {
                    if (o.isJsonObject()) {
                        String m = string(o.getAsJsonObject().get("model"));
                        if (m != null) {
                            addModel(m, deps);
                        }
                    }
                }
            }
        }
        return deps;
    }

    /** Any string {@code "model"} (and special-model {@code "base"}) anywhere in an item definition tree. */
    private static void collectItemModels(JsonElement el, List<String> deps) {
        if (el.isJsonArray()) {
            el.getAsJsonArray().forEach(child -> collectItemModels(child, deps));
        } else if (el.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                String s = string(e.getValue());
                if (s != null && (e.getKey().equals("model") || e.getKey().equals("base"))) {
                    addModel(s, deps);
                } else {
                    collectItemModels(e.getValue(), deps);
                }
            }
        }
    }

    private static void addModel(String id, List<String> deps) {
        String path = resolve(id, "models/", ".json");
        if (path != null) {
            deps.add(path);
        }
    }

    /** {@code [ns:]path} to the output path {@code assets/<ns>/<dir><path><ext>}; minecraft is the default. */
    static String resolve(String id, String dir, String ext) {
        String lower = id.trim().toLowerCase(Locale.ROOT);
        int colon = lower.indexOf(':');
        String ns = colon < 0 ? "minecraft" : lower.substring(0, colon);
        String path = lower.substring(colon + 1);
        if (ns.isEmpty() || path.isEmpty()) {
            return null;
        }
        return PackPath.output("assets/" + ns + "/" + dir + path + ext);
    }

    private static String kindOf(String out) {
        int nsEnd = out.indexOf('/', "assets/".length());
        int kindEnd = nsEnd < 0 ? -1 : out.indexOf('/', nsEnd + 1);
        return kindEnd < 0 ? null : out.substring(nsEnd + 1, kindEnd);
    }

    private static String string(JsonElement el) {
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString() ? el.getAsString() : null;
    }

    /** Parsed json, or null if the entry cannot be read or parsed (the copy step reports unreadable entries). */
    private static JsonElement read(PackSource src, String raw) {
        try (InputStream in = src.open(raw)) {
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
