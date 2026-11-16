package com.kcvisuals.module.visual;

import com.kcvisuals.gui.Theme;
import com.kcvisuals.module.Module;
import com.kcvisuals.module.setting.ColorSetting;
import com.kcvisuals.module.setting.NumberSetting;
import net.minecraft.client.render.DrawStyle;

/**
 * Colour and line width for the vanilla F3+B hitboxes. Has no effect unless vanilla draws them; the box geometry is
 * never changed (see EntityHitboxDebugRendererMixin).
 */
public class HitboxStyle extends Module {
    /** Vanilla colour of the entity bounding box (white); the eye-height and passenger boxes keep their colours. */
    private static final int VANILLA_BOX = 0xFFFFFFFF;
    private static HitboxStyle instance;

    public final ColorSetting color = add(new ColorSetting("color", "Linienfarbe", Theme.ACCENT));
    public final NumberSetting width = add(new NumberSetting("width", "Linienbreite", 2.5, 1.0, 6.0, 0.5));

    public HitboxStyle() {
        super("hitbox_style", "Hitbox-Stil", "Farbe und Linienbreite der F3+B-Hitboxen (Größe bleibt unverändert).");
        instance = this;
    }

    public static HitboxStyle instance() {
        return instance;
    }

    /** Same box, new stroke: the main box gets the colour, every box gets the width. */
    public DrawStyle restyle(DrawStyle s) {
        if (!s.hasStroke()) {
            return s;
        }
        int stroke = s.stroke() == VANILLA_BOX ? color.get() : s.stroke();
        return new DrawStyle(stroke, width.getFloat(), s.fill());
    }
}
