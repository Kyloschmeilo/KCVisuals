package com.kcvisuals.mixin;

import com.kcvisuals.module.visual.AspectRatio;
import com.kcvisuals.render.ScreenMath;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Aspect Ratio: stretches the world projection horizontally, and the first-person hand projection by the same
 * factor (passed as a narrower width, which also keys that projection's cache so toggling takes effect at once).
 * A horizontally stretched projection only narrows the visible area, so frustum culling stays correct.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererProjectionMixin {
    @Inject(method = "getBasicProjectionMatrix", at = @At("RETURN"))
    private void kcvisuals$stretchWorld(float fovDegrees, CallbackInfoReturnable<Matrix4f> cir) {
        float f = AspectRatio.factor();
        if (f != 1f) {
            cir.getReturnValue().scaleLocal(f, 1f, 1f);
        }
    }

    @ModifyArg(method = "renderWorld",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/ProjectionMatrix3;set(IIF)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"),
            index = 0)
    private int kcvisuals$stretchHand(int width) {
        float f = AspectRatio.factor();
        return f != 1f ? ScreenMath.stretchedWidth(width, f) : width;
    }
}
