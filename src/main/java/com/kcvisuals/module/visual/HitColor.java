package com.kcvisuals.module.visual;

import com.kcvisuals.gui.Theme;
import com.kcvisuals.mixin.OverlayTextureMixin;
import com.kcvisuals.module.Module;
import com.kcvisuals.module.setting.ColorSetting;
import com.kcvisuals.render.HitMath;
import com.kcvisuals.render.WorldRender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

/**
 * Recolours the hurt tint of living entities. Vanilla draws it from the top half of the shared 16×16 overlay texture
 * (rows 0-7, texel 0xB2FF0000); this module rewrites those rows and re-uploads the texture when the colour changes,
 * and restores the vanilla texel when disabled. No per-entity hook is needed.
 */
public class HitColor extends Module implements WorldRender.Ticker {
    /** Vanilla hurt texel (30 % red tint). */
    public static final int VANILLA_TEXEL = 0xB2FF0000;
    private static final int HURT_ROWS = 8;
    private static final int SIZE = 16;

    public final ColorSetting color = add(new ColorSetting("color", "Farbe", (Theme.ACCENT & 0xFFFFFF) | 0x66000000));

    /** Texel currently in the hurt rows of the overlay texture. */
    private int applied = VANILLA_TEXEL;

    public HitColor() {
        super("hit_color", "Trefferfarbe", "Färbt getroffene Wesen in deiner Farbe statt rot.");
    }

    @Override
    public void tick(MinecraftClient mc) {
        apply(mc, HitMath.overlayTexel(color.get()));
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && applied != VANILLA_TEXEL) {
            mc.execute(() -> apply(mc, VANILLA_TEXEL));
        }
    }

    private void apply(MinecraftClient mc, int texel) {
        if (texel == applied || mc.gameRenderer == null) {
            return;
        }
        NativeImageBackedTexture tex = ((OverlayTextureMixin) mc.gameRenderer.getOverlayTexture()).kcvisuals$texture();
        NativeImage img = tex.getImage();
        if (img == null) {
            return;
        }
        for (int y = 0; y < HURT_ROWS; y++) {
            for (int x = 0; x < SIZE; x++) {
                img.setColorArgb(x, y, texel);
            }
        }
        tex.upload();
        applied = texel;
    }
}
