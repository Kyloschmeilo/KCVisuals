package com.kcvisuals.pack;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackStampTest {
    @TempDir Path dir;

    private static PackIndex index(long size, long mtime) {
        Map<String, PackIndex.PackInfo> m = new LinkedHashMap<>();
        m.put("A.zip", new PackIndex.PackInfo("A.zip", "A", Path.of("packs", "A.zip"), size, mtime, Set.of()));
        m.put("B.zip", new PackIndex.PackInfo("B.zip", "B", Path.of("packs", "B.zip"), 5, 5, Set.of()));
        return new PackIndex(m);
    }

    private static Selection sel(String sword) {
        return Selection.of(Map.of("sword", sword, "fire", "B.zip"));
    }

    @Test
    void stableRegardlessOfPickOrder() {
        Map<String, String> a = new LinkedHashMap<>();
        a.put("sword", "A.zip");
        a.put("fire", "B.zip");
        Map<String, String> b = new LinkedHashMap<>();
        b.put("fire", "B.zip");
        b.put("sword", "A.zip");
        assertEquals(PackStamp.compute(Selection.of(a), index(1, 1), "1.0"),
                PackStamp.compute(Selection.of(b), index(1, 1), "1.0"));
    }

    @Test
    void changesWithSelectionPackFileOrSalt() {
        String base = PackStamp.compute(sel("A.zip"), index(1, 1), "1.0");
        assertNotEquals(base, PackStamp.compute(sel("B.zip"), index(1, 1), "1.0"));
        assertNotEquals(base, PackStamp.compute(sel("A.zip"), index(2, 1), "1.0"));
        assertNotEquals(base, PackStamp.compute(sel("A.zip"), index(1, 2), "1.0"));
        assertNotEquals(base, PackStamp.compute(sel("A.zip"), index(1, 1), "1.1"));
    }

    @Test
    void writeAndMatch() throws Exception {
        Path pack = dir.resolve("gen");
        String stamp = PackStamp.compute(sel("A.zip"), index(1, 1), "1.0");
        assertFalse(PackStamp.matches(pack, stamp));
        PackStamp.write(pack, stamp);
        assertTrue(PackStamp.matches(pack, stamp));
        assertFalse(PackStamp.matches(pack, PackStamp.compute(sel("A.zip"), index(9, 1), "1.0")));
        Files.writeString(pack.resolve(PackStamp.FILE), "garbage");
        assertFalse(PackStamp.matches(pack, stamp));
    }

    @Test
    void stampSurvivesRebuild() throws Exception {
        // the builder only replaces assets/ and pack.mcmeta in the output folder
        Path out = dir.resolve("generated").resolve("KC Visuals Custom");
        PackStamp.write(out, "abc");
        PackBuilder.build(Selection.empty(), new PackIndex(Map.of()), CategoryRegistry.loadBundled(), out);
        assertTrue(PackStamp.matches(out, "abc"));
    }
}
