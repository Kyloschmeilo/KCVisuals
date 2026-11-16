package com.kcvisuals.mixin;

import com.kcvisuals.module.visual.RenderTweaks;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Render Tweaks: scales the number of debris particles an explosion schedules on the client. */
@Mixin(ClientWorld.class)
public abstract class ClientWorldParticlesMixin {
    @ModifyVariable(method = "addBlockParticleEffects", at = @At("HEAD"), argsOnly = true)
    private int kcvisuals$blockCount(int blockCount) {
        return RenderTweaks.explosionBlockParticles(blockCount);
    }
}
