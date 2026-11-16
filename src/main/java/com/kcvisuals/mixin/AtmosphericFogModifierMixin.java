package com.kcvisuals.mixin;

import com.kcvisuals.module.visual.CustomSky;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.AtmosphericFogModifier;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Custom Sky, atmospheric (air) fog only. Vanilla uses the first matching fog modifier (lava, powder snow,
 * blindness, darkness, water, then atmospheric), so these hooks never run while an effect or fluid fog applies.
 */
@Mixin(AtmosphericFogModifier.class)
public abstract class AtmosphericFogModifierMixin {
    @Inject(method = "getFogColor", at = @At("RETURN"), cancellable = true)
    private void kcvisuals$horizon(ClientWorld world, Camera camera, int viewDistance, float tickProgress,
            CallbackInfoReturnable<Integer> cir) {
        CustomSky sky = CustomSky.instance();
        if (sky != null && sky.enabled() && world.getDimension().skybox() == DimensionType.Skybox.OVERWORLD) {
            cir.setReturnValue(sky.horizon.get() | 0xFF000000);
        }
    }

    @Inject(method = "applyStartEndModifier", at = @At("TAIL"))
    private void kcvisuals$noFog(FogData data, Camera camera, ClientWorld world, float viewDistanceBlocks,
            RenderTickCounter tickCounter, CallbackInfo ci) {
        CustomSky.setAtmosphericFog(true);
        if (CustomSky.removeFog()) {
            data.environmentalStart = Float.MAX_VALUE;
            data.environmentalEnd = Float.MAX_VALUE;
        }
    }
}
