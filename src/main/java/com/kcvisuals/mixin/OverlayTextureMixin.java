package com.kcvisuals.mixin;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.texture.NativeImageBackedTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Access to the 16×16 entity overlay texture so HitColor can recolour its hurt rows. */
@Mixin(OverlayTexture.class)
public interface OverlayTextureMixin {
    @Accessor("texture")
    NativeImageBackedTexture kcvisuals$texture();
}
