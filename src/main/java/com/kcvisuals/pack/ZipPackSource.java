package com.kcvisuals.pack;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class ZipPackSource implements PackSource {
    private final String id;
    private final ZipFile zip;
    private final Map<String, String> entries;

    ZipPackSource(Path file) throws IOException {
        this.id = file.getFileName().toString();
        this.zip = openZip(file);
        try {
            List<String> names = new ArrayList<>();
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (!e.isDirectory()) {
                    names.add(e.getName());
                }
            }
            this.entries = PackEntries.normalise(names);
        } catch (RuntimeException | Error e) {
            zip.close();
            throw e;
        }
    }

    /** UTF-8 first, falling back to CP437 for old zips with non-UTF-8 names. */
    private static ZipFile openZip(Path file) throws IOException {
        try {
            return new ZipFile(file.toFile(), StandardCharsets.UTF_8);
        } catch (IOException | IllegalArgumentException utf8Failure) {
            try {
                return new ZipFile(file.toFile(), Charset.forName("IBM437"));
            } catch (IOException | IllegalArgumentException cp437Failure) {
                throw new IOException("Not a readable zip: " + file.getFileName(), utf8Failure);
            }
        }
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
        ZipEntry e = raw == null ? null : zip.getEntry(raw);
        if (e == null) {
            throw new FileNotFoundException(entryPath + " in " + id);
        }
        return zip.getInputStream(e);
    }

    @Override
    public void close() throws IOException {
        zip.close();
    }
}
