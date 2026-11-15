package com.kcvisuals.pack;

import com.kcvisuals.KCPaths;
import com.kcvisuals.KCVisuals;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;

/** Applies the active profile once, after the client's first resource load has finished (title screen showing). */
public final class StartupApply {
    private static boolean done;

    private StartupApply() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(StartupApply::tick);
    }

    private static void tick(MinecraftClient mc) {
        if (done || mc.getOverlay() != null || !(mc.currentScreen instanceof TitleScreen)) {
            return;
        }
        done = true;
        KCPaths paths = KCVisuals.pathsOrDefault();
        CompletableFuture.supplyAsync(() -> {
            ProfileStore store = new ProfileStore(paths.profiles());
            String name = store.active();
            CategoryRegistry reg = PackApplier.registry();
            PackIndex index = PackScanner.scan(paths.packs(), reg, paths.index());
            ProfileStore.LoadResult r = store.load(name, index);
            if (!r.droppedCategories().isEmpty()) {
                KCVisuals.LOGGER.warn("Profile '{}': dropped categories with missing packs: {}", name, r.droppedCategories());
            }
            return new Object[] {name, r.selection()};
        }).thenCompose(o -> {
            Selection sel = (Selection) o[1];
            if (sel.isEmpty()) {
                KCVisuals.LOGGER.info("Active profile '{}' is empty, nothing to apply at startup", o[0]);
                return CompletableFuture.completedFuture(null);
            }
            KCVisuals.LOGGER.info("Applying active profile '{}' at startup ({} picks)", o[0], sel.picks().size());
            return PackApplier.apply(sel).thenAccept(rep ->
                    KCVisuals.LOGGER.info("Startup apply of profile '{}' finished", o[0]));
        }).exceptionally(e -> {
            KCVisuals.LOGGER.error("Startup profile apply failed", e);
            return null;
        });
    }
}
