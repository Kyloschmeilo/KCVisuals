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
 *
 * <p>Files (and pack.mcmeta) are written to {@code <outDir>/../.kc-staging} first and swapped in at the end. An
 * unreadable source entry is skipped and listed in {@link BuildReport#skippedEntries()}; a pack that cannot be opened
 * at all is listed in {@link BuildReport#missingPacks()}. Write or swap errors throw
 * {@link java.io.UncheckedIOException} and leave the previous pack in place.
 */
public final class PackBuilder {
    private static final System.Logger LOG = System.getLogger("kcvisuals");

    /** Resource pack format of Minecraft 1.21.11 ({@code version.json} → {@code pack_version.resource_major}). */
    public static final int PACK_FORMAT = 75;
    public static final String DESCRIPTION = "KC Visuals Custom";
    /** Largest single entry that is read (zip-bomb guard); bigger entries are skipped and reported. */
    static final int MAX_ENTRY_BYTES = 32 * 1024 * 1024;

    private static final Pattern MODEL_JSON = Pattern.compile("assets/[^/]+/models/.+\\.json");

    private PackBuilder() {}

    /**
     * @param skippedEntries {@code packId:entry} of source entries that could not be read (corrupt zip entry, locked
     *     file) and were left out; the rest of the pack is still used
     */
    public record BuildReport(int filesWritten, List<String> missingPacks, List<String> skippedEntries) {
        public BuildReport {
            missingPacks = List.copyOf(missingPacks);
            skippedEntries = List.copyOf(skippedEntries);
        }

        public BuildReport(int filesWritten, List<String> missingPacks) {
            this(filesWritten, missingPacks, List.of());
        }
    }

    /** Writes one staged file; injectable so tests can simulate a failing disk. */
    @FunctionalInterface
    interface EntryWriter {
        EntryWriter FILES = (target, data) -> {
            Files.createDirectories(target.getParent());
            Files.write(target, data);
        };

        void write(Path target, byte[] data) throws IOException;
    }

    /** {@code outPath} is the full path in the generated pack, e.g. {@code assets/minecraft/textures/item/x.png}. */
    private record Planned(String packId, String rawEntry, String outPath, boolean legacy) {}

    public static final String STAGING_DIR = ".kc-staging";
    public static final String TRASH_DIR = ".kc-old";

    public static BuildReport build(Selection sel, PackIndex index, CategoryRegistry reg, Path outDir) {
        return build(sel, index, reg, outDir, PackSource::open, EntryWriter.FILES);
    }

    static BuildReport build(Selection sel, PackIndex index, CategoryRegistry reg, Path outDir, PackScanner.PackOpener opener,
            EntryWriter writer) {
        Path parent = outDir.toAbsolutePath().getParent();
        Path staging = parent.resolve(STAGING_DIR);
        Path trash = parent.resolve(TRASH_DIR);
        List<String> missing = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Map<String, PackSource> open = new HashMap<>();
        int written = 0;
        try {
            Files.createDirectories(outDir);
            recoverInterruptedSwap(outDir.resolve("assets"), trash);
            deleteRecursively(staging);
            Map<String, Planned> plan = plan(sel, index, reg, opener, open, missing, skipped);
            for (Planned p : plan.values()) {
                if (copy(open.get(p.packId()), p, staging, writer, skipped)) {
                    written++;
                }
            }
            closeAll(open);
            writeMcmeta(staging);
            swapIn(staging, outDir, trash);
        } catch (IOException e) {
            throw new UncheckedIOException("Building " + outDir.getFileName() + " failed: " + e.getMessage(), e);
        } finally {
            closeAll(open);
            try {
                deleteRecursively(staging);
            } catch (IOException e) {
                LOG.log(System.Logger.Level.WARNING, "Could not clean " + staging + ": " + e);
            }
        }
        return new BuildReport(written, missing, skipped);
    }

    /**
     * A crash between "rename live assets aside" and "move staged assets in" leaves only {@code .kc-old}: put it
     * back. If live assets exist, a leftover {@code .kc-old} is stale and is discarded.
     */
    private static void recoverInterruptedSwap(Path assets, Path trash) throws IOException {
        if (!Files.exists(trash)) {
            return;
        }
        if (Files.exists(assets)) {
            deleteRecursively(trash);
        } else {
            LOG.log(System.Logger.Level.WARNING, "Restoring " + assets + " from interrupted rebuild");
            Files.move(trash, assets);
        }
    }

    /**
     * Explicit category files first (registry order, later category wins), then each category's dependency closure
     * from the same pack ({@link ModelClosure}). A closure file never replaces an explicitly selected file; among
     * closure files the first category in registry order wins.
     */
    private static Map<String, Planned> plan(Selection sel, PackIndex index, CategoryRegistry reg,
            PackScanner.PackOpener opener, Map<String, PackSource> open, List<String> missing, List<String> skipped) {
        Map<String, Planned> explicit = new LinkedHashMap<>();
        Map<String, Planned> closure = new LinkedHashMap<>();
        Map<String, Map<String, String>> outputIndexes = new HashMap<>();
        for (Category cat : reg.all()) {
            String packId = sel.packFor(cat.id());
            if (packId == null) {
                continue;
            }
            PackSource src = openPack(packId, index, opener, open, missing);
            if (src == null) {
                continue;
            }
            Map<String, Planned> forCategory = new LinkedHashMap<>();
            for (String entry : new TreeSet<>(src.entries())) {
                PackPath path = PackPath.of(entry);
                if (path == null || !path.in(cat)) {
                    continue;
                }
                String out = PackPath.MC_ASSETS + path.modern();
                if (!PackPath.isSafeOutput(out)) {
                    LOG.log(System.Logger.Level.WARNING, "Skipping unsafe or invalid path " + entry + " in " + packId);
                    skipped.add(packId + ":" + entry);
                    continue;
                }
                Planned prev = forCategory.get(out);
                if (prev != null && !prev.legacy() && path.isLegacy()) {
                    continue;
                }
                forCategory.put(out, new Planned(packId, entry, out, path.isLegacy()));
            }
            for (Map.Entry<String, Planned> e : forCategory.entrySet()) {
                explicit.remove(e.getKey());
                explicit.put(e.getKey(), e.getValue());
            }
            Map<String, String> outIndex =
                    outputIndexes.computeIfAbsent(packId, id -> ModelClosure.outputIndex(src.entries()));
            for (Map.Entry<String, String> dep : ModelClosure.closure(src, outIndex, forCategory.keySet()).entrySet()) {
                if (PackPath.isSafeOutput(dep.getKey())) {
                    closure.putIfAbsent(dep.getKey(),
                            new Planned(packId, dep.getValue(), dep.getKey(), PackPath.isLegacyEntry(dep.getValue())));
                }
            }
        }
        Map<String, Planned> plan = new LinkedHashMap<>();
        closure.forEach((out, p) -> {
            if (!explicit.containsKey(out)) {
                plan.put(out, p);
            }
        });
        plan.putAll(explicit);
        return plan;
    }

    /**
     * Replaces {@code outDir/assets} and {@code outDir/pack.mcmeta} with the staged ones. The live folder is renamed
     * aside first; if that rename fails (e.g. a file is locked on Windows) the build fails and the live pack is left
     * exactly as it was. If moving the staged folder in fails (move, then copy), the old folder is put back.
     */
    private static void swapIn(Path staging, Path outDir, Path trash) throws IOException {
        Path assets = outDir.resolve("assets");
        Path stagedAssets = staging.resolve("assets");
        if (Files.exists(assets)) {
            try {
                Files.move(assets, trash);
            } catch (IOException renameFailed) {
                throw new IOException("cannot move the current pack aside (" + renameFailed
                        + "); the current pack was left unchanged", renameFailed);
            }
        }
        if (Files.exists(stagedAssets)) {
            try {
                moveOrCopy(stagedAssets, assets);
            } catch (IOException installFailed) {
                try {
                    deleteRecursively(assets);
                    if (Files.exists(trash)) {
                        Files.move(trash, assets);
                    }
                } catch (IOException restoreFailed) {
                    installFailed.addSuppressed(restoreFailed);
                    throw new IOException("cannot install the new pack and restoring the old one failed; it is kept in "
                            + trash + " and will be restored on the next build", installFailed);
                }
                throw new IOException("cannot install the new pack; the previous pack was restored", installFailed);
            }
        }
        try {
            Files.move(staging.resolve("pack.mcmeta"), outDir.resolve("pack.mcmeta"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not replace pack.mcmeta (keeping the old one): " + e);
        }
        try {
            deleteRecursively(trash);
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not delete old assets " + trash + ": " + e);
        }
    }

    private static void moveOrCopy(Path from, Path to) throws IOException {
        try {
            Files.move(from, to);
        } catch (IOException moveFailed) {
            copyRecursively(from, to);
        }
    }

    private static void closeAll(Map<String, PackSource> open) {
        for (PackSource src : open.values()) {
            try {
                src.close();
            } catch (IOException ignored) {
                // read-only source; nothing to recover
            }
        }
        open.clear();
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

    private static PackSource openPack(String packId, PackIndex index, PackScanner.PackOpener opener, Map<String, PackSource> open, List<String> missing) {
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
            PackSource src = opener.open(info.file());
            open.put(packId, src);
            return src;
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Cannot open pack " + packId + ": " + e);
            missing.add(packId);
            return null;
        }
    }

    /**
     * Returns false if the entry was left out. Read errors of the source (corrupt zip entry, locked file) skip just
     * that entry and are recorded in {@code skipped}; write errors into staging fail the whole build.
     */
    private static boolean copy(PackSource src, Planned p, Path staging, EntryWriter writer, List<String> skipped)
            throws IOException {
        Path root = staging.toAbsolutePath().normalize();
        Path target = root.resolve(p.outPath()).normalize();
        if (!PackPath.isSafeOutput(p.outPath()) || !target.startsWith(root) || target.equals(root)) {
            LOG.log(System.Logger.Level.WARNING, "Refusing to write " + p.outPath() + " outside the pack (" + p.packId() + ")");
            skipped.add(p.packId() + ":" + p.rawEntry());
            return false;
        }
        byte[] data;
        try (InputStream in = src.open(p.rawEntry())) {
            data = readLimited(in);
            if (data == null) {
                LOG.log(System.Logger.Level.WARNING, "Skipping " + p.rawEntry() + " in " + p.packId() + ": larger than "
                        + MAX_ENTRY_BYTES + " bytes");
                skipped.add(p.packId() + ":" + p.rawEntry());
                return false;
            }
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Skipping unreadable " + p.rawEntry() + " in " + p.packId() + ": " + e);
            skipped.add(p.packId() + ":" + p.rawEntry());
            return false;
        }
        if (MODEL_JSON.matcher(p.outPath()).matches()) {
            data = rewriteModel(data);
            if (data == null) {
                LOG.log(System.Logger.Level.WARNING, "Skipping unparseable model " + p.rawEntry() + " in " + p.packId());
                return false;
            }
        }
        writer.write(target, data);
        return true;
    }

    /** Reads at most {@link #MAX_ENTRY_BYTES}; returns null if the stream is longer. */
    static byte[] readLimited(InputStream in) throws IOException {
        byte[] data = in.readNBytes(MAX_ENTRY_BYTES + 1);
        return data.length > MAX_ENTRY_BYTES ? null : data;
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
            String updated = rewriteRef(old, "models/", ".json");
            if (!updated.equals(old)) {
                root.addProperty("parent", updated);
                changed = true;
            }
        }
        return changed ? root.toString().getBytes(StandardCharsets.UTF_8) : data;
    }

    static String rewriteTextureRef(String ref) {
        return rewriteRef(ref, "textures/", ".png");
    }

    /**
     * Lowercases a resource id (output files are lowercase) and, in the minecraft namespace, maps legacy names the same
     * way {@link LegacyPaths} maps the files, e.g. {@code items/gold_sword} to {@code item/golden_sword}.
     */
    static String rewriteRef(String ref, String dir, String ext) {
        if (ref.startsWith("#")) {
            return ref;
        }
        int colon = ref.indexOf(':');
        String ns = colon < 0 ? null : ref.substring(0, colon).toLowerCase(Locale.ROOT);
        String path = ref.substring(colon + 1).toLowerCase(Locale.ROOT);
        if (ns != null && !ns.equals("minecraft")) {
            return ns + ":" + path;
        }
        String mapped = LegacyPaths.normalise(dir + path + ext);
        if (mapped.startsWith(dir) && mapped.endsWith(ext)) {
            path = mapped.substring(dir.length(), mapped.length() - ext.length());
        }
        return ns == null ? path : "minecraft:" + path;
    }

    private static void copyRecursively(Path from, Path to) throws IOException {
        try (java.util.stream.Stream<Path> walk = Files.walk(from)) {
            for (Path src : (Iterable<Path>) walk::iterator) {
                Path dst = to.resolve(from.relativize(src).toString()).normalize();
                if (!dst.startsWith(to.normalize())) {
                    LOG.log(System.Logger.Level.WARNING, "Refusing to copy " + src + " outside " + to);
                    continue;
                }
                if (Files.isDirectory(src)) {
                    Files.createDirectories(dst);
                } else {
                    Files.copy(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
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
