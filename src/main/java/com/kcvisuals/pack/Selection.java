package com.kcvisuals.pack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Which pack supplies each category: category id to pack id. A missing key means Vanilla. Immutable. */
public record Selection(Map<String, String> picks) {
    private static final Selection EMPTY = new Selection(Map.of());

    public Selection {
        picks = Collections.unmodifiableMap(new LinkedHashMap<>(picks));
    }

    public static Selection empty() {
        return EMPTY;
    }

    public static Selection of(Map<String, String> picks) {
        return new Selection(picks);
    }

    /** The pack id for the category, or null for Vanilla. */
    public String packFor(String categoryId) {
        return picks.get(categoryId);
    }

    public Selection with(String categoryId, String packId) {
        Map<String, String> m = new LinkedHashMap<>(picks);
        if (packId == null) {
            m.remove(categoryId);
        } else {
            m.put(categoryId, packId);
        }
        return new Selection(m);
    }

    public Selection without(String categoryId) {
        return with(categoryId, null);
    }

    public boolean isEmpty() {
        return picks.isEmpty();
    }
}
