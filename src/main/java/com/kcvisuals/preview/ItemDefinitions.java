package com.kcvisuals.preview;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Picks the one model an item definition ({@code assets/<ns>/items/<name>.json}, 1.21.4+) shows in the inventory:
 * a {@code display_context} select takes the case for {@code gui}; other selects and dispatches take their fallback,
 * else their first case; a condition takes {@code on_false} (the idle state, e.g. a bow not being drawn); a composite
 * its first part. {@code special} models are drawn by code and only flagged.
 */
public final class ItemDefinitions {
    private static final int MAX_DEPTH = 32;
    /** Leaf models collected from one definition at most. */
    private static final int MAX_LEAVES = 256;

    /** @param model normalised model id, or null; @param special true for a {@code minecraft:special} model */
    public record Pick(String model, boolean special) {
        static final Pick NONE = new Pick(null, false);
    }

    private ItemDefinitions() {}

    public static Pick pick(String json) {
        return pick(json, null);
    }

    /**
     * Like {@link #pick(String)}, but when {@code fromPack} is given the first model (in the same order) for which it
     * is true wins over the default choice, so a pack whose definition only adds its own model on a non-default branch
     * (e.g. a knockback condition) is previewed with that model. Non-gui branches of a {@code display_context} select
     * are never considered: they are not shown in the inventory.
     */
    public static Pick pick(String json, Predicate<String> fromPack) {
        if (json == null) {
            return Pick.NONE;
        }
        List<Pick> leaves = new ArrayList<>();
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return Pick.NONE;
            }
            collect(root.getAsJsonObject().get("model"), 0, leaves);
        } catch (RuntimeException e) {
            return Pick.NONE;
        }
        if (fromPack != null) {
            for (Pick p : leaves) {
                if (p.model() != null && fromPack.test(p.model())) {
                    return p;
                }
            }
        }
        return leaves.isEmpty() ? Pick.NONE : leaves.get(0);
    }

    /** Leaf models in preference order (the default choice first). */
    private static void collect(JsonElement el, int depth, List<Pick> out) {
        if (el == null || !el.isJsonObject() || depth > MAX_DEPTH || out.size() >= MAX_LEAVES) {
            return;
        }
        JsonObject o = el.getAsJsonObject();
        String type = strip(ModelResolver.string(o.get("type")));
        if (type == null) {
            return;
        }
        switch (type) {
            case "model" -> {
                String m = ModelResolver.string(o.get("model"));
                if (m != null && !m.isBlank()) {
                    out.add(new Pick(ModelResolver.normalise(m), false));
                }
            }
            case "special" -> out.add(new Pick(null, true));
            case "select" -> {
                JsonArray cases = o.get("cases") != null && o.get("cases").isJsonArray() ? o.getAsJsonArray("cases") : new JsonArray();
                if ("display_context".equals(strip(ModelResolver.string(o.get("property"))))) {
                    int before = out.size();
                    for (JsonElement c : cases) {
                        if (c.isJsonObject() && whenHas(c.getAsJsonObject().get("when"), "gui")) {
                            collect(c.getAsJsonObject().get("model"), depth + 1, out);
                        }
                    }
                    if (out.size() > before) {
                        return; // the inventory only ever shows the gui case
                    }
                }
                collect(o.get("fallback"), depth + 1, out);
                for (JsonElement c : cases) {
                    if (c.isJsonObject()) {
                        collect(c.getAsJsonObject().get("model"), depth + 1, out);
                    }
                }
            }
            case "condition" -> {
                collect(o.get("on_false"), depth + 1, out);
                collect(o.get("on_true"), depth + 1, out);
            }
            case "range_dispatch" -> {
                collect(o.get("fallback"), depth + 1, out);
                JsonElement entries = o.get("entries");
                if (entries != null && entries.isJsonArray()) {
                    for (JsonElement e : entries.getAsJsonArray()) {
                        if (e.isJsonObject()) {
                            collect(e.getAsJsonObject().get("model"), depth + 1, out);
                        }
                    }
                }
            }
            case "composite" -> {
                JsonElement models = o.get("models");
                if (models != null && models.isJsonArray()) {
                    for (JsonElement m : models.getAsJsonArray()) {
                        collect(m, depth + 1, out);
                    }
                }
            }
            default -> {
                // empty, bundle/selected_item...: nothing to show
            }
        }
    }

    private static boolean whenHas(JsonElement when, String value) {
        if (when == null) {
            return false;
        }
        if (when.isJsonArray()) {
            for (JsonElement w : when.getAsJsonArray()) {
                if (value.equals(strip(ModelResolver.string(w)))) {
                    return true;
                }
            }
            return false;
        }
        return value.equals(strip(ModelResolver.string(when)));
    }

    private static String strip(String s) {
        if (s == null) {
            return null;
        }
        String l = s.trim().toLowerCase(Locale.ROOT);
        return l.startsWith("minecraft:") ? l.substring("minecraft:".length()) : l;
    }
}
