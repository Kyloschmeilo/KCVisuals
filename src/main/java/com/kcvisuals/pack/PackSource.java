package com.kcvisuals.pack;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/** Read-only view of a resource pack (zip or folder) with entry names normalised to start with {@code assets/}. */
public interface PackSource extends Closeable {
    /** Stable identifier: the file or folder name inside the packs directory. */
    String id();

    /** Human readable name: colour codes and the {@code .zip} extension removed. */
    String displayName();

    /** Normalised file entries (never directories), e.g. {@code assets/minecraft/textures/item/bow.png}. */
    Set<String> entries();

    InputStream open(String entryPath) throws IOException;

    static PackSource open(Path zipOrDir) throws IOException {
        return Files.isDirectory(zipOrDir) ? new DirPackSource(zipOrDir) : new ZipPackSource(zipOrDir);
    }

    /** Strips {@code section-sign + char} colour codes and a trailing {@code .zip}. */
    static String displayNameOf(String fileName) {
        StringBuilder sb = new StringBuilder(fileName.length());
        for (int i = 0; i < fileName.length(); i++) {
            char c = fileName.charAt(i);
            if (c == '§') {
                i++;
            } else {
                sb.append(c);
            }
        }
        String s = sb.toString();
        if (s.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
            s = s.substring(0, s.length() - 4);
        }
        return s.trim();
    }
}
