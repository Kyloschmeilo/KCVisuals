package com.kcvisuals.preview;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Locale;

/**
 * Picks the one model an item definition ({@code assets/<ns>/items/<name>.json}, 1.21.4+) shows in the inventory:
 * a {@code display_context} select takes the case for {@code gui}; other selects and dispatches take their fallback,
 * else their first case; a condition takes {@code on_false} (the idle state, e.g. a bow not being drawn); a composite
 * its first part. {@code special} models are drawn by code and only flagged.
 */
public final class ItemDefinitions {
    private static final int MAX_DEPTH = 32;

    /** @param model normalised model id, or null; @param special true for a {@code minecraft:special} model */
    public record Pick(String model, boolean special) {
        static final Pick NONE = new Pick(null, false);
    }

    private ItemDefinitions() {}

    public static Pick pick(String json) {
        if (json == null) {
            return Pick.NONE;
        }
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return Pick.NONE;
            }
            return node(root.getAsJsonObject().get("model"), 0);
        } catch (RuntimeException e) {
            return Pick.NONE;
        }
    }

    private static Pick node(JsonElement el, int depth) {
        if (el == null || !el.isJsonObject() || depth > MAX_DEPTH) {
            return Pick.NONE;
        }
        JsonObject o = el.getAsJsonObject();
        String type = strip(ModelResolver.string(o.get("type")));
        if (type == null) {
            return Pick.NONE;
        }
        switch (type) {
            case "model": {
                String m = ModelResolver.string(o.get("model"));
                return m == null || m.isBlank() ? Pick.NONE : new Pick(ModelResolver.normalise(m), false);
            }
            case "special":
                return new Pick(null, true);
            case "select": {
                JsonArray cases = o.get("cases") != null && o.get("cases").isJsonArray() ? o.getAsJsonArray("cases") : new JsonArray();
                if ("display_context".equals(strip(ModelResolver.string(o.get("property"))))) {
                    for (JsonElement c : cases) {
                        if (c.isJsonObject() && whenHas(c.getAsJsonObject().get("when"), "gui")) {
                            Pick p = node(c.getAsJsonObject().get("model"), depth + 1);
                            if (p.model() != null || p.special()) {
                                return p;
                            }
                        }
                    }
                }
                Pick fb = node(o.get("fallback"), depth + 1);
                if (fb.model() != null || fb.special()) {
                    return fb;
                }
                for (JsonElement c : cases) {
                    if (c.isJsonObject()) {
                        Pick p = node(c.getAsJsonObject().get("model"), depth + 1);
                        if (p.model() != null || p.special()) {
                            return p;
                        }
                    }
                }
                return Pick.NONE;
            }
            case "condition": {
                Pick p = node(o.get("on_false"), depth + 1);
                return p.model() != null || p.special() ? p : node(o.get("on_true"), depth + 1);
            }
            case "range_dispatch": {
                Pick fb = node(o.get("fallback"), depth + 1);
                if (fb.model() != null || fb.special()) {
                    return fb;
                }
                JsonElement entries = o.get("entries");
                if (entries != null && entries.isJsonArray()) {
                    for (JsonElement e : entries.getAsJsonArray()) {
                        if (e.isJsonObject()) {
                            Pick p = node(e.getAsJsonObject().get("model"), depth + 1);
                            if (p.model() != null || p.special()) {
                                return p;
                            }
                        }
                    }
                }
                return Pick.NONE;
            }
            case "composite": {
                JsonElement models = o.get("models");
                if (models != null && models.isJsonArray()) {
                    for (JsonElement m : models.getAsJsonArray()) {
                        Pick p = node(m, depth + 1);
                        if (p.model() != null || p.special()) {
                            return p;
                        }
                    }
                }
                return Pick.NONE;
            }
            default:
                return Pick.NONE;
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
