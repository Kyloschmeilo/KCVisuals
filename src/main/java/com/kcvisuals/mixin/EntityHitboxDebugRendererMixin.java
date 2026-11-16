package com.kcvisuals.mixin;

import com.kcvisuals.module.visual.HitboxStyle;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.render.debug.EntityHitboxDebugRenderer;
import net.minecraft.util.math.Box;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Restyles the vanilla F3+B hitbox boxes (only drawn while that debug entry is on). Only the stroke colour and width
 * of the {@link DrawStyle} change; the {@link Box} argument is untouched, so the box size stays vanilla.
 */
@Mixin(EntityHitboxDebugRenderer.class)
public abstract class EntityHitboxDebugRendererMixin {
    @ModifyArg(method = "drawHitbox",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/debug/gizmo/GizmoDrawing;box(Lnet/minecraft/util/math/Box;Lnet/minecraft/client/render/DrawStyle;)Lnet/minecraft/world/debug/gizmo/VisibilityConfigurable;"),
            index = 1)
    private DrawStyle kcvisuals$style(DrawStyle style) {
        HitboxStyle m = HitboxStyle.instance();
        return m != null && m.enabled() ? m.restyle(style) : style;
    }
}
