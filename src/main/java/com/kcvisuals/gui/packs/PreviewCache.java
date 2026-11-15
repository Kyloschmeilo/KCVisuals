package com.kcvisuals.gui.packs;

import com.kcvisuals.KCVisuals;
import com.kcvisuals.pack.Category;
import com.kcvisuals.pack.PackIndex;
import com.kcvisuals.pack.PackSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

/**
 * Preview textures for (pack, category) pairs. Reading and PNG decoding happen on one background thread; only the
 * texture upload runs on the render thread. Everything is released by {@link #evictAll()} when the menu closes.
 */
public final class PreviewCache {
    /** Pack id used for the Vanilla tile (reads from the game's default resource pack). */
    public static final String VANILLA = "";
    static final int MAX_SIZE = 128;
    private static final int OPEN_SOURCES = 16;

    /** One worker: keeps zip access serial (sources are cached between requests) and decoding off the render thread. */
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "KC Visuals previews");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private static final AtomicInteger NEXT_ID = new AtomicInteger();

    public record Preview(Identifier id, int width, int height) {}

    private enum State { LOADING, READY, NONE }

    private static final class Entry {
        volatile State state = State.LOADING;
        volatile Preview preview;
    }

    private final Supplier<PackIndex> index;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    /** Render-thread only: textures registered by this cache. */
    private final List<Identifier> registered = new ArrayList<>();
    /** Worker-thread only: recently opened packs, closed when evicted. */
    private final LinkedHashMap<String, PackSource> sources = new LinkedHashMap<>(16, 0.75f, true);
    private volatile int generation;

    public PreviewCache(Supplier<PackIndex> index) {
        this.index = index;
    }

    /** The preview texture, or null while it is loading or if the pack has no image for the category. */
    public Identifier get(String packId, Category c) {
        Preview p = preview(packId, c);
        return p == null ? null : p.id();
    }

    /** Like {@link #get} but with the texture size, for aspect-correct drawing. Starts loading on first request. */
    public Preview preview(String packId, Category c) {
        String key = packId + '\u0000' + c.id();
        Entry e = entries.get(key);
        if (e == null) {
            Entry created = new Entry();
            e = entries.putIfAbsent(key, created);
            if (e == null) {
                e = created;
                int gen = generation;
                Entry target = created;
                WORKER.execute(() -> load(gen, target, packId, c));
            }
        }
        return e.state == State.READY ? e.preview : null;
    }

    /** True while the preview is still being read; false when ready or known to be missing. */
    public boolean isLoading(String packId, Category c) {
        Entry e = entries.get(packId + '\u0000' + c.id());
        return e == null || e.state == State.LOADING;
    }

    private void load(int gen, Entry entry, String packId, Category c) {
        if (gen != generation) {
            return;
        }
        NativeImage img = null;
        try {
            byte[] bytes = readBytes(packId, c);
            if (bytes != null) {
                img = prepare(NativeImage.read(bytes));
            }
        } catch (IOException | RuntimeException ex) {
            KCVisuals.LOGGER.debug("No preview for {} / {}: {}", packId, c.id(), ex.toString());
        }
        if (img == null) {
            entry.state = State.NONE;
            return;
        }
        NativeImage finalImg = img;
        MinecraftClient.getInstance().execute(() -> upload(gen, entry, finalImg));
    }

    private void upload(int gen, Entry entry, NativeImage img) {
        if (gen != generation) {
            img.close();
            return;
        }
        Identifier id = Identifier.of(KCVisuals.MOD_ID, "preview/" + NEXT_ID.incrementAndGet());
        int w = img.getWidth(), h = img.getHeight();
        NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "KC Visuals preview " + id, img);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
        registered.add(id);
        entry.preview = new Preview(id, w, h);
        entry.state = State.READY;
    }

    private byte[] readBytes(String packId, Category c) throws IOException {
        if (VANILLA.equals(packId)) {
            InputSupplier<InputStream> in = MinecraftClient.getInstance().getDefaultResourcePack()
                    .open(ResourceType.CLIENT_RESOURCES, Identifier.ofVanilla(c.previewPath()));
            if (in == null) {
                return null;
            }
            try (InputStream s = in.get()) {
                return s.readAllBytes();
            }
        }
        PackSource src = source(packId);
        if (src == null) {
            return null;
        }
        String entry = PreviewPick.find(src.entries(), c);
        if (entry == null) {
            return null;
        }
        try (InputStream s = src.open(entry)) {
            return s.readAllBytes();
        }
    }

    private PackSource source(String packId) throws IOException {
        PackSource src = sources.get(packId);
        if (src != null) {
            return src;
        }
        PackIndex idx = index.get();
        PackIndex.PackInfo info = idx == null ? null : idx.packs().get(packId);
        if (info == null) {
            return null;
        }
        src = PackSource.open(info.file());
        sources.put(packId, src);
        while (sources.size() > OPEN_SOURCES) {
            var it = sources.entrySet().iterator();
            closeQuietly(it.next().getValue());
            it.remove();
        }
        return src;
    }

    /** First animation frame, shrunk with nearest-neighbour sampling if larger than {@link #MAX_SIZE}. */
    static NativeImage prepare(NativeImage src) {
        int[] f = PreviewPick.firstFrame(src.getWidth(), src.getHeight());
        int[] size = PreviewPick.fitWithin(f[2], f[3], MAX_SIZE);
        if (f[2] == src.getWidth() && f[3] == src.getHeight() && size[0] == f[2] && size[1] == f[3]) {
            return src;
        }
        NativeImage out = new NativeImage(size[0], size[1], false);
        for (int y = 0; y < size[1]; y++) {
            int sy = f[1] + (int) ((long) y * f[3] / size[1]);
            for (int x = 0; x < size[0]; x++) {
                int sx = f[0] + (int) ((long) x * f[2] / size[0]);
                out.setColorArgb(x, y, src.getColorArgb(sx, sy));
            }
        }
        src.close();
        return out;
    }

    /** Drops every preview and closes the textures and open packs. Must be called on the render thread. */
    public void evictAll() {
        generation++;
        entries.clear();
        var tm = MinecraftClient.getInstance().getTextureManager();
        for (Identifier id : registered) {
            tm.destroyTexture(id);
        }
        registered.clear();
        WORKER.execute(() -> {
            for (PackSource s : sources.values()) {
                closeQuietly(s);
            }
            sources.clear();
        });
    }

    private static void closeQuietly(PackSource s) {
        try {
            s.close();
        } catch (IOException ignored) {
            // read-only source; nothing to recover
        }
    }
}
