package com.kcvisuals.mixin;

import com.kcvisuals.module.visual.Fullbright;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Fullbright: replaces the brightness factor written into the lightmap uniform ({@code max(0, gamma - darkness)}).
 * The gamma option is only read, never set, so options.txt cannot pick up the boost.
 */
@Mixin(LightmapTextureManager.class)
public abstract class LightmapMixin {
    @ModifyArg(method = "update", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F"), index = 1)
    private float kcvisuals$brightness(float gammaMinusDarkness) {
        MinecraftClient mc = MinecraftClient.getInstance();
        float userGamma = mc.options.getGamma().getValue().floatValue();
        return Fullbright.brightness(gammaMinusDarkness, userGamma);
    }
}
