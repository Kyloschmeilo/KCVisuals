package com.kcvisuals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** All on-disk locations used by KC Visuals, relative to the game directory. */
public record KCPaths(Path root, Path config) {
    public static final String CUSTOM_PACK_NAME = "KC Visuals Custom";

    public static KCPaths of(Path gameDir) {
        return new KCPaths(gameDir.resolve("kcvisuals"), gameDir.resolve("config").resolve("kcvisuals.json"));
    }

    public Path packs() {
        return root.resolve("packs");
    }

    public Path generated() {
        return root.resolve("generated");
    }

    public Path generatedPack() {
        return generated().resolve(CUSTOM_PACK_NAME);
    }

    public Path profiles() {
        return root.resolve("profiles");
    }

    public Path index() {
        return root.resolve("index.json");
    }

    public void ensureDirs() throws IOException {
        Files.createDirectories(packs());
        Files.createDirectories(generatedPack());
        Files.createDirectories(profiles());
        Files.createDirectories(config.getParent());
    }
}
