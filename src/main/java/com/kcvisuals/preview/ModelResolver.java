package com.kcvisuals.preview;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Follows a Java model's parent chain (pack data is untrusted: malformed parts are skipped, cycles and long chains
 * stop at {@link #MAX_DEPTH}). Model ids are looked up through a caller-supplied function, so the caller decides the
 * source order (the pack first, then vanilla). Pure Java (Gson only), unit tested.
 */
public final class ModelResolver {
    /** Longest parent chain / texture reference chain followed. */
    public static final int MAX_DEPTH = 16;
    /** Elements read from one model at most. */
    static final int MAX_ELEMENTS = 2048;

    private ModelResolver() {}

    /** {@code [ns:]path}, lowercased and trimmed, with the {@code minecraft} namespace made explicit. */
    public static String normalise(String id) {
        String s = id.trim().toLowerCase(Locale.ROOT);
        return s.indexOf(':') < 0 ? "minecraft:" + s : s;
    }

    private static boolean isGenerated(String id) {
        return id.equals("minecraft:item/generated") || id.equals("minecraft:builtin/generated");
    }

    /**
     * Resolves {@code modelId}. {@code lookup} maps a normalised model id ({@code minecraft:block/cube_all}) to its json
     * text, or null if neither the pack nor vanilla has it.
     */
    public static ResolvedModel resolve(String modelId, Function<String, String> lookup) {
        List<String> chain = new ArrayList<>();
        List<JsonObject> models = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        ResolvedModel.Kind marker = null;
        String id = modelId == null ? null : normalise(modelId);
        while (id != null && chain.size() < MAX_DEPTH) {
            if (isGenerated(id)) {
                marker = ResolvedModel.Kind.FLAT;
                break;
            }
            if (id.equals("minecraft:builtin/entity")) {
                marker = ResolvedModel.Kind.SPECIAL;
                break;
            }
            if (!visited.add(id)) {
                break;
            }
            JsonObject o = parseObject(lookup.apply(id));
            if (o == null) {
                break;
            }
            chain.add(id);
            models.add(o);
            String parent = string(o.get("parent"));
            id = parent == null || parent.isBlank() ? null : normalise(parent);
        }

        Map<String, String> textures = new HashMap<>();
        for (int i = models.size() - 1; i >= 0; i--) {
            JsonElement t = models.get(i).get("textures");
            if (t != null && t.isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : t.getAsJsonObject().entrySet()) {
                    String v = textureValue(e.getValue());
                    if (v != null) {
                        textures.put(e.getKey(), v);
                    }
                }
            }
        }
        List<ModelElement> elements = List.of();
        GuiTransform gui = null;
        for (JsonObject o : models) {
            if (elements.isEmpty() && o.get("elements") != null && o.get("elements").isJsonArray()) {
                elements = parseElements(o.getAsJsonArray("elements"));
            }
            if (gui == null) {
                gui = parseGui(o.get("display"));
            }
        }

        ResolvedModel.Kind kind;
        List<String> layers = new ArrayList<>();
        if (marker == ResolvedModel.Kind.FLAT) {
            kind = ResolvedModel.Kind.FLAT;
            ResolvedModel probe = new ResolvedModel(kind, List.of(), textures, gui, List.of(), chain);
            for (int i = 0; textures.containsKey("layer" + i) && i < 16; i++) {
                String tex = probe.texture("#layer" + i);
                if (tex != null) {
                    layers.add(tex);
                }
            }
        } else if (marker == ResolvedModel.Kind.SPECIAL) {
            kind = ResolvedModel.Kind.SPECIAL;
        } else {
            kind = elements.isEmpty() ? ResolvedModel.Kind.MISSING : ResolvedModel.Kind.MODEL;
        }
        return new ResolvedModel(kind, elements, textures, gui, layers, chain);
    }

    private static JsonObject parseObject(String json) {
        if (json == null) {
            return null;
        }
        try {
            JsonElement el = JsonParser.parseString(json);
            return el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** A texture variable value: a plain string, or the newer {@code {"sprite": ...}} object form. */
    private static String textureValue(JsonElement v) {
        String s = string(v);
        if (s == null && v != null && v.isJsonObject()) {
            s = string(v.getAsJsonObject().get("sprite"));
        }
        return s;
    }

    static String string(JsonElement el) {
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString() ? el.getAsString() : null;
    }

    private static GuiTransform parseGui(JsonElement display) {
        if (display == null || !display.isJsonObject()) {
            return null;
        }
        JsonElement gui = display.getAsJsonObject().get("gui");
        if (gui == null || !gui.isJsonObject()) {
            return null;
        }
        JsonObject g = gui.getAsJsonObject();
        float[] r = vec3(g.get("rotation"));
        float[] t = vec3(g.get("translation"));
        float[] s = vec3(g.get("scale"));
        return new GuiTransform(r == null ? new float[3] : r, t == null ? new float[3] : t,
                s == null ? new float[] {1, 1, 1} : s);
    }

    /** Three finite numbers, or null. */
    private static float[] vec3(JsonElement el) {
        if (el == null || !el.isJsonArray() || el.getAsJsonArray().size() != 3) {
            return null;
        }
        float[] out = new float[3];
        for (int i = 0; i < 3; i++) {
            Float f = number(el.getAsJsonArray().get(i));
            if (f == null) {
                return null;
            }
            out[i] = f;
        }
        return out;
    }

    private static Float number(JsonElement el) {
        if (el == null || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        float f = el.getAsFloat();
        return Float.isFinite(f) ? f : null;
    }

    private static List<ModelElement> parseElements(JsonArray arr) {
        List<ModelElement> out = new ArrayList<>();
        for (JsonElement el : arr) {
            if (out.size() >= MAX_ELEMENTS) {
                break;
            }
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject o = el.getAsJsonObject();
            float[] from = vec3(o.get("from"));
            float[] to = vec3(o.get("to"));
            if (from == null || to == null || o.get("faces") == null || !o.get("faces").isJsonObject()) {
                continue;
            }
            Map<Direction, ModelElement.Face> faces = new EnumMap<>(Direction.class);
            for (Map.Entry<String, JsonElement> f : o.getAsJsonObject("faces").entrySet()) {
                Direction d = Direction.byName(f.getKey());
                if (d == null || !f.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject fo = f.getValue().getAsJsonObject();
                String tex = string(fo.get("texture"));
                if (tex == null) {
                    continue;
                }
                float[] uv = null;
                JsonElement uvEl = fo.get("uv");
                if (uvEl != null && uvEl.isJsonArray() && uvEl.getAsJsonArray().size() == 4) {
                    uv = new float[4];
                    for (int i = 0; i < 4 && uv != null; i++) {
                        Float n = number(uvEl.getAsJsonArray().get(i));
                        if (n == null) {
                            uv = null;
                        } else {
                            uv[i] = n;
                        }
                    }
                }
                Float rot = number(fo.get("rotation"));
                int r = rot == null ? 0 : Math.floorMod(Math.round(rot / 90f) * 90, 360);
                faces.put(d, new ModelElement.Face(uv, tex, r));
            }
            if (faces.isEmpty()) {
                continue;
            }
            ModelElement.Rotation rotation = null;
            JsonElement rotEl = o.get("rotation");
            if (rotEl != null && rotEl.isJsonObject()) {
                JsonObject ro = rotEl.getAsJsonObject();
                float[] origin = vec3(ro.get("origin"));
                Float angle = number(ro.get("angle"));
                String axis = string(ro.get("axis"));
                int ax = axis == null ? -1 : "xyz".indexOf(axis.toLowerCase(Locale.ROOT));
                if (angle != null && ax >= 0 && axis.length() == 1) {
                    boolean rescale = ro.get("rescale") != null && ro.get("rescale").isJsonPrimitive()
                            && ro.get("rescale").getAsJsonPrimitive().isBoolean() && ro.get("rescale").getAsBoolean();
                    rotation = new ModelElement.Rotation(origin == null ? new float[] {8, 8, 8} : origin, ax, angle, rescale);
                }
            }
            boolean shade = !(o.get("shade") != null && o.get("shade").isJsonPrimitive()
                    && o.get("shade").getAsJsonPrimitive().isBoolean() && !o.get("shade").getAsBoolean());
            out.add(new ModelElement(from, to, rotation, shade, faces));
        }
        return out;
    }
}
