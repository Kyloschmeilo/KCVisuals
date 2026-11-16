package com.kcvisuals.mixin;

import com.kcvisuals.module.visual.RenderTweaks;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Render Tweaks: scales the totem pop item (GameRenderer.showFloatingItem → renderFloatingItem) around itself, right
 * after vanilla's own 0.8 scale; 0 % skips the item. The pop sound, particles and the totem itself are unchanged.
 */
@Mixin(InGameOverlayRenderer.class)
public abstract class TotemAnimationMixin {
    @Inject(method = "renderFloatingItem", at = @At("HEAD"), cancellable = true)
    private void kcvisuals$hide(MatrixStack matrices, float tickProgress, OrderedRenderCommandQueue queue, CallbackInfo ci) {
        if (RenderTweaks.totemScale() <= 0f) {
            ci.cancel();
        }
    }

    @Inject(method = "renderFloatingItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;scale(FFF)V", ordinal = 0, shift = At.Shift.AFTER))
    private void kcvisuals$scale(MatrixStack matrices, float tickProgress, OrderedRenderCommandQueue queue, CallbackInfo ci) {
        float s = RenderTweaks.totemScale();
        if (s != 1f) {
            matrices.scale(s, s, s);
        }
    }
}
