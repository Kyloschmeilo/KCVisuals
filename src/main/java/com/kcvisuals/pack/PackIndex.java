package com.kcvisuals.pack;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Result of scanning a packs directory: pack id (file or folder name) to what the pack contains. */
public record PackIndex(Map<String, PackInfo> packs) {
    public PackIndex {
        packs = Collections.unmodifiableMap(new LinkedHashMap<>(packs));
    }

    public record PackInfo(String id, String displayName, Path file, long size, long mtime, Set<String> categories) {
        public PackInfo {
            categories = Set.copyOf(categories);
        }
    }

    /** Packs that contribute at least one file to the category, sorted by display name. */
    public List<PackInfo> packsFor(String categoryId) {
        List<PackInfo> list = new ArrayList<>();
        for (PackInfo info : packs.values()) {
            if (info.categories().contains(categoryId)) {
                list.add(info);
            }
        }
        list.sort(Comparator.comparing(PackInfo::displayName, String.CASE_INSENSITIVE_ORDER));
        return list;
    }
}
