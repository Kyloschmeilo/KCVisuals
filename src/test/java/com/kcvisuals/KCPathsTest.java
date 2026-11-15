package com.kcvisuals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KCPathsTest {
    @Test
    void createsAllDirs(@TempDir Path tmp) throws IOException {
        KCPaths p = KCPaths.of(tmp);
        p.ensureDirs();
        assertTrue(Files.isDirectory(tmp.resolve("kcvisuals/packs")));
        assertTrue(Files.isDirectory(tmp.resolve("kcvisuals/generated/KC Visuals Custom")));
        assertTrue(Files.isDirectory(tmp.resolve("kcvisuals/profiles")));
        assertEquals(tmp.resolve("config/kcvisuals.json"), p.config());
        assertEquals(tmp.resolve("kcvisuals/index.json"), p.index());
        assertTrue(!Files.exists(p.index()));
    }
}
