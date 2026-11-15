package com.kcvisuals.pack;

import com.kcvisuals.KCPaths;
import com.kcvisuals.KCVisuals;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;

/** Builds the generated pack off-thread, then enables it on top of the resource pack list and reloads resources. */
public final class PackApplier {
    /** Single worker so concurrent applies never write the output folder at the same time. */
    private static final ExecutorService BUILD_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "KC Visuals pack builder");
        t.setDaemon(true);
        return t;
    });

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

    /**
     * Rebuilds {@code KC Visuals Custom} for the selection. The returned future completes on the client thread after
     * the pack has been enabled and the resource reload has been started.
     */
    public static CompletableFuture<PackBuilder.BuildReport> apply(Selection selection) {
        MinecraftClient client = MinecraftClient.getInstance();
        KCPaths paths = KCVisuals.pathsOrDefault();
        return CompletableFuture.supplyAsync(() -> {
                    CategoryRegistry reg = registry();
                    PackIndex index = PackScanner.scan(paths.packs(), reg, paths.index());
                    PackBuilder.BuildReport report = PackBuilder.build(selection, index, reg, paths.generatedPack());
                    KCVisuals.LOGGER.info("Built {}: {} files, missing packs {}",
                            KCPaths.CUSTOM_PACK_NAME, report.filesWritten(), report.missingPacks());
                    return report;
                }, BUILD_EXECUTOR)
                .thenApplyAsync(report -> {
                    enableOnTopAndReload(client);
                    return report;
                }, client)
                .whenComplete((r, e) -> {
                    if (e != null) {
                        KCVisuals.LOGGER.error("Applying {} failed", KCPaths.CUSTOM_PACK_NAME, e);
                    }
                });
    }

    /** Must run on the client thread. */
    static void enableOnTopAndReload(MinecraftClient client) {
        ResourcePackManager manager = client.getResourcePackManager();
        manager.scanPacks();
        if (!manager.hasProfile(GeneratedPackProvider.PACK_ID)) {
            KCVisuals.LOGGER.error("{} is not registered with the resource pack manager", GeneratedPackProvider.PACK_ID);
            return;
        }
        List<String> enabled = new ArrayList<>(manager.getEnabledIds());
        enabled.remove(GeneratedPackProvider.PACK_ID);
        enabled.add(GeneratedPackProvider.PACK_ID); // last = highest priority = top of the list
        manager.setEnabledProfiles(enabled);

        List<String> before = List.copyOf(client.options.resourcePacks);
        // Writes options.resourcePacks / options.txt; reloads by itself only if the list changed.
        client.options.refreshResourcePacks(manager);
        if (before.equals(client.options.resourcePacks)) {
            client.reloadResources();
        }
        KCVisuals.LOGGER.info("Enabled {} on top ({}) and reloading resources",
                GeneratedPackProvider.PACK_ID, manager.getEnabledIds());
    }
}
