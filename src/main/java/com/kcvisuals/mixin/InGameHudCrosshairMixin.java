package com.kcvisuals.mixin;

import com.kcvisuals.module.visual.Crosshair;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Crosshair: swaps only the vanilla crosshair texture draw (ordinal 0 in renderCrosshair) for the custom one. That
 * call is reached only under vanilla's own conditions (HUD visible, first person, spectator rule, no 3D debug
 * crosshair); the attack indicator draws after it unchanged.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudCrosshairMixin {
    @Redirect(method = "renderCrosshair",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V",
                    ordinal = 0))
    private void kcvisuals$crosshair(DrawContext ctx, RenderPipeline pipeline, Identifier texture, int x, int y, int w, int h) {
        if (Crosshair.active()) {
            Crosshair.instance().draw(ctx);
        } else {
            ctx.drawGuiTexture(pipeline, texture, x, y, w, h);
        }
    }
}
