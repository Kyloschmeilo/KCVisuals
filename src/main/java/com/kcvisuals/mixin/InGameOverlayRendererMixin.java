package com.kcvisuals.mixin;

import com.kcvisuals.module.visual.RenderTweaks;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Render Tweaks: lowers or hides the first-person fire overlay (the burning state itself is unchanged). */
@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererMixin {
    @Unique
    private static boolean kcvisuals$pushed;

    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void kcvisuals$fireHead(MatrixStack matrices, VertexConsumerProvider consumers, Sprite sprite, CallbackInfo ci) {
        if (RenderTweaks.hideFire()) {
            ci.cancel();
            return;
        }
        float drop = RenderTweaks.fireDrop();
        kcvisuals$pushed = drop != 0f;
        if (kcvisuals$pushed) {
            matrices.push();
            matrices.translate(0f, drop, 0f);
        }
    }

    @Inject(method = "renderFireOverlay", at = @At("TAIL"))
    private static void kcvisuals$fireTail(MatrixStack matrices, VertexConsumerProvider consumers, Sprite sprite, CallbackInfo ci) {
        if (kcvisuals$pushed) {
            kcvisuals$pushed = false;
            matrices.pop();
        }
    }
}
