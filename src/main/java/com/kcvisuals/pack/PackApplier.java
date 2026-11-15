package com.kcvisuals.pack;

import com.kcvisuals.KCPaths;
import com.kcvisuals.KCVisuals;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;

/** Builds the generated pack off-thread, then enables it on top of the resource pack list and reloads resources. */
public final class PackApplier {
    /** Single worker so concurrent applies never write the output folder at the same time. */
    private static final ExecutorService BUILD_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "KC Visuals pack builder");
        t.setDaemon(true);
        return t;
    });

    static final long RELOAD_TIMEOUT_SECONDS = 60;

    private static volatile CategoryRegistry registry;

    private PackApplier() {}

    public static CategoryRegistry registry() {
        CategoryRegistry r = registry;
        if (r == null) {
            r = CategoryRegistry.loadBundled();
            registry = r;
        }
        return r;
    }

    /** One apply cycle (build, enable, reload) at a time; applies arriving meanwhile coalesce to the newest. */
    private static final ApplyQueue<Selection, PackBuilder.BuildReport> QUEUE = new ApplyQueue<>(PackApplier::runCycle);

    /**
     * Rebuilds {@code KC Visuals Custom} for the selection, enables it on top and reloads resources. While a cycle is
     * running, further calls replace one pending selection, which starts only after the running reload finished.
     * The future completes (on the client thread) once the reload of this call, or of the call that superseded it,
     * is done; it fails if the build failed (the previous pack is then left in place).
     */
    public static CompletableFuture<PackBuilder.BuildReport> apply(Selection selection) {
        return QUEUE.submit(selection);
    }

    private static CompletableFuture<PackBuilder.BuildReport> runCycle(Selection selection) {
        MinecraftClient client = MinecraftClient.getInstance();
        KCPaths paths = KCVisuals.pathsOrDefault();
        return CompletableFuture.supplyAsync(() -> {
                    CategoryRegistry reg = registry();
                    PackIndex index = PackScanner.scan(paths.packs(), reg, paths.index());
                    PackBuilder.BuildReport report = PackBuilder.build(selection, index, reg, paths.generatedPack());
                    KCVisuals.LOGGER.info("Built {}: {} files, missing packs {}, skipped entries {}", KCPaths.CUSTOM_PACK_NAME,
                            report.filesWritten(), report.missingPacks(), report.skippedEntries());
                    return report;
                }, BUILD_EXECUTOR)
                .thenComposeAsync(report -> enableOnTopAndReload(client)
                        // Bounded so a reload future that never completes cannot wedge the ApplyQueue. copy() keeps the
                        // timeout from completing the client's own reload future.
                        .copy()
                        .orTimeout(RELOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .thenApply(v -> report), client)
                .whenComplete((r, e) -> {
                    if (e != null) {
                        KCVisuals.LOGGER.error("Applying {} failed", KCPaths.CUSTOM_PACK_NAME, e);
                    }
                });
    }

    /** Must run on the client thread. Returns the future of the started resource reload. */
    static CompletableFuture<Void> enableOnTopAndReload(MinecraftClient client) {
        ResourcePackManager manager = client.getResourcePackManager();
        manager.scanPacks();
        if (!manager.hasProfile(GeneratedPackProvider.PACK_ID)) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    GeneratedPackProvider.PACK_ID + " is not registered with the resource pack manager"));
        }
        List<String> enabled = new ArrayList<>(manager.getEnabledIds());
        enabled.remove(GeneratedPackProvider.PACK_ID);
        enabled.add(GeneratedPackProvider.PACK_ID); // last = highest priority = top of the list
        manager.setEnabledProfiles(enabled);
        syncOptions(client, manager);
        KCVisuals.LOGGER.info("Enabled {} on top ({}) and reloading resources",
                GeneratedPackProvider.PACK_ID, manager.getEnabledIds());
        return client.reloadResources();
    }

    /**
     * Same as {@code GameOptions#refreshResourcePacks} minus its own reload trigger, so exactly one reload is started
     * and its future is ours to wait on.
     */
    private static void syncOptions(MinecraftClient client, ResourcePackManager manager) {
        GameOptions options = client.options;
        options.resourcePacks.clear();
        options.incompatibleResourcePacks.clear();
        for (ResourcePackProfile profile : manager.getEnabledProfiles()) {
            if (!profile.isPinned()) {
                options.resourcePacks.add(profile.getId());
                if (!profile.getCompatibility().isCompatible()) {
                    options.incompatibleResourcePacks.add(profile.getId());
                }
            }
        }
        options.write();
    }
}
