package com.kcvisuals.pack;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Entry-name normalisation shared by the zip and folder pack sources. */
final class PackEntries {
    private PackEntries() {}

    /**
     * Maps normalised name to original name. Backslashes become slashes; if nothing sits at the root {@code assets/}
     * but exactly one top-level folder contains {@code assets/}, that folder prefix is stripped.
     */
    static Map<String, String> normalise(Collection<String> rawNames) {
        Map<String, String> slashed = new LinkedHashMap<>();
        for (String raw : rawNames) {
            String n = raw.replace('\\', '/');
            while (n.startsWith("/")) {
                n = n.substring(1);
            }
            if (!n.isEmpty() && !n.endsWith("/")) {
                slashed.put(n, raw);
            }
        }

        String prefix = null;
        boolean rootAssets = false;
        Set<String> wrappers = new HashSet<>();
        for (String n : slashed.keySet()) {
            if (n.startsWith("assets/")) {
                rootAssets = true;
                break;
            }
            int slash = n.indexOf('/');
            if (slash > 0 && n.startsWith("assets/", slash + 1)) {
                wrappers.add(n.substring(0, slash + 1));
            }
        }
        if (!rootAssets && wrappers.size() == 1) {
            prefix = wrappers.iterator().next();
        }

        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : slashed.entrySet()) {
            String n = e.getKey();
            if (prefix != null && n.startsWith(prefix)) {
                n = n.substring(prefix.length());
            }
            result.putIfAbsent(n, e.getValue());
        }
        return result;
    }
}
