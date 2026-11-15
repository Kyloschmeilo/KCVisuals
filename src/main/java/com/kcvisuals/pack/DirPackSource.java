package com.kcvisuals.pack;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

final class DirPackSource implements PackSource {
    private final String id;
    private final Path root;
    private final Map<String, String> entries;

    DirPackSource(Path root) throws IOException {
        this.root = root;
        this.id = root.getFileName().toString();
        List<String> names = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .forEach(p -> names.add(root.relativize(p).toString().replace('\\', '/')));
        }
        this.entries = PackEntries.normalise(names);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return PackSource.displayNameOf(id);
    }

    @Override
    public Set<String> entries() {
        return Collections.unmodifiableSet(entries.keySet());
    }

    @Override
    public InputStream open(String entryPath) throws IOException {
        String raw = entries.get(entryPath);
        if (raw == null) {
            throw new FileNotFoundException(entryPath + " in " + id);
        }
        return Files.newInputStream(root.resolve(raw));
    }

    @Override
    public void close() {}
}
