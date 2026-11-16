package com.kcvisuals.mixin;

import com.kcvisuals.module.visual.RenderTweaks;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Render Tweaks: thins out explosion puffs (TNT, creepers, end crystals). Only the visible EXPLOSION particles are
 * counted, not the invisible emitter that spawns them, so the multiplier applies exactly once.
 */
@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {
    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), cancellable = true)
    private void kcvisuals$explosion(ParticleEffect parameters, double x, double y, double z, double vx, double vy, double vz,
            CallbackInfoReturnable<Particle> cir) {
        if (parameters.getType() == ParticleTypes.EXPLOSION && !RenderTweaks.allowExplosionParticle()) {
            cir.setReturnValue(null);
        }
    }
}
