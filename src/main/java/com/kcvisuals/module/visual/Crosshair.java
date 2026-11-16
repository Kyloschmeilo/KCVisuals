package com.kcvisuals.module.visual;

import com.kcvisuals.gui.Theme;
import com.kcvisuals.module.Module;
import com.kcvisuals.module.setting.BoolSetting;
import com.kcvisuals.module.setting.ColorSetting;
import com.kcvisuals.module.setting.ModeSetting;
import com.kcvisuals.module.setting.NumberSetting;
import com.kcvisuals.render.CrosshairGeometry;
import com.kcvisuals.render.CrosshairGeometry.Rect;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Custom crosshair drawn in framebuffer pixels. InGameHudCrosshairMixin calls {@link #draw} in place of the vanilla
 * crosshair texture, i.e. only when vanilla would draw one (HUD shown, first person, not spectator without a target,
 * no 3D debug crosshair); the attack indicator below it stays vanilla.
 */
public class Crosshair extends Module {
    private static Crosshair instance;

    public final ModeSetting mode = add(new ModeSetting("mode", "Form", CrosshairGeometry.CROSS,
            CrosshairGeometry.CROSS, CrosshairGeometry.DOT, CrosshairGeometry.CIRCLE, CrosshairGeometry.PLUS_DOT));
    public final NumberSetting size = add(new NumberSetting("size", "Größe", 6, 1, 20, 1));
    public final NumberSetting gap = add(new NumberSetting("gap", "Abstand", 3, 0, 15, 1));
    public final NumberSetting thickness = add(new NumberSetting("thickness", "Dicke", 2, 1, 6, 1));
    public final ColorSetting color = add(new ColorSetting("color", "Farbe", Theme.ACCENT));
    public final BoolSetting outline = add(new BoolSetting("outline", "Umrandung", true));

    private String cachedKey = "";
    private List<Rect> cached = List.of();

    public Crosshair() {
        super("crosshair", "Fadenkreuz", "Eigenes Fadenkreuz: Kreuz, Punkt, Kreis oder Plus-Punkt.");
        instance = this;
    }

    public static Crosshair instance() {
        return instance;
    }

    public static boolean active() {
        return instance != null && instance.enabled();
    }

    private List<Rect> shapes() {
        String key = mode.get() + '/' + size.getInt() + '/' + gap.getInt() + '/' + thickness.getInt();
        if (!key.equals(cachedKey)) {
            cached = CrosshairGeometry.build(mode.get(), size.getInt(), gap.getInt(), thickness.getInt());
            cachedKey = key;
        }
        return cached;
    }

    public void draw(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        float scale = Math.max(1, mc.getWindow().getScaleFactor());
        int cx = Math.round(ctx.getScaledWindowWidth() * scale / 2f);
        int cy = Math.round(ctx.getScaledWindowHeight() * scale / 2f);
        int c = color.get();
        List<Rect> rects = shapes();
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().scale(1f / scale, 1f / scale);
        if (outline.get()) {
            int o = c & 0xFF000000; // black, same opacity as the crosshair
            for (Rect r : rects) {
                Rect g = r.grow(1);
                ctx.fill(cx + g.x0(), cy + g.y0(), cx + g.x1(), cy + g.y1(), o);
            }
        }
        for (Rect r : rects) {
            ctx.fill(cx + r.x0(), cy + r.y0(), cx + r.x1(), cy + r.y1(), c);
        }
        ctx.getMatrices().popMatrix();
    }
}
