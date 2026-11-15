package com.kcvisuals.gui.packs;

import com.kcvisuals.KCPaths;
import com.kcvisuals.KCVisuals;
import com.kcvisuals.gui.Toasts;
import com.kcvisuals.pack.CategoryRegistry;
import com.kcvisuals.pack.PackApplier;
import com.kcvisuals.pack.PackBuilder;
import com.kcvisuals.pack.PackIndex;
import com.kcvisuals.pack.PackScanner;
import com.kcvisuals.pack.ProfileStore;
import com.kcvisuals.pack.Selection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.MinecraftClient;

/**
 * State shared by the Packs and Profiles tabs for one opening of the menu: the pack index (scanned off-thread), the
 * in-memory selection, the active profile and the preview textures. All public methods are render-thread only.
 */
public final class PackSession {
    /** Background thread for pack scans and profile file writes. */
    static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "KC Visuals menu IO");
        t.setDaemon(true);
        return t;
    });

    private final KCPaths paths;
    private final CategoryRegistry registry;
    private final ProfileStore store;
    private final PreviewCache previews;

    private volatile PackIndex index;
    private boolean loading;
    private boolean loadedOnce;
    private String loadError;
    private String activeProfile;
    private Selection selection = Selection.empty();
    private Selection saved = Selection.empty();
    private boolean applying;
    /** The stored profile still names packs that are gone; it stays "dirty" until the user applies without them. */
    private boolean savedHasDropped;
    /** Sequence of the newest apply, so a superseded apply cannot mark an older selection as saved. */
    private int applySeq;
    /** The first scan failed, so the profile was never loaded; applying now would overwrite it with nothing. */
    private boolean selectionUnknown;
    private int revision;
    private boolean closed;

    public PackSession(KCPaths paths) {
        this.paths = paths;
        this.registry = PackApplier.registry();
        this.store = new ProfileStore(paths.profiles());
        this.previews = new PreviewCache(() -> index);
    }

    public CategoryRegistry registry() {
        return registry;
    }

    public ProfileStore store() {
        return store;
    }

    public PreviewCache previews() {
        return previews;
    }

    /** Null until the first scan finished. */
    public PackIndex index() {
        return index;
    }

    public boolean isLoading() {
        return loading;
    }

    public String loadError() {
        return loadError;
    }

    public boolean isApplying() {
        return applying;
    }

    /** Bumped whenever the index, the selection or the profiles change, so views can refresh cached data. */
    public int revision() {
        return revision;
    }

    public String activeProfile() {
        return activeProfile;
    }

    public Selection selection() {
        return selection;
    }

    public boolean isDirty() {
        return savedHasDropped || !selection.equals(saved);
    }

    public void select(String categoryId, String packId) {
        selection = selection.with(categoryId, packId);
        revision++;
    }

    /** All categories back to Vanilla (in memory; needs "Anwenden" to take effect). */
    public void resetSelection() {
        selection = Selection.empty();
        revision++;
    }

    /** Starts the first scan if none ran yet. */
    public void ensureLoaded() {
        if (!loadedOnce && !loading) {
            rescan();
        }
    }

    /** Re-reads the packs folder in the background. The first scan also loads the active profile's selection. */
    public void rescan() {
        if (loading) {
            return;
        }
        loading = true;
        loadError = null;
        boolean first = !loadedOnce || selectionUnknown;
        MinecraftClient mc = MinecraftClient.getInstance();
        CompletableFuture.supplyAsync(() -> {
            PackIndex idx = PackScanner.scan(paths.packs(), registry, paths.index());
            String active = store.active();
            ProfileStore.LoadResult r = first ? store.load(active, idx) : null;
            return new Object[] {idx, active, r};
        }, IO).whenCompleteAsync((res, err) -> {
            loading = false;
            loadedOnce = true;
            if (closed) {
                return;
            }
            if (err != null) {
                KCVisuals.LOGGER.error("Scanning packs failed", err);
                loadError = "Packs konnten nicht eingelesen werden";
                if (activeProfile == null) {
                    activeProfile = store.active();
                }
                selectionUnknown = selectionUnknown || first;
                Toasts.show(Toasts.APPLY, "Packs konnten nicht eingelesen werden", "Details im Log");
                if (index == null) {
                    index = new PackIndex(java.util.Map.of());
                }
                revision++;
                return;
            }
            index = (PackIndex) res[0];
            selectionUnknown = false;
            previews.evictAll();
            if (first) {
                activeProfile = (String) res[1];
                ProfileStore.LoadResult r = (ProfileStore.LoadResult) res[2];
                selection = r.selection();
                saved = selection;
                if (!r.droppedCategories().isEmpty()) {
                    // Keep the stored picks on disk; the session is dirty until the user applies without them.
                    savedHasDropped = true;
                    Toasts.multiline(Toasts.DROPPED, "Fehlende Packs",
                            "Pack fehlt für: " + joinCategories(r.droppedCategories()) + ". Erst „Anwenden“ entfernt sie aus dem Profil.");
                }
            } else {
                // Keep the in-memory picks whose packs still exist.
                Selection kept = selection;
                for (String cat : new ArrayList<>(selection.picks().keySet())) {
                    if (!index.packs().containsKey(selection.packFor(cat))) {
                        kept = kept.without(cat);
                    }
                }
                selection = kept;
                Toasts.show(Toasts.INFO, "Packs neu eingelesen", index.packs().size() + " Packs gefunden");
            }
            revision++;
        }, mc);
    }

    /** Saves the selection to the active profile and builds/applies the custom pack. */
    public void apply() {
        if (index == null || activeProfile == null) {
            return;
        }
        if (selectionUnknown) {
            Toasts.show(Toasts.APPLY, "Anwenden nicht möglich", "Packs zuerst neu einlesen");
            return;
        }
        Selection sel = selection;
        String profile = activeProfile;
        int seq = ++applySeq;
        revision++;
        applying = true;
        Toasts.show(Toasts.APPLY, "Pack wird angewendet…", "Profil: " + profile);
        CompletableFuture.runAsync(() -> store.save(profile, sel), IO)
                .thenCompose(v -> PackApplier.apply(sel))
                .whenCompleteAsync((report, err) -> finishApply(seq, sel, report, err), MinecraftClient.getInstance());
    }

    /**
     * Makes {@code name} the active profile, loads it and applies it. Categories whose pack no longer exists are
     * reported in a warning toast.
     */
    public void activate(String name) {
        if (index == null) {
            return;
        }
        PackIndex idx = index;
        int seq = ++applySeq;
        applying = true;
        Toasts.show(Toasts.APPLY, "Pack wird angewendet…", "Profil: " + name);
        CompletableFuture.supplyAsync(() -> {
            store.setActive(name);
            return store.load(name, idx);
        }, IO).thenComposeAsync(r -> {
            activeProfile = name;
            selection = r.selection();
            savedHasDropped = false;
            revision++;
            if (!r.droppedCategories().isEmpty()) {
                Toasts.multiline(Toasts.DROPPED, "Kategorien entfernt",
                        "Pack fehlt für: " + joinCategories(r.droppedCategories()));
            }
            Selection sel = r.selection();
            return PackApplier.apply(sel).thenApply(rep -> new Object[] {sel, rep});
        }, MinecraftClient.getInstance()).whenCompleteAsync((o, err) -> finishApply(seq,
                o == null ? null : (Selection) o[0], o == null ? null : (PackBuilder.BuildReport) o[1], err),
                MinecraftClient.getInstance());
    }

    /** Called after profile files were renamed/deleted elsewhere so the active name stays in sync. */
    public void setActiveProfileName(String name) {
        activeProfile = name;
        revision++;
    }

    public void touch() {
        revision++;
    }

    private void finishApply(int seq, Selection sel, PackBuilder.BuildReport report, Throwable err) {
        if (seq == applySeq) {
            applying = false;
        }
        revision++;
        if (err != null) {
            Throwable cause = err.getCause() != null ? err.getCause() : err;
            KCVisuals.LOGGER.error("Apply from menu failed", err);
            Toasts.show(Toasts.APPLY, "Anwenden fehlgeschlagen", String.valueOf(cause.getMessage()));
            return;
        }
        if (seq == applySeq) {
            saved = sel;
            savedHasDropped = false;
        }
        Toasts.show(Toasts.APPLY, "Fertig (" + report.filesWritten() + (report.filesWritten() == 1 ? " Datei)" : " Dateien)"), "Profil: " + activeProfile);
        if (!report.missingPacks().isEmpty()) {
            List<String> names = report.missingPacks().stream().map(PackNames::clean).toList();
            Toasts.multiline(Toasts.MISSING, "Fehlende Packs", String.join(", ", names));
        }
        if (!report.skippedEntries().isEmpty()) {
            Toasts.show(Toasts.SKIPPED, report.skippedEntries().size() + (report.skippedEntries().size() == 1 ? " Datei übersprungen" : " Dateien übersprungen"),
                    "Unlesbare Einträge, siehe Log");
        }
    }

    private String joinCategories(List<String> ids) {
        List<String> names = new ArrayList<>();
        for (String id : ids) {
            var c = registry.get(id);
            names.add(c == null ? id : c.displayName());
        }
        return String.join(", ", names);
    }

    /** Releases preview textures; called when the menu closes. */
    public void close() {
        closed = true;
        previews.evictAll();
    }
}
