package com.kcvisuals.mixin;

import com.kcvisuals.KCVisuals;
import com.kcvisuals.pack.GeneratedPackProvider;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.client.resource.DefaultClientResourcePackProvider;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric API 0.141.6 only offers built-in packs shipped inside a mod jar, so the generated folder pack is added here:
 * to the client's resource pack manager (recognised by its vanilla default-pack provider) only, never to data packs.
 */
@Mixin(ResourcePackManager.class)
public abstract class ResourcePackManagerMixin {
    @Shadow
    @Final
    @Mutable
    private Set<ResourcePackProvider> providers;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void kcvisuals$addGeneratedPack(ResourcePackProvider[] given, CallbackInfo ci) {
        boolean client = false;
        for (ResourcePackProvider p : given) {
            if (p instanceof DefaultClientResourcePackProvider) {
                client = true;
                break;
            }
        }
        if (client) {
            this.providers = new LinkedHashSet<>(this.providers);
            this.providers.add(new GeneratedPackProvider(
                    () -> KCVisuals.pathsOrDefault().generatedPack()));
        }
    }
}
