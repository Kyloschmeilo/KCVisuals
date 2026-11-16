package com.kcvisuals.gui.packs;

import com.kcvisuals.KCVisuals;
import com.kcvisuals.pack.Category;
import com.kcvisuals.pack.ModelClosure;
import com.kcvisuals.pack.PackIndex;
import com.kcvisuals.pack.PackSource;
import com.kcvisuals.preview.ArgbImage;
import com.kcvisuals.preview.Scene;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
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
 * Preview textures for (pack, category, view) triples. Reading, PNG decoding and model rendering happen on one
 * background thread; only the texture upload runs on the render thread. Item categories show the pack's inventory
 * model (3D render or flat icon, see {@link ModelPreviews}) and fall back to the category's 2D texture; the hovered
 * tile additionally gets a strip of {@link Scene#FRAMES} turn frames. Everything is released by {@link #evictAll()}
 * when the menu closes.
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
    /** Hover strips: a second worker, so a strip never queues behind the grid's initial loads. */
    private static final ExecutorService HOVER_WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "KC Visuals hover preview");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicInteger NEXT_ID = new AtomicInteger();

    /** What a preview shows: the category's item (or texture), or the placed end crystal. */
    public enum View { ITEM, PLACED }

    /** {@code model} is true for a 3D render (square, {@link ModelPreviews#SIZE} px, has a hover strip). */
    public record Preview(Identifier id, int width, int height, boolean model) {}

    /** Hover animation: {@code frames} square frames of {@code size} px stacked vertically in one texture. */
    public record Strip(Identifier id, int size, int frames) {}

    private enum State { LOADING, READY, NONE, MODEL_3D }

    /** Raw preview bytes plus what is needed to judge them. {@code source} is null for Vanilla. */
    private record Raw(byte[] bytes, PackSource source, String entry) {}

    private static final class Entry {
        volatile State state = State.LOADING;
        volatile Preview preview;
    }

    /** Strips of the hovered tile (one per view); only one tile has strips at a time. Render thread only. */
    private static final class Hover {
        final String tileKey;
        final Map<View, Strip> strips = new EnumMap<>(View.class);
        final Map<View, Boolean> requested = new EnumMap<>(View.class);

        Hover(String tileKey) {
            this.tileKey = tileKey;
        }
    }

    private final Supplier<PackIndex> index;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    /** Render-thread only: textures registered by this cache (strips included). */
    private final List<Identifier> registered = new ArrayList<>();
    /** Worker-thread only: recently opened packs, closed when evicted, with their output-path index. */
    private final LinkedHashMap<String, PackSource> sources = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<String, Map<String, String>> sourceIndex = new java.util.HashMap<>();
    private volatile int generation;
    /** Written on the render thread, read by the worker to drop stale strip jobs. */
    private volatile Hover hover;

    public PreviewCache(Supplier<PackIndex> index) {
        this.index = index;
    }

    private static String key(String packId, Category c, View view) {
        return packId + '\u0000' + c.id() + '\u0000' + view;
    }

    /** The preview texture, or null while it is loading or if the pack has no image for the category. */
    public Identifier get(String packId, Category c) {
        Preview p = preview(packId, c);
        return p == null ? null : p.id();
    }

    /** The item preview with its size, for aspect-correct drawing. Starts loading on first request. */
    public Preview preview(String packId, Category c) {
        return preview(packId, c, View.ITEM);
    }

    /** Like {@link #preview(String, Category)} for the given view. */
    public Preview preview(String packId, Category c, View view) {
        String key = key(packId, c, view);
        Entry e = entries.get(key);
        if (e == null) {
            Entry created = new Entry();
            e = entries.putIfAbsent(key, created);
            if (e == null) {
                e = created;
                int gen = generation;
                Entry target = created;
                WORKER.execute(() -> load(gen, target, packId, c, view));
            }
        }
        return e.state == State.READY ? e.preview : null;
    }

    /**
     * True if the pack's texture for this category is a UV atlas of a custom 3D item model that could not be
     * rendered; callers then show the Vanilla icon with a "3D" badge instead.
     */
    public boolean isModel3d(String packId, Category c) {
        Entry e = entries.get(key(packId, c, View.ITEM));
        return e != null && e.state == State.MODEL_3D;
    }

    /** True while the preview is still being read; false when ready or known to be missing. */
    public boolean isLoading(String packId, Category c) {
        return isLoading(packId, c, View.ITEM);
    }

    public boolean isLoading(String packId, Category c, View view) {
        Entry e = entries.get(key(packId, c, view));
        return e == null || e.state == State.LOADING;
    }

    /**
     * The turn animation for a hovered tile's 3D preview, or null until it is rendered (or if the view has no 3D
     * preview). Asking for another tile releases the previous tile's strips. Render thread.
     */
    public Strip hoverStrip(String packId, Category c, View view) {
        String tileKey = packId + '\u0000' + c.id();
        if (hover == null || !hover.tileKey.equals(tileKey)) {
            clearHover();
            hover = new Hover(tileKey);
        }
        Strip s = hover.strips.get(view);
        if (s != null || hover.requested.containsKey(view)) {
            return s;
        }
        Entry e = entries.get(key(packId, c, view));
        if (e == null || e.state != State.READY || e.preview == null || !e.preview.model()) {
            return null;
        }
        hover.requested.put(view, Boolean.TRUE);
        Hover target = hover;
        int gen = generation;
        PackIndex idx = index.get();
        PackIndex.PackInfo info = VANILLA.equals(packId) || idx == null ? null : idx.packs().get(packId);
        // Own thread and own zip handle: the strip does not wait behind the grid's initial loads, and no scene
        // (quads + textures) has to be kept per tile. The model is resolved again for the hovered tile only.
        HOVER_WORKER.execute(() -> {
            if (gen != generation || target != hover) {
                return;
            }
            NativeImage img;
            try (PackSource src = info == null ? null : PackSource.open(info.file())) {
                if (src == null && !VANILLA.equals(packId)) {
                    return;
                }
                ModelPreviews.Result r = ModelPreviews.compute(src, src == null ? null : ModelClosure.outputIndex(src.entries()), c, view);
                if (r == null || r.scene() == null || gen != generation || target != hover) {
                    return;
                }
                img = toNative(r.scene().strip(ModelPreviews.SIZE));
            } catch (IOException | RuntimeException ex) {
                KCVisuals.LOGGER.debug("Hover strip failed for {} / {}: {}", packId, c.id(), ex.toString());
                return;
            }
            MinecraftClient.getInstance().execute(() -> {
                if (gen != generation || target != hover) {
                    img.close();
                    return;
                }
                Identifier id = register(img);
                target.strips.put(view, new Strip(id, ModelPreviews.SIZE, Scene.FRAMES));
            });
        });
        return null;
    }

    /** Releases the hovered tile's strips (no tile hovered). Render thread. */
    public void clearHover() {
        if (hover == null) {
            return;
        }
        var tm = MinecraftClient.getInstance().getTextureManager();
        for (Strip s : hover.strips.values()) {
            tm.destroyTexture(s.id());
            registered.remove(s.id());
        }
        hover = null;
    }

    private void load(int gen, Entry entry, String packId, Category c, View view) {
        if (gen != generation) {
            return;
        }
        NativeImage img = null;
        try {
            ModelPreviews.Result model = modelPreview(packId, c, view);
            if (model != null) {
                NativeImage rendered = toNative(model.image());
                boolean is3d = model.scene() != null;
                MinecraftClient.getInstance().execute(() -> upload(gen, entry, rendered, is3d));
                return;
            }
            if (view == View.PLACED) {
                entry.state = State.NONE;
                return;
            }
            Raw raw = readRaw(packId, c);
            if (raw != null) {
                long pixels = PreviewPick.pngPixels(raw.bytes());
                if (pixels > PreviewPick.MAX_PIXELS) {
                    KCVisuals.LOGGER.warn("Preview {} in {} declares {} pixels, skipped", raw.entry(), packId, pixels);
                    entry.state = State.NONE;
                    return;
                }
                NativeImage decoded = NativeImage.read(raw.bytes());
                boolean atlas;
                try {
                    int[] f = PreviewPick.firstFrame(decoded.getWidth(), decoded.getHeight());
                    atlas = raw.source() != null && PreviewPick.isModelAtlas(raw.source().entries(), raw.entry(), f[2], f[3],
                            name -> readText(raw.source(), name));
                } catch (RuntimeException e) {
                    decoded.close();
                    throw e;
                }
                if (atlas) {
                    decoded.close();
                    entry.state = State.MODEL_3D;
                    return;
                }
                img = prepare(decoded);
            }
        } catch (IOException | RuntimeException ex) {
            KCVisuals.LOGGER.debug("No preview for {} / {}: {}", packId, c.id(), ex.toString());
        }
        if (img == null) {
            entry.state = State.NONE;
            return;
        }
        NativeImage finalImg = img;
        MinecraftClient.getInstance().execute(() -> upload(gen, entry, finalImg, false));
    }

    /** Model-based preview, or null to use the 2D texture preview. Worker thread. */
    private ModelPreviews.Result modelPreview(String packId, Category c, View view) throws IOException {
        if (view == View.ITEM && PreviewPick.previewItem(c) == null) {
            return null;
        }
        PackSource src = null;
        Map<String, String> outIndex = null;
        if (!VANILLA.equals(packId)) {
            src = source(packId);
            if (src == null) {
                return null;
            }
            PackSource s = src;
            outIndex = sourceIndex.computeIfAbsent(packId, id -> ModelClosure.outputIndex(s.entries()));
        }
        try {
            return ModelPreviews.compute(src, outIndex, c, view);
        } catch (RuntimeException e) {
            KCVisuals.LOGGER.debug("Model preview failed for {} / {}: {}", packId, c.id(), e.toString());
            return null;
        }
    }

    private void upload(int gen, Entry entry, NativeImage img, boolean model) {
        if (gen != generation) {
            img.close();
            return;
        }
        int w = img.getWidth(), h = img.getHeight();
        Identifier id = register(img);
        entry.preview = new Preview(id, w, h, model);
        entry.state = State.READY;
    }

    /** Uploads {@code img} as a new texture owned by this cache. Render thread. */
    private Identifier register(NativeImage img) {
        Identifier id = Identifier.of(KCVisuals.MOD_ID, "preview/" + NEXT_ID.incrementAndGet());
        NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "KC Visuals preview " + id, img);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
        registered.add(id);
        return id;
    }

    private static NativeImage toNative(ArgbImage src) {
        NativeImage out = new NativeImage(src.width(), src.height(), false);
        try {
            for (int y = 0; y < src.height(); y++) {
                for (int x = 0; x < src.width(); x++) {
                    out.setColorArgb(x, y, src.get(x, y));
                }
            }
            return out;
        } catch (RuntimeException | Error e) {
            out.close();
            throw e;
        }
    }

    /** Small text entry (item model JSON) of a pack, or null if unreadable. Worker thread. */
    private static String readText(PackSource src, String entry) {
        try (InputStream s = src.open(entry)) {
            byte[] b = PreviewPick.readCapped(s, PreviewPick.MAX_JSON_BYTES);
            return b == null ? null : new String(b, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private Raw readRaw(String packId, Category c) throws IOException {
        if (VANILLA.equals(packId)) {
            InputSupplier<InputStream> in = MinecraftClient.getInstance().getDefaultResourcePack()
                    .open(ResourceType.CLIENT_RESOURCES, Identifier.ofVanilla(c.previewPath()));
            if (in == null) {
                return null;
            }
            try (InputStream s = in.get()) {
                byte[] b = PreviewPick.readCapped(s, PreviewPick.MAX_IMAGE_BYTES);
                return b == null ? null : new Raw(b, null, null);
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
            byte[] b = PreviewPick.readCapped(s, PreviewPick.MAX_IMAGE_BYTES);
            if (b == null) {
                KCVisuals.LOGGER.warn("Preview {} in {} is larger than {} bytes, skipped", entry, packId, PreviewPick.MAX_IMAGE_BYTES);
                return null;
            }
            return new Raw(b, src, entry);
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
            var eldest = it.next();
            closeQuietly(eldest.getValue());
            sourceIndex.remove(eldest.getKey());
            it.remove();
        }
        return src;
    }

    /**
     * First animation frame, shrunk with nearest-neighbour sampling if larger than {@link #MAX_SIZE}. Takes ownership
     * of {@code src}: it is closed whenever a different image is returned or an exception is thrown.
     */
    static NativeImage prepare(NativeImage src) {
        NativeImage out = null;
        try {
            int[] f = PreviewPick.firstFrame(src.getWidth(), src.getHeight());
            int[] size = PreviewPick.fitWithin(f[2], f[3], MAX_SIZE);
            if (f[2] == src.getWidth() && f[3] == src.getHeight() && size[0] == f[2] && size[1] == f[3]) {
                return src;
            }
            out = new NativeImage(size[0], size[1], false);
            for (int y = 0; y < size[1]; y++) {
                int sy = f[1] + (int) ((long) y * f[3] / size[1]);
                for (int x = 0; x < size[0]; x++) {
                    int sx = f[0] + (int) ((long) x * f[2] / size[0]);
                    out.setColorArgb(x, y, src.getColorArgb(sx, sy));
                }
            }
            src.close();
            return out;
        } catch (RuntimeException | Error e) {
            if (out != null) {
                out.close();
            }
            src.close();
            throw e;
        }
    }

    /** Drops every preview and closes the textures and open packs. Must be called on the render thread. */
    public void evictAll() {
        generation++;
        entries.clear();
        hover = null;
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
            sourceIndex.clear();
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
