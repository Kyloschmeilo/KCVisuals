package com.kcvisuals.pack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Fingerprint of what the generated pack was built from (the selection plus file name, size and mtime of every
 * selected pack). Stored as {@code .kc-stamp} next to the pack's {@code assets}, which the builder's swap never touches,
 * so an unchanged profile need not be rebuilt (and resources reloaded) on every launch.
 */
public final class PackStamp {
    public static final String FILE = ".kc-stamp";

    private PackStamp() {}

    /** @param salt anything else that changes the build output, e.g. the mod version */
    public static String compute(Selection sel, PackIndex index, String salt) {
        StringBuilder sb = new StringBuilder("kcv-stamp-1|").append(salt).append('|').append(PackBuilder.PACK_FORMAT).append('\n');
        for (Map.Entry<String, String> e : new TreeMap<>(sel.picks()).entrySet()) {
            sb.append("pick|").append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        for (String id : new TreeSet<>(sel.picks().values())) {
            PackIndex.PackInfo info = index.packs().get(id);
            if (info == null) {
                sb.append("pack|").append(id).append("|missing\n");
            } else {
                Path name = info.file() == null ? null : info.file().getFileName();
                sb.append("pack|").append(id).append('|').append(name).append('|').append(info.size()).append('|')
                        .append(info.mtime()).append('\n');
            }
        }
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void write(Path packDir, String stamp) throws IOException {
        Files.createDirectories(packDir);
        Files.writeString(packDir.resolve(FILE), stamp, StandardCharsets.UTF_8);
    }

    /** True when the stored stamp equals {@code stamp}; a missing or unreadable file never matches. */
    public static boolean matches(Path packDir, String stamp) {
        try {
            return stamp.equals(Files.readString(packDir.resolve(FILE), StandardCharsets.UTF_8).trim());
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
