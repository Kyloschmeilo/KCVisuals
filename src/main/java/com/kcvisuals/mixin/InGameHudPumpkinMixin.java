package com.kcvisuals.mixin;

import com.kcvisuals.module.visual.RenderTweaks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Render Tweaks: skips only the carved pumpkin camera overlay; powder snow and other overlays stay. */
@Mixin(InGameHud.class)
public abstract class InGameHudPumpkinMixin {
    @Unique
    private static final Identifier KCVISUALS_PUMPKIN = Identifier.ofVanilla("textures/misc/pumpkinblur.png");

    @Inject(method = "renderOverlay", at = @At("HEAD"), cancellable = true)
    private void kcvisuals$pumpkin(DrawContext context, Identifier texture, float opacity, CallbackInfo ci) {
        if (KCVISUALS_PUMPKIN.equals(texture) && RenderTweaks.hidePumpkin()) {
            ci.cancel();
        }
    }
}
