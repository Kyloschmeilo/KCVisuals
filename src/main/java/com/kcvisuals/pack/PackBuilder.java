package com.kcvisuals.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Composes the generated pack: for every selected category, the matching files of the chosen pack are copied under
 * their normalised modern path ({@link PackPath}).
 *
 * <p>Overwrite order is deterministic: categories are applied in registry order, so when two selected categories
 * produce the same output path, the one later in {@code categories.json} wins. Inside one category a modern entry
 * beats a legacy entry mapping to the same path; otherwise entries are applied in sorted raw-name order.
 */
public final class PackBuilder {
    private static final System.Logger LOG = System.getLogger("kcvisuals");

    /** Resource pack format of Minecraft 1.21.11 ({@code version.json} → {@code pack_version.resource_major}). */
    public static final int PACK_FORMAT = 75;
    public static final String DESCRIPTION = "KC Visuals Custom";

    /** Characters Minecraft accepts in an identifier path; anything else would be rejected when loading. */
    private static final Pattern VALID_PATH = Pattern.compile("[a-z0-9/._-]+");

    private PackBuilder() {}

    public record BuildReport(int filesWritten, List<String> missingPacks) {
        public BuildReport {
            missingPacks = List.copyOf(missingPacks);
        }
    }

    private record Planned(String packId, String rawEntry, PackPath path) {}

    public static BuildReport build(Selection sel, PackIndex index, CategoryRegistry reg, Path outDir) {
        try {
            Files.createDirectories(outDir);
            deleteRecursively(outDir.resolve("assets"));
            writeMcmeta(outDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<String> missing = new ArrayList<>();
        Map<String, PackSource> open = new HashMap<>();
        try {
            Map<String, Planned> plan = new LinkedHashMap<>();
            for (Category cat : reg.all()) {
                String packId = sel.packFor(cat.id());
                if (packId == null) {
                    continue;
                }
                PackSource src = openPack(packId, index, open, missing);
                if (src == null) {
                    continue;
                }
                Map<String, Planned> forCategory = new LinkedHashMap<>();
                for (String entry : new TreeSet<>(src.entries())) {
                    PackPath path = PackPath.of(entry);
                    if (path == null || !path.in(cat) || !VALID_PATH.matcher(path.modern()).matches()) {
                        continue;
                    }
                    Planned prev = forCategory.get(path.modern());
                    if (prev != null && !prev.path().isLegacy() && path.isLegacy()) {
                        continue;
                    }
                    forCategory.put(path.modern(), new Planned(packId, entry, path));
                }
                for (Map.Entry<String, Planned> e : forCategory.entrySet()) {
                    plan.remove(e.getKey());
                    plan.put(e.getKey(), e.getValue());
                }
            }

            Path assets = outDir.resolve(PackPath.MC_ASSETS);
            int written = 0;
            for (Planned p : plan.values()) {
                if (copy(open.get(p.packId()), p, assets.resolve(p.path().modern()))) {
                    written++;
                }
            }
            return new BuildReport(written, missing);
        } finally {
            for (PackSource src : open.values()) {
                try {
                    src.close();
                } catch (IOException ignored) {
                    // read-only source; nothing to recover
                }
            }
        }
    }

    /** Writes a pack.mcmeta valid for 1.21.11 (formats from 65 on are declared via min_format/max_format). */
    public static void writeMcmeta(Path outDir) throws IOException {
        JsonObject pack = new JsonObject();
        pack.addProperty("description", DESCRIPTION);
        pack.addProperty("pack_format", PACK_FORMAT);
        pack.addProperty("min_format", PACK_FORMAT);
        pack.addProperty("max_format", PACK_FORMAT);
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        Files.createDirectories(outDir);
        Files.writeString(outDir.resolve("pack.mcmeta"), root.toString(), StandardCharsets.UTF_8);
    }

    private static PackSource openPack(String packId, PackIndex index, Map<String, PackSource> open, List<String> missing) {
        if (open.containsKey(packId)) {
            return open.get(packId);
        }
        if (missing.contains(packId)) {
            return null;
        }
        PackIndex.PackInfo info = index.packs().get(packId);
        if (info == null || !Files.exists(info.file())) {
            missing.add(packId);
            return null;
        }
        try {
            PackSource src = PackSource.open(info.file());
            open.put(packId, src);
            return src;
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Cannot open pack " + packId + ": " + e);
            missing.add(packId);
            return null;
        }
    }

    private static boolean copy(PackSource src, Planned p, Path target) {
        try (InputStream in = src.open(p.rawEntry())) {
            byte[] data = in.readAllBytes();
            if (p.path().modern().startsWith("models/") && p.path().modern().endsWith(".json")) {
                data = rewriteModel(data);
                if (data == null) {
                    LOG.log(System.Logger.Level.WARNING, "Skipping unparseable model " + p.rawEntry() + " in " + p.packId());
                    return false;
                }
            }
            Files.createDirectories(target.getParent());
            Files.write(target, data);
            return true;
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Cannot copy " + p.rawEntry() + " from " + p.packId() + ": " + e);
            return false;
        }
    }

    /**
     * Maps texture references of a block/item model through the same normalisation as file paths, so a legacy
     * {@code items/diamond_sword} becomes {@code item/diamond_sword} (and uppercase ids are lowercased, matching the
     * lowercased output files). Returns the original bytes when nothing changes, or null if the json is unusable.
     */
    static byte[] rewriteModel(byte[] data) {
        JsonObject root;
        try {
            JsonElement el = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
            if (!el.isJsonObject()) {
                return null;
            }
            root = el.getAsJsonObject();
        } catch (RuntimeException e) {
            return null;
        }
        boolean changed = false;
        JsonElement textures = root.get("textures");
        if (textures != null && textures.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : textures.getAsJsonObject().entrySet()) {
                JsonElement v = e.getValue();
                if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) {
                    String old = v.getAsString();
                    String updated = rewriteTextureRef(old);
                    if (!updated.equals(old)) {
                        e.setValue(new JsonPrimitive(updated));
                        changed = true;
                    }
                }
            }
        }
        JsonElement parent = root.get("parent");
        if (parent != null && parent.isJsonPrimitive() && parent.getAsJsonPrimitive().isString()) {
            String old = parent.getAsString();
            String lower = old.toLowerCase(Locale.ROOT);
            if (!lower.equals(old)) {
                root.addProperty("parent", lower);
                changed = true;
            }
        }
        return changed ? root.toString().getBytes(StandardCharsets.UTF_8) : data;
    }

    static String rewriteTextureRef(String ref) {
        if (ref.startsWith("#")) {
            return ref;
        }
        int colon = ref.indexOf(':');
        String ns = colon < 0 ? null : ref.substring(0, colon);
        if (ns != null && !ns.equalsIgnoreCase("minecraft")) {
            return ref;
        }
        String path = ref.substring(colon + 1).toLowerCase(Locale.ROOT);
        String mapped = LegacyPaths.normalise("textures/" + path + ".png");
        if (mapped.startsWith("textures/") && mapped.endsWith(".png")) {
            path = mapped.substring("textures/".length(), mapped.length() - ".png".length());
        }
        return ns == null ? path : "minecraft:" + path;
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
