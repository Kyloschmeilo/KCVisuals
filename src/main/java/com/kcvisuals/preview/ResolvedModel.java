package com.kcvisuals.preview;

import java.util.List;
import java.util.Map;

/**
 * A model after following its parent chain.
 *
 * @param kind what kind of preview it can produce
 * @param elements the elements of the nearest model in the chain that defines any (empty for flat/special models)
 * @param textures texture variables merged child-over-parent (values may still be {@code #refs})
 * @param gui the nearest {@code display.gui}, or null when no model in the chain defines one
 * @param layers resolved {@code layer0..n} texture ids for flat models
 * @param chain normalised ids of the models read, child first
 */
public record ResolvedModel(Kind kind, List<ModelElement> elements, Map<String, String> textures, GuiTransform gui,
        List<String> layers, List<String> chain) {
    public enum Kind {
        /** Has elements: rendered in 3D. */
        MODEL,
        /** Chain ends in {@code item/generated} / {@code builtin/generated}: a flat icon from its layers. */
        FLAT,
        /** {@code builtin/entity}: drawn by code, no preview from the model. */
        SPECIAL,
        /** Unreadable, missing, cyclic or without elements. */
        MISSING
    }

    public ResolvedModel {
        elements = List.copyOf(elements);
        textures = Map.copyOf(textures);
        layers = List.copyOf(layers);
        chain = List.copyOf(chain);
    }

    /**
     * Texture id for a face texture: {@code #name} (or a bare variable name present in the map) is looked up and
     * followed through further references; anything else is taken as a texture id. Null if unresolved or cyclic.
     */
    public String texture(String ref) {
        if (ref == null) {
            return null;
        }
        String cur = ref;
        for (int i = 0; i < ModelResolver.MAX_DEPTH; i++) {
            String name = cur.startsWith("#") ? cur.substring(1) : cur;
            if (!cur.startsWith("#") && !textures.containsKey(name)) {
                return cur.isBlank() ? null : ModelResolver.normalise(cur);
            }
            String next = textures.get(name);
            if (next == null) {
                return null;
            }
            if (!next.startsWith("#")) {
                return next.isBlank() ? null : ModelResolver.normalise(next);
            }
            cur = next;
        }
        return null;
    }
}
