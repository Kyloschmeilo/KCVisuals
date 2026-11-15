package com.kcvisuals.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CategoryRegistry {
    public static final String BUNDLED_RESOURCE = "/assets/kcvisuals/categories.json";

    private final Map<String, Category> byId;
    private final List<Category> all;
    private final String fingerprint;

    private CategoryRegistry(List<Category> categories) {
        Map<String, Category> map = new LinkedHashMap<>();
        for (Category c : categories) {
            if (map.putIfAbsent(c.id(), c) != null) {
                throw new IllegalArgumentException("Duplicate category id: " + c.id());
            }
        }
        this.byId = Collections.unmodifiableMap(map);
        this.all = List.copyOf(map.values());
        this.fingerprint = computeFingerprint(this.all);
    }

    public static CategoryRegistry load(InputStream json) {
        try (Reader r = new InputStreamReader(json, StandardCharsets.UTF_8)) {
            List<Category> list = new ArrayList<>();
            for (JsonElement e : JsonParser.parseReader(r).getAsJsonObject().getAsJsonArray("categories")) {
                JsonObject o = e.getAsJsonObject();
                List<String> paths = new ArrayList<>();
                for (JsonElement p : o.getAsJsonArray("paths")) {
                    paths.add(p.getAsString());
                }
                list.add(new Category(
                        o.get("id").getAsString(),
                        o.get("group").getAsString(),
                        o.get("displayName").getAsString(),
                        o.get("previewPath").getAsString(),
                        paths));
            }
            return new CategoryRegistry(list);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Loads the categories.json shipped inside the mod jar. */
    public static CategoryRegistry loadBundled() {
        try (InputStream in = CategoryRegistry.class.getResourceAsStream(BUNDLED_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + BUNDLED_RESOURCE);
            }
            return load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<Category> all() {
        return all;
    }

    /** Returns the category, or null if the id is unknown. */
    public Category get(String id) {
        return byId.get(id);
    }

    /** Stable hash of the category definitions; used to invalidate the scan cache when they change. */
    public String fingerprint() {
        return fingerprint;
    }

    private static String computeFingerprint(List<Category> categories) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (Category c : categories) {
                md.update(c.id().getBytes(StandardCharsets.UTF_8));
                for (String p : c.paths()) {
                    md.update((byte) 0);
                    md.update(p.getBytes(StandardCharsets.UTF_8));
                }
                md.update((byte) 1);
            }
            return HexFormat.of().formatHex(md.digest(), 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
