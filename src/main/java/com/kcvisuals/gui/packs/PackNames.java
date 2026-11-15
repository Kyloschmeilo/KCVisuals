package com.kcvisuals.gui.packs;

import java.util.Locale;

/** Display-only cleanup of pack names and the search filter used by the Packs tab. Pure Java, unit tested. */
public final class PackNames {
    private PackNames() {}

    /**
     * Removes {@code §x} formatting codes, a trailing {@code .zip} and leading {@code !} / whitespace that pack
     * authors use to push their pack to the top of a sorted list. Falls back to the trimmed input if nothing is left.
     */
    public static String clean(String name) {
        if (name == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '§') {
                i++;
            } else {
                sb.append(c);
            }
        }
        String s = sb.toString().strip();
        if (s.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            s = s.substring(0, s.length() - 4).strip();
        }
        int start = 0;
        while (start < s.length() && (s.charAt(start) == '!' || Character.isWhitespace(s.charAt(start)))) {
            start++;
        }
        String out = s.substring(start).strip().replaceAll("\s{2,}", " ");
        return out.isEmpty() ? s : out;
    }

    /** Lowercased, cleaned form used for matching. */
    static String key(String name) {
        return clean(name).toLowerCase(Locale.ROOT);
    }

    /**
     * True if every whitespace-separated token of {@code query} occurs in the cleaned, lowercased name. A blank query
     * matches everything.
     */
    public static boolean matches(String name, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String k = key(name);
        for (String token : query.toLowerCase(Locale.ROOT).strip().split("\s+")) {
            if (!k.contains(token)) {
                return false;
            }
        }
        return true;
    }
}
