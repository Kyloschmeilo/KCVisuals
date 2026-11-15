package com.kcvisuals.pack;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileStoreTest {
    @TempDir Path dir;

    static PackIndex index(String... ids) {
        Map<String, PackIndex.PackInfo> m = new java.util.LinkedHashMap<>();
        for (String id : ids) {
            m.put(id, new PackIndex.PackInfo(id, id, Path.of(id), 1, 1, Set.of()));
        }
        return new PackIndex(m);
    }

    @Test
    void saveLoadRoundTrip() {
        ProfileStore s = new ProfileStore(dir);
        s.save("Mine", Selection.of(Map.of("sword", "A.zip", "fire", "B.zip")));
        assertTrue(s.list().contains("Mine"));
        ProfileStore.LoadResult r = s.load("Mine", index("A.zip", "B.zip"));
        assertEquals(Map.of("sword", "A.zip", "fire", "B.zip"), r.selection().picks());
        assertTrue(r.droppedCategories().isEmpty());
    }

    @Test
    void loadDropsMissingPack() {
        ProfileStore s = new ProfileStore(dir);
        s.save("Mine", Selection.of(Map.of("sword", "A.zip", "fire", "Gone.zip")));
        ProfileStore.LoadResult r = s.load("Mine", index("A.zip"));
        assertEquals(Map.of("sword", "A.zip"), r.selection().picks());
        assertEquals(java.util.List.of("fire"), r.droppedCategories());
    }

    @Test
    void invalidNameRejected() {
        ProfileStore s = new ProfileStore(dir);
        for (String bad : new String[] {"a/b", "a\\b", "a:b", "a*b", "a?b", "a\"b", "a<b", "a>b", "a|b", "", " ", "..", "."}) {
            assertThrows(IllegalArgumentException.class, () -> s.save(bad, Selection.empty()), bad);
        }
    }

    @Test
    void activeDefaultsToStandard() {
        ProfileStore s = new ProfileStore(dir);
        assertEquals("Standard", s.active());
        s.setActive("Mine");
        assertEquals("Mine", new ProfileStore(dir).active());
    }

    @Test
    void corruptProfileIsEmpty() throws Exception {
        Files.writeString(dir.resolve("Bad.json"), "{not json");
        ProfileStore.LoadResult r = new ProfileStore(dir).load("Bad", index());
        assertTrue(r.selection().isEmpty());
    }

    @Test
    void duplicateAndDelete() {
        ProfileStore s = new ProfileStore(dir);
        s.save("A", Selection.of(Map.of("sword", "A.zip")));
        s.duplicate("A", "B");
        assertEquals(java.util.List.of("A", "B"), s.list());
        s.delete("A");
        assertEquals(java.util.List.of("B"), s.list());
        assertEquals(Map.of("sword", "A.zip"), s.load("B", index("A.zip")).selection().picks());
    }
}
