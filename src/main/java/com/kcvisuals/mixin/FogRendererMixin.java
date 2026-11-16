package com.kcvisuals.mixin;

import com.kcvisuals.module.visual.CustomSky;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Custom Sky "Nebel aus": the render-distance fade band collapses onto the render distance edge (start = end), but
 * only in frames where the atmospheric modifier was the active one (see AtmosphericFogModifierMixin). Nothing beyond
 * the loaded render distance becomes visible.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    private static final String APPLY = "applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;";
    private static final String WRITE = "Lnet/minecraft/client/render/fog/FogRenderer;applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V";

    @Inject(method = APPLY, at = @At("HEAD"))
    private void kcvisuals$reset(Camera camera, int viewDistance, RenderTickCounter tickCounter, float skyDarkness,
            ClientWorld world, CallbackInfoReturnable<Vector4f> cir) {
        CustomSky.setAtmosphericFog(false);
    }

    @ModifyArg(method = APPLY, at = @At(value = "INVOKE", target = WRITE), index = 5)
    private float kcvisuals$renderDistanceStart(float start) {
        return CustomSky.removeFog() ? MinecraftClient.getInstance().options.getClampedViewDistance() * 16f : start;
    }
}
