package com.kcvisuals.mixin;

import com.kcvisuals.module.visual.RenderTweaks;
import com.kcvisuals.render.ScreenMath;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Render Tweaks hurt-cam off: the damage tilt strength read inside tiltViewWhenHurt becomes 0 (same as the vanilla
 * option at 0 %). The death roll in the same method and the option value itself are untouched.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererHurtCamMixin {
    @Redirect(method = "tiltViewWhenHurt",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;"))
    private Object kcvisuals$tilt(SimpleOption<?> option) {
        Object v = option.getValue();
        if (v instanceof Double d) {
            return ScreenMath.hurtTilt(d, RenderTweaks.hurtCamOff());
        }
        return v;
    }
}
