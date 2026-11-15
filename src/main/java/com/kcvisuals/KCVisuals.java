package com.kcvisuals;

import com.kcvisuals.gui.KCScreen;
import com.kcvisuals.config.KCConfig;
import com.kcvisuals.module.ModuleManager;
import java.io.IOException;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KCVisuals implements ClientModInitializer {
    public static final String MOD_ID = "kcvisuals";
    public static final Logger LOGGER = LoggerFactory.getLogger("KC Visuals");
    private static KCPaths paths;
    private static final ModuleManager MODULES = new ModuleManager();
    private static KCConfig config = new KCConfig();

    public static ModuleManager modules() {
        return MODULES;
    }

    public static KCConfig config() {
        return config;
    }

    /** Writes modules and the HUD slot to config/kcvisuals.json. */
    public static void saveConfig() {
        config.save(pathsOrDefault().config(), MODULES);
    }

    /** Paths, also usable before the client entrypoint has run (e.g. from mixins during client construction). */
    public static KCPaths pathsOrDefault() {
        return paths != null ? paths : KCPaths.of(FabricLoader.getInstance().getGameDir());
    }

    public static KCPaths paths() {
        return paths;
    }

    @Override
    public void onInitializeClient() {
        paths = KCPaths.of(FabricLoader.getInstance().getGameDir());
        try {
            paths.ensureDirs();
        } catch (IOException e) {
            LOGGER.error("Konnte KC-Visuals-Ordner nicht anlegen", e);
        }
        // Modules register themselves into MODULES before this point (Tasks 9-11).
        config = KCConfig.load(paths.config(), MODULES);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> saveConfig());
        com.kcvisuals.pack.StartupApply.register();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("kcv").executes(ctx -> {
                    openMenu();
                    return 1;
                })));
    }

    /** Opens the menu on the next client tick (chat closes the current screen after a command runs). */
    public static void openMenu() {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.send(() -> mc.setScreen(new KCScreen(mc.currentScreen)));
    }
}
