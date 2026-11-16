package com.kcvisuals.mixin;

import com.kcvisuals.module.visual.CustomHand;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Custom Hand: view-space offset right after vanilla pushes the per-hand matrix, and rotation/scale right before the
 * item model is drawn (both renderItem call sites: crossbow and every other item), so the item shrinks around itself.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    private static Arm kcvisuals$arm(AbstractClientPlayerEntity player, Hand hand) {
        return hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
    }

    @Inject(method = "renderFirstPersonItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;push()V", shift = At.Shift.AFTER))
    private void kcvisuals$offset(AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand,
            float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
            OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        if (CustomHand.active()) {
            CustomHand.instance().applyOffset(matrices, hand == Hand.MAIN_HAND, kcvisuals$arm(player, hand));
        }
    }

    @Inject(method = "renderFirstPersonItem",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V"))
    private void kcvisuals$itemTransform(AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand,
            float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
            OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        if (CustomHand.active()) {
            CustomHand.instance().applyItemTransform(matrices, kcvisuals$arm(player, hand));
        }
    }
}
