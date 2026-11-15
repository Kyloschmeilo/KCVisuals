package com.kcvisuals.pack;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/**
 * Manual check of one real pack: KCV_REAL_PACK = zip or folder path, KCV_REAL_CATEGORY = category id (default
 * totem), optional KCV_REAL_EXPECT = output path that must exist. Builds into a temp dir and prints every file.
 */
@EnabledIfEnvironmentVariable(named = "KCV_REAL_PACK", matches = ".+")
class RealPackClosureTest {
    @Test
    void buildRealPack(@TempDir Path tmp) throws IOException {
        Path pack = Path.of(System.getenv("KCV_REAL_PACK"));
        String category = System.getenv().getOrDefault("KCV_REAL_CATEGORY", "totem");
        Path packs = Files.createDirectory(tmp.resolve("packs"));
        Files.copy(pack, packs.resolve(pack.getFileName().toString()));
        CategoryRegistry reg = CategoryRegistry.loadBundled();
        PackIndex idx = PackScanner.scan(packs, reg);
        Path out = tmp.resolve("generated").resolve("KC Visuals Custom");
        PackBuilder.BuildReport report = PackBuilder.build(
                Selection.of(Map.of(category, pack.getFileName().toString())), idx, reg, out);
        List<String> files;
        try (Stream<Path> walk = Files.walk(out)) {
            files = walk.filter(Files::isRegularFile).map(p -> out.relativize(p).toString().replace('\\', '/'))
                    .sorted().toList();
        }
        System.out.println("[real-pack] " + report);
        files.forEach(f -> System.out.println("[real-pack]   " + f));
        String expect = System.getenv("KCV_REAL_EXPECT");
        if (expect != null) {
            assertTrue(files.contains(expect), expect + " missing from output");
        }
    }
}
