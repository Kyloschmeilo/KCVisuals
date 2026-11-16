package com.kcvisuals.mixin;

import com.kcvisuals.module.visual.CustomSky;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.state.SkyRenderState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Custom Sky: overrides the overworld sky colour and star brightness after vanilla filled the render state. */
@Mixin(SkyRendering.class)
public abstract class SkyRenderingMixin {
    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void kcvisuals$sky(ClientWorld world, float tickProgress, Camera camera, SkyRenderState state, CallbackInfo ci) {
        CustomSky sky = CustomSky.instance();
        if (sky == null || !sky.enabled() || state.skybox != DimensionType.Skybox.OVERWORLD) {
            return;
        }
        state.skyColor = sky.top.get() | 0xFF000000;
        state.starBrightness = sky.stars.get() ? sky.starBrightness.getFloat() : 0f;
    }
}
