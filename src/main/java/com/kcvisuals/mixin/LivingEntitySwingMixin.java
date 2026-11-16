package com.kcvisuals.mixin;

import com.kcvisuals.module.visual.CustomHand;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Custom Hand swing speed: only the client player's own swing animation length changes (client-side animation
 * state); other entities and the integrated server's player objects keep the vanilla duration.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySwingMixin {
    @Inject(method = "getHandSwingDuration", at = @At("RETURN"), cancellable = true)
    private void kcvisuals$swingDuration(CallbackInfoReturnable<Integer> cir) {
        if (CustomHand.active() && (Object) this == MinecraftClient.getInstance().player) {
            cir.setReturnValue(CustomHand.instance().swingDuration(cir.getReturnValueI()));
        }
    }
}
