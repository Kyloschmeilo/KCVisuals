package com.kcvisuals.pack;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackScannerTest {
    static CategoryRegistry reg;

    @BeforeAll
    static void loadRegistry() throws IOException {
        try (InputStream in = PackScannerTest.class.getResourceAsStream("/assets/kcvisuals/categories.json")) {
            reg = CategoryRegistry.load(in);
        }
    }

    static Path zip(Path dir, String name, String... entries) throws IOException {
        Path p = dir.resolve(name);
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(p))) {
            for (String e : entries) {
                z.putNextEntry(new ZipEntry(e));
                z.write(new byte[] {1, 2, 3});
                z.closeEntry();
            }
        }
        return p;
    }

    @Test
    void scanFindsCategories(@TempDir Path dir) throws IOException {
        zip(dir, "a.zip", "pack.mcmeta", "assets/minecraft/textures/item/totem_of_undying.png");
        PackIndex idx = PackScanner.scan(dir, reg);
        assertEquals(1, idx.packs().size());
        assertEquals(Set.of("totem"), idx.packs().get("a.zip").categories());
    }

    @Test
    void nestedFolderZipIsUnwrapped(@TempDir Path dir) throws IOException {
        zip(dir, "a.zip", "MyPack/pack.mcmeta", "MyPack/assets/minecraft/textures/item/totem_of_undying.png");
        PackIndex idx = PackScanner.scan(dir, reg);
        assertEquals(Set.of("totem"), idx.packs().get("a.zip").categories());
        try (PackSource src = PackSource.open(dir.resolve("a.zip"))) {
            assertTrue(src.entries().contains("assets/minecraft/textures/item/totem_of_undying.png"));
            try (InputStream in = src.open("assets/minecraft/textures/item/totem_of_undying.png")) {
                assertEquals(3, in.readAllBytes().length);
            }
        }
    }

    @Test
    void corruptZipIsSkipped(@TempDir Path dir) throws IOException {
        Files.write(dir.resolve("bad.zip"), new byte[] {9, 8, 7, 6, 5, 4, 3, 2, 1, 0, 11, 12});
        zip(dir, "good.zip", "assets/minecraft/textures/item/ender_pearl.png");
        PackIndex idx = assertDoesNotThrow(() -> PackScanner.scan(dir, reg));
        assertNull(idx.packs().get("bad.zip"));
        assertNotNull(idx.packs().get("good.zip"));
    }

    @Test
    void legacyItemsFolderMatches(@TempDir Path dir) throws IOException {
        zip(dir, "old.zip", "assets/minecraft/textures/items/diamond_sword.png",
                "assets/minecraft/textures/items/gold_axe.png");
        PackIndex idx = PackScanner.scan(dir, reg);
        assertTrue(idx.packs().get("old.zip").categories().containsAll(Set.of("swords", "axes")));
    }

    @Test
    void colourCodesStripped(@TempDir Path dir) throws IOException {
        zip(dir, "§5§lAraxy ᴠ₁.₂.zip", "assets/minecraft/textures/item/mace.png");
        PackIndex idx = PackScanner.scan(dir, reg);
        assertEquals(1, idx.packs().size());
        assertEquals("Araxy ᴠ₁.₂", idx.packs().values().iterator().next().displayName());
    }

    @Test
    void indexCacheReused(@TempDir Path dir) throws IOException {
        Path packs = Files.createDirectory(dir.resolve("packs"));
        Path indexFile = dir.resolve("index.json");
        zip(packs, "a.zip", "assets/minecraft/textures/item/mace.png");
        AtomicInteger opens = new AtomicInteger();
        PackScanner.PackOpener counting = p -> {
            opens.incrementAndGet();
            return PackSource.open(p);
        };
        PackIndex first = PackScanner.scan(packs, reg, indexFile, counting);
        assertEquals(1, opens.get());
        assertTrue(Files.isRegularFile(indexFile));

        PackIndex second = PackScanner.scan(packs, reg, indexFile, counting);
        assertEquals(1, opens.get(), "unchanged pack must not be reopened");
        assertEquals(first.packs().get("a.zip"), second.packs().get("a.zip"));
        assertEquals(Set.of("mace"), second.packs().get("a.zip").categories());

        zip(packs, "a.zip", "assets/minecraft/textures/item/mace.png", "assets/minecraft/textures/item/bow.png");
        PackIndex third = PackScanner.scan(packs, reg, indexFile, counting);
        assertEquals(2, opens.get(), "changed pack must be rescanned");
        assertEquals(Set.of("mace", "bow"), third.packs().get("a.zip").categories());
    }

    @Test
    void directoryPackIsScanned(@TempDir Path dir) throws IOException {
        Path pack = dir.resolve("DirPack");
        Path f = pack.resolve("assets/minecraft/textures/block/obsidian.png");
        Files.createDirectories(f.getParent());
        Files.write(f, new byte[] {1});
        PackIndex idx = PackScanner.scan(dir, reg);
        assertEquals(Set.of("obsidian"), idx.packs().get("DirPack").categories());
    }
}
