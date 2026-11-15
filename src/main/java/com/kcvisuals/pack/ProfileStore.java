package com.kcvisuals.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Named selections stored as one JSON file each, plus the active profile name. Pure Java, no Minecraft types. */
public final class ProfileStore {
    public static final String DEFAULT = "Standard";
    private static final Logger LOG = LoggerFactory.getLogger("KC Visuals");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String INVALID_CHARS = "/\\:*?\"<>|";

    public record LoadResult(Selection selection, List<String> droppedCategories) {}

    private final Path dir;

    public ProfileStore(Path dir) {
        this.dir = dir;
    }

    public List<String> list() {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(dir)) {
            List<String> names = new ArrayList<>();
            s.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json"))
                    .forEach(n -> names.add(n.substring(0, n.length() - 5)));
            Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
            return names;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Loads a profile; a missing or corrupt file yields an empty selection. Picks of packs not in the index are dropped. */
    public LoadResult load(String name, PackIndex idx) {
        Path file = file(name);
        Map<String, String> picks = new LinkedHashMap<>();
        if (Files.isRegularFile(file)) {
            try {
                JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                JsonObject p = root.getAsJsonObject().getAsJsonObject("picks");
                if (p != null) {
                    for (Map.Entry<String, JsonElement> e : p.entrySet()) {
                        picks.put(e.getKey(), e.getValue().getAsString());
                    }
                }
            } catch (Exception e) {
                LOG.error("Profile '{}' is corrupt, treating it as empty", name, e);
                picks.clear();
            }
        }
        Map<String, String> kept = new LinkedHashMap<>();
        List<String> dropped = new ArrayList<>();
        for (Map.Entry<String, String> e : picks.entrySet()) {
            if (idx.packs().containsKey(e.getValue())) {
                kept.put(e.getKey(), e.getValue());
            } else {
                dropped.add(e.getKey());
            }
        }
        return new LoadResult(Selection.of(kept), dropped);
    }

    public void save(String name, Selection selection) {
        Path file = file(name);
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonObject p = new JsonObject();
        selection.picks().forEach(p::addProperty);
        root.add("picks", p);
        writeAtomic(file, GSON.toJson(root));
    }

    public void duplicate(String from, String to) {
        Path src = file(from);
        Path dst = file(to);
        try {
            writeAtomic(dst, Files.readString(src, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void delete(String name) {
        try {
            Files.deleteIfExists(file(name));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String active() {
        Path f = dir.resolve("_active.txt");
        try {
            if (Files.isRegularFile(f)) {
                String s = Files.readString(f, StandardCharsets.UTF_8).trim();
                if (!s.isEmpty()) {
                    return s;
                }
            }
        } catch (IOException e) {
            LOG.warn("Could not read active profile", e);
        }
        return DEFAULT;
    }

    public void setActive(String name) {
        checkName(name);
        writeAtomic(dir.resolve("_active.txt"), name);
    }

    private Path file(String name) {
        checkName(name);
        return dir.resolve(name + ".json");
    }

    static void checkName(String name) {
        if (name == null || name.isBlank() || !name.equals(name.strip()) || name.equals(".") || name.equals("..")
                || name.endsWith(".")) {
            throw new IllegalArgumentException("Invalid profile name: '" + name + "'");
        }
        for (char c : name.toCharArray()) {
            if (INVALID_CHARS.indexOf(c) >= 0 || c < 32) {
                throw new IllegalArgumentException("Invalid character in profile name: '" + name + "'");
            }
        }
    }

    private void writeAtomic(Path target, String content) {
        try {
            Files.createDirectories(dir);
            Path tmp = Files.createTempFile(dir, "tmp", ".part");
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
