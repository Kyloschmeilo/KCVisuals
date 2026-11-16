package com.kcvisuals.hud;

import com.kcvisuals.gui.KCScreen;
import com.kcvisuals.gui.hud.HudEditorScreen;
import com.kcvisuals.gui.widget.Draw;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;

/** Hooks the HUD into the Fabric HudElementRegistry (drawn above the vanilla layers) and the attack event. */
public final class HudRenderer {
    private HudRenderer() {}

    public static void register() {
        HudElementRegistry.addLast(Identifier.of("kcvisuals", "hud"), (ctx, tick) -> render(ctx));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            TargetHud.clearTarget();
            HudManager.get().resetAll();
        });
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient()) {
                TargetHud.setTarget(entity);
            }
            return ActionResult.PASS;
        });
    }

    private static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) {
            return;
        }
        if (mc.currentScreen instanceof KCScreen || mc.currentScreen instanceof HudEditorScreen) {
            return;
        }
        Draw.beginFrame();
        Keystrokes.poll();
        HudManager.get().renderAll(ctx, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight(), false);
    }
}
