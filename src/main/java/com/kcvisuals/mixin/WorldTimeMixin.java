package com.kcvisuals.mixin;

import com.kcvisuals.module.visual.CustomSky;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Custom Sky fixed time: the client world reports the fixed time of day to its readers (sky, celestial angles,
 * lightmap via the environment attribute timelines). Only {@link ClientWorld} instances are affected; the stored
 * client time keeps ticking and syncing from the server (ClientWorld reads its properties directly), and the
 * integrated server's worlds are never touched.
 */
@Mixin(World.class)
public abstract class WorldTimeMixin {
    @Inject(method = "getTimeOfDay", at = @At("HEAD"), cancellable = true)
    private void kcvisuals$fixedTime(CallbackInfoReturnable<Long> cir) {
        if ((Object) this instanceof ClientWorld) {
            long fixed = CustomSky.fixedTimeOfDay();
            if (fixed >= 0) {
                cir.setReturnValue(fixed);
            }
        }
    }
}
