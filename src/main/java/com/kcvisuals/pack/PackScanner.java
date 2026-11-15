package com.kcvisuals.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Scans a directory of resource packs (zips and folders) and records which categories each one touches. */
public final class PackScanner {
    private static final System.Logger LOG = System.getLogger("kcvisuals");
    private static final String ASSETS_PREFIX = "assets/minecraft/";
    private static final int INDEX_VERSION = 2;

    private PackScanner() {}

    /** Hook so tests can count how often a pack is really opened. */
    @FunctionalInterface
    interface PackOpener {
        PackSource open(Path zipOrDir) throws IOException;
    }

    /** Scans without a persistent cache. */
    public static PackIndex scan(Path packsDir, CategoryRegistry reg) {
        return scan(packsDir, reg, null, PackSource::open);
    }

    /** Scans, reusing and updating the cache at {@code indexFile} keyed by (file name, size, mtime). */
    public static PackIndex scan(Path packsDir, CategoryRegistry reg, Path indexFile) {
        return scan(packsDir, reg, indexFile, PackSource::open);
    }

    static PackIndex scan(Path packsDir, CategoryRegistry reg, Path indexFile, PackOpener opener) {
        Map<String, PackIndex.PackInfo> cached = indexFile == null ? Map.of() : readCache(indexFile, packsDir, reg);
        Map<String, PackIndex.PackInfo> result = new LinkedHashMap<>();

        for (Path file : listCandidates(packsDir)) {
            String id = file.getFileName().toString();
            boolean isDir = Files.isDirectory(file);
            try {
                long size = isDir ? 0 : Files.size(file);
                long mtime = Files.getLastModifiedTime(file).toMillis();
                PackIndex.PackInfo hit = cached.get(id);
                if (!isDir && hit != null && hit.size() == size && hit.mtime() == mtime) {
                    result.put(id, hit);
                    continue;
                }
                try (PackSource src = opener.open(file)) {
                    result.put(id, new PackIndex.PackInfo(
                            id, src.displayName(), file, size, mtime, categorise(src.entries(), reg)));
                }
            } catch (IOException | RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Skipping unreadable pack " + id + ": " + e);
            }
        }

        if (indexFile != null) {
            writeCache(indexFile, reg, result);
        }
        return new PackIndex(result);
    }

    /** Category ids that at least one entry of the pack belongs to. */
    static Set<String> categorise(Set<String> entries, CategoryRegistry reg) {
        Set<String> found = new LinkedHashSet<>();
        List<Category> remaining = new ArrayList<>(reg.all());
        for (String entry : entries) {
            if (remaining.isEmpty()) {
                break;
            }
            if (!entry.startsWith(ASSETS_PREFIX)) {
                continue;
            }
            String rel = entry.substring(ASSETS_PREFIX.length()).toLowerCase(java.util.Locale.ROOT);
            String norm = LegacyPaths.normalise(rel);
            remaining.removeIf(c -> {
                boolean hit = c.matches(rel) || (!norm.equals(rel) && c.matches(norm));
                if (hit) {
                    found.add(c.id());
                }
                return hit;
            });
        }
        return found;
    }

    private static List<Path> listCandidates(Path packsDir) {
        if (!Files.isDirectory(packsDir)) {
            return List.of();
        }
        try (Stream<Path> list = Files.list(packsDir)) {
            return list.filter(p -> {
                        String name = p.getFileName().toString();
                        if (name.startsWith(".")) {
                            return false;
                        }
                        return Files.isDirectory(p)
                                || (Files.isRegularFile(p) && name.toLowerCase(Locale.ROOT).endsWith(".zip"));
                    })
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Cannot list " + packsDir + ": " + e);
            return List.of();
        }
    }

    private static Map<String, PackIndex.PackInfo> readCache(Path indexFile, Path packsDir, CategoryRegistry reg) {
        if (!Files.isRegularFile(indexFile)) {
            return Map.of();
        }
        try (Reader r = Files.newBufferedReader(indexFile, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            if (root.get("version").getAsInt() != INDEX_VERSION
                    || !reg.fingerprint().equals(root.get("registry").getAsString())) {
                return Map.of();
            }
            Map<String, PackIndex.PackInfo> map = new HashMap<>();
            for (JsonElement e : root.getAsJsonArray("packs")) {
                JsonObject o = e.getAsJsonObject();
                Set<String> cats = new LinkedHashSet<>();
                for (JsonElement c : o.getAsJsonArray("categories")) {
                    cats.add(c.getAsString());
                }
                String id = o.get("id").getAsString();
                map.put(id, new PackIndex.PackInfo(
                        id,
                        o.get("displayName").getAsString(),
                        packsDir.resolve(id),
                        o.get("size").getAsLong(),
                        o.get("mtime").getAsLong(),
                        cats));
            }
            return map;
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Ignoring unreadable pack index " + indexFile + ": " + e);
            return Map.of();
        }
    }

    private static void writeCache(Path indexFile, CategoryRegistry reg, Map<String, PackIndex.PackInfo> packs) {
        JsonObject root = new JsonObject();
        root.addProperty("version", INDEX_VERSION);
        root.addProperty("registry", reg.fingerprint());
        JsonArray arr = new JsonArray();
        for (PackIndex.PackInfo info : packs.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", info.id());
            o.addProperty("displayName", info.displayName());
            o.addProperty("size", info.size());
            o.addProperty("mtime", info.mtime());
            JsonArray cats = new JsonArray();
            info.categories().stream().sorted().forEach(cats::add);
            o.add("categories", cats);
            arr.add(o);
        }
        root.add("packs", arr);
        try {
            Path parent = indexFile.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            Path tmp = Files.createTempFile(parent, "index", ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                w.write(root.toString());
            }
            Files.move(tmp, indexFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not write pack index " + indexFile + ": " + e);
        }
    }
}
