package com.kcvisuals.pack;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackBuilderTest {
    static CategoryRegistry reg;

    static final String MC = "assets/minecraft/";
    static final String TOTEM = "textures/item/totem_of_undying.png";
    static final String SWORD = "textures/item/diamond_sword.png";

    @BeforeAll
    static void loadRegistry() throws IOException {
        try (InputStream in = PackBuilderTest.class.getResourceAsStream("/assets/kcvisuals/categories.json")) {
            reg = CategoryRegistry.load(in);
        }
    }

    /** Writes a zip; each entry's content is its own name unless given explicitly via {@code contents}. */
    static Path zip(Path dir, String name, Map<String, String> contents, String... entries) throws IOException {
        Files.createDirectories(dir);
        Path p = dir.resolve(name);
        Map<String, String> all = new LinkedHashMap<>();
        for (String e : entries) {
            all.put(e, e);
        }
        all.putAll(contents);
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(p))) {
            for (Map.Entry<String, String> e : all.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return p;
    }

    static Path zip(Path dir, String name, String... entries) throws IOException {
        return zip(dir, name, Map.of(), entries);
    }

    static Path out(Path dir, String rel) {
        return dir.resolve("out").resolve(MC + rel);
    }

    static PackBuilder.BuildReport build(Path dir, Selection sel) {
        return build(dir, sel, reg);
    }

    static PackBuilder.BuildReport build(Path dir, Selection sel, CategoryRegistry r) {
        PackIndex idx = PackScanner.scan(dir.resolve("packs"), r);
        return PackBuilder.build(sel, idx, r, dir.resolve("out"));
    }

    @Test
    void buildCopiesOnlySelectedCategory(@TempDir Path dir) throws IOException {
        zip(dir.resolve("packs"), "A.zip", "pack.mcmeta", MC + TOTEM, MC + SWORD);
        PackBuilder.BuildReport report = build(dir, Selection.of(Map.of("totem", "A.zip")));
        assertTrue(Files.isRegularFile(out(dir, TOTEM)));
        assertEquals(MC + TOTEM, Files.readString(out(dir, TOTEM)));
        assertFalse(Files.exists(out(dir, SWORD)));
        assertTrue(Files.isRegularFile(dir.resolve("out/pack.mcmeta")));
        assertEquals(1, report.filesWritten());
        assertEquals(List.of(), report.missingPacks());
    }

    @Test
    void mcmetaUses1_21_11Format(@TempDir Path dir) throws IOException {
        build(dir, Selection.empty());
        JsonObject pack = JsonParser.parseString(Files.readString(dir.resolve("out/pack.mcmeta")))
                .getAsJsonObject().getAsJsonObject("pack");
        assertEquals("KC Visuals Custom", pack.get("description").getAsString());
        assertEquals(75, pack.get("pack_format").getAsInt());
        assertEquals(75, pack.get("min_format").getAsInt());
        assertEquals(75, pack.get("max_format").getAsInt());
    }

    @Test
    void rebuildRemovesDeselected(@TempDir Path dir) throws IOException {
        zip(dir.resolve("packs"), "A.zip", MC + TOTEM);
        build(dir, Selection.of(Map.of("totem", "A.zip")));
        assertTrue(Files.exists(out(dir, TOTEM)));
        PackBuilder.BuildReport second = build(dir, Selection.empty());
        assertFalse(Files.exists(out(dir, TOTEM)));
        assertEquals(0, second.filesWritten());
        assertTrue(Files.isRegularFile(dir.resolve("out/pack.mcmeta")));
    }

    @Test
    void legacyPathWrittenModern(@TempDir Path dir) throws IOException {
        zip(dir.resolve("packs"), "old.zip", MC + "textures/items/totem_of_undying.png", MC + "textures/items/totem.png");
        build(dir, Selection.of(Map.of("totem", "old.zip")));
        assertTrue(Files.isRegularFile(out(dir, TOTEM)));
        assertFalse(Files.exists(out(dir, "textures/items/totem_of_undying.png")));
    }

    @Test
    void modernEntryBeatsLegacyEntryInSamePack(@TempDir Path dir) throws IOException {
        zip(dir.resolve("packs"), "mixed.zip", MC + TOTEM, MC + "textures/items/totem.png");
        build(dir, Selection.of(Map.of("totem", "mixed.zip")));
        assertEquals(MC + TOTEM, Files.readString(out(dir, TOTEM)));
    }

    @Test
    void missingPackReported(@TempDir Path dir) throws IOException {
        zip(dir.resolve("packs"), "A.zip", MC + TOTEM);
        PackBuilder.BuildReport report = assertDoesNotThrow(
                () -> build(dir, Selection.of(Map.of("totem", "x", "swords", "A.zip"))));
        assertEquals(List.of("x"), report.missingPacks());
        assertEquals(0, report.filesWritten());
    }

    @Test
    void builderCopiesUppercaseEntry(@TempDir Path dir) throws IOException {
        zip(dir.resolve("packs"), "caps.zip", MC + "textures/item/Totem_Of_Undying.PNG");
        PackBuilder.BuildReport report = build(dir, Selection.of(Map.of("totem", "caps.zip")));
        assertEquals(1, report.filesWritten());
        assertTrue(Files.isRegularFile(out(dir, TOTEM)));
        try (var list = Files.list(out(dir, TOTEM).getParent())) {
            assertEquals(List.of("totem_of_undying.png"), list.map(p -> p.getFileName().toString()).toList(),
                    "file name on disk must be lowercase");
        }
    }

    @Test
    void uppercaseAssetsPrefixMatches(@TempDir Path dir) throws IOException {
        zip(dir.resolve("packs"), "caps.zip", "Assets/Minecraft/textures/item/totem_of_undying.png");
        PackIndex idx = PackScanner.scan(dir.resolve("packs"), reg);
        assertEquals(Set.of("totem"), idx.packs().get("caps.zip").categories());
        PackBuilder.build(Selection.of(Map.of("totem", "caps.zip")), idx, reg, dir.resolve("out"));
        assertEquals("Assets/Minecraft/textures/item/totem_of_undying.png", Files.readString(out(dir, TOTEM)));
    }

    @Test
    void legacyModelTextureRefsRewritten(@TempDir Path dir) throws IOException {
        String model = "{\"parent\":\"item/handheld\",\"textures\":{\"layer0\":\"items/Diamond_Sword\","
                + "\"layer1\":\"minecraft:items/gold_sword\",\"layer2\":\"#layer0\",\"layer3\":\"other:items/x\"}}";
        zip(dir.resolve("packs"), "old.zip", Map.of(MC + "models/item/diamond_sword.json", model));
        build(dir, Selection.of(Map.of("swords", "old.zip")));
        JsonObject tex = JsonParser.parseString(Files.readString(out(dir, "models/item/diamond_sword.json")))
                .getAsJsonObject().getAsJsonObject("textures");
        assertEquals("item/diamond_sword", tex.get("layer0").getAsString());
        assertEquals("minecraft:item/golden_sword", tex.get("layer1").getAsString());
        assertEquals("#layer0", tex.get("layer2").getAsString());
        assertEquals("other:items/x", tex.get("layer3").getAsString());
    }

    @Test
    void modernModelCopiedByteForByte(@TempDir Path dir) throws IOException {
        String model = "{ \"parent\": \"item/handheld\",\n  \"textures\": { \"layer0\": \"item/diamond_sword\" } }";
        zip(dir.resolve("packs"), "new.zip", Map.of(MC + "models/item/diamond_sword.json", model));
        build(dir, Selection.of(Map.of("swords", "new.zip")));
        assertEquals(model, Files.readString(out(dir, "models/item/diamond_sword.json")));
    }

    @Test
    void unparseableModelSkipped(@TempDir Path dir) throws IOException {
        zip(dir.resolve("packs"), "bad.zip", Map.of(MC + "models/item/diamond_sword.json", "{not json"),
                MC + SWORD);
        PackBuilder.BuildReport report = build(dir, Selection.of(Map.of("swords", "bad.zip")));
        assertFalse(Files.exists(out(dir, "models/item/diamond_sword.json")));
        assertTrue(Files.exists(out(dir, SWORD)));
        assertEquals(1, report.filesWritten());
    }

    @Test
    void overlappingCategoriesLaterInRegistryWins(@TempDir Path dir) throws IOException {
        String json = "{\"categories\":["
                + "{\"id\":\"first\",\"group\":\"g\",\"displayName\":\"F\",\"previewPath\":\"p\",\"paths\":[\"textures/item/shared.png\"]},"
                + "{\"id\":\"second\",\"group\":\"g\",\"displayName\":\"S\",\"previewPath\":\"p\",\"paths\":[\"textures/item/shared.png\"]}]}";
        CategoryRegistry r = CategoryRegistry.load(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        zip(dir.resolve("packs"), "A.zip", Map.of(MC + "textures/item/shared.png", "A"));
        zip(dir.resolve("packs"), "B.zip", Map.of(MC + "textures/item/shared.png", "B"));
        // Selection map order must not matter: registry order decides.
        Map<String, String> sel = new LinkedHashMap<>();
        sel.put("second", "B.zip");
        sel.put("first", "A.zip");
        build(dir, Selection.of(sel), r);
        assertEquals("B", Files.readString(out(dir, "textures/item/shared.png")));
    }

    @Test
    void folderPackCopied(@TempDir Path dir) throws IOException {
        Path f = dir.resolve("packs/DirPack/" + MC + TOTEM);
        Files.createDirectories(f.getParent());
        Files.writeString(f, "dir");
        build(dir, Selection.of(Map.of("totem", "DirPack")));
        assertEquals("dir", Files.readString(out(dir, TOTEM)));
    }

    /** Wraps a source so that opening the given entry fails, as a corrupt zip entry would. */
    static PackSource failingOn(PackSource real, String badEntry) {
        return new PackSource() {
            public String id() { return real.id(); }
            public String displayName() { return real.displayName(); }
            public Set<String> entries() { return real.entries(); }
            public InputStream open(String entryPath) throws IOException {
                if (entryPath.equals(badEntry)) {
                    throw new java.util.zip.ZipException("invalid entry CRC (simulated)");
                }
                return real.open(entryPath);
            }
            public void close() throws IOException { real.close(); }
        };
    }

    static final PackBuilder.EntryWriter FAILING_WRITER = (target, data) -> {
        throw new IOException("No space left on device (simulated)");
    };

    @Test
    void corruptEntryIsSkippedOthersCopied(@TempDir Path dir) throws IOException {
        zip(dir.resolve("packs"), "A.zip", MC + TOTEM, MC + SWORD, MC + "textures/item/iron_sword.png");
        PackIndex idx = PackScanner.scan(dir.resolve("packs"), reg);
        PackScanner.PackOpener opener = p -> failingOn(PackSource.open(p), MC + SWORD);
        PackBuilder.BuildReport report = PackBuilder.build(Selection.of(Map.of("totem", "A.zip", "swords", "A.zip")),
                idx, reg, dir.resolve("out"), opener, PackBuilder.EntryWriter.FILES);
        assertEquals(2, report.filesWritten());
        assertEquals(List.of("A.zip:" + MC + SWORD), report.skippedEntries());
        assertTrue(Files.exists(out(dir, TOTEM)));
        assertTrue(Files.exists(out(dir, "textures/item/iron_sword.png")));
        assertFalse(Files.exists(out(dir, SWORD)));
    }

    @Test
    void stagingWriteFailureKeepsOldPack(@TempDir Path dir) throws IOException {
        zip(dir.resolve("packs"), "A.zip", MC + TOTEM);
        zip(dir.resolve("packs"), "B.zip", MC + SWORD);
        build(dir, Selection.of(Map.of("totem", "A.zip")));
        assertEquals(MC + TOTEM, Files.readString(out(dir, TOTEM)));

        PackIndex idx = PackScanner.scan(dir.resolve("packs"), reg);
        assertThrows(java.io.UncheckedIOException.class, () -> PackBuilder.build(Selection.of(Map.of("swords", "B.zip")),
                idx, reg, dir.resolve("out"), PackSource::open, FAILING_WRITER));

        assertEquals(MC + TOTEM, Files.readString(out(dir, TOTEM)), "previous pack must survive a failed build");
        assertFalse(Files.exists(out(dir, SWORD)));
        assertTrue(Files.isRegularFile(dir.resolve("out/pack.mcmeta")));
        assertFalse(Files.exists(dir.resolve(PackBuilder.STAGING_DIR)), "staging must be cleaned up");
    }

    @Test
    void restoresKcOldWhenAssetsMissing(@TempDir Path dir) throws IOException {
        // Simulates a crash between "rename live assets aside" and "move staged assets in".
        Path kcOld = dir.resolve(PackBuilder.TRASH_DIR).resolve("minecraft/" + TOTEM);
        Files.createDirectories(kcOld.getParent());
        Files.writeString(kcOld, "old");
        Files.createDirectories(dir.resolve("out"));
        zip(dir.resolve("packs"), "B.zip", MC + SWORD);
        PackIndex idx = PackScanner.scan(dir.resolve("packs"), reg);

        assertThrows(java.io.UncheckedIOException.class, () -> PackBuilder.build(Selection.of(Map.of("swords", "B.zip")),
                idx, reg, dir.resolve("out"), PackSource::open, FAILING_WRITER));

        assertEquals("old", Files.readString(out(dir, TOTEM)), ".kc-old must be restored, not deleted");
        assertFalse(Files.exists(dir.resolve(PackBuilder.TRASH_DIR)));
    }

    @Test
    void staleKcOldNextToLiveAssetsIsDiscarded(@TempDir Path dir) throws IOException {
        zip(dir.resolve("packs"), "A.zip", MC + TOTEM);
        build(dir, Selection.of(Map.of("totem", "A.zip")));
        Path stale = dir.resolve(PackBuilder.TRASH_DIR).resolve("minecraft/textures/item/stale.png");
        Files.createDirectories(stale.getParent());
        Files.writeString(stale, "stale");
        build(dir, Selection.of(Map.of("totem", "A.zip")));
        assertTrue(Files.exists(out(dir, TOTEM)));
        assertFalse(Files.exists(out(dir, "textures/item/stale.png")));
        assertFalse(Files.exists(dir.resolve(PackBuilder.TRASH_DIR)));
    }
    @Test
    void successfulBuildLeavesNoStagingBehind(@TempDir Path dir) throws IOException {
        zip(dir.resolve("packs"), "A.zip", MC + TOTEM);
        build(dir, Selection.of(Map.of("totem", "A.zip")));
        build(dir, Selection.of(Map.of("totem", "A.zip")));
        assertTrue(Files.exists(out(dir, TOTEM)));
        assertFalse(Files.exists(dir.resolve(PackBuilder.STAGING_DIR)));
        assertFalse(Files.exists(dir.resolve(PackBuilder.TRASH_DIR)));
    }
}
