package com.kcvisuals.pack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Manual check against real packs. Run with KCV_PACK_DIRS (path-separator list of directories) and
 * KCV_COVERAGE_OUT (report file) set; skipped otherwise.
 */
@EnabledIfEnvironmentVariable(named = "KCV_PACK_DIRS", matches = ".+")
class RealPackCoverageTest {
    @Test
    void printCoverage() throws IOException {
        CategoryRegistry reg = CategoryRegistry.loadBundled();
        Map<String, Integer> perCategory = new TreeMap<>();
        reg.all().forEach(c -> perCategory.put(c.id(), 0));
        List<String> none = new ArrayList<>();
        int total = 0;
        StringBuilder sb = new StringBuilder();
        for (String dir : System.getenv("KCV_PACK_DIRS").split(java.io.File.pathSeparator)) {
            PackIndex idx = PackScanner.scan(Path.of(dir), reg);
            total += idx.packs().size();
            for (PackIndex.PackInfo info : idx.packs().values()) {
                info.categories().forEach(c -> perCategory.merge(c, 1, Integer::sum));
                if (info.categories().isEmpty()) {
                    none.add(dir + "/" + info.id());
                }
                sb.append(info.id()).append(" -> ").append(new java.util.TreeSet<>(info.categories())).append('\n');
            }
        }
        sb.append("\nTOTAL packs: ").append(total).append('\n');
        perCategory.forEach((k, v) -> sb.append(k).append(": ").append(v).append('\n'));
        sb.append("NO CATEGORY (").append(none.size()).append("):\n");
        none.forEach(n -> sb.append("  ").append(n).append('\n'));
        String out = System.getenv("KCV_COVERAGE_OUT");
        if (out != null) {
            Files.writeString(Path.of(out), sb.toString());
        } else {
            System.out.println(sb);
        }
    }
}
