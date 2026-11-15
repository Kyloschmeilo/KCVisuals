package com.kcvisuals.gui.widget;

import com.kcvisuals.gui.Theme;
import java.util.function.Consumer;
import net.minecraft.client.gui.DrawContext;

/** Label on the left, animated switch on the right. The whole row is clickable. */
public class Toggle extends Widget {
    private static final int SW = 20, SH = 10;

    private final String label;
    private boolean value;
    private float anim;
    private final Consumer<Boolean> onChange;

    public Toggle(int x, int y, int w, int h, String label, boolean value, Consumer<Boolean> onChange) {
        super(x, y, w, h);
        this.label = label;
        this.value = value;
        this.anim = value ? 1f : 0f;
        this.onChange = onChange;
    }

    public boolean value() {
        return value;
    }

    public void setValue(boolean v) {
        value = v;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        if (!visible) {
            return;
        }
        updateHover(mx, my);
        anim = Draw.approach(anim, value ? 1f : 0f);
        if (hoverAnim > 0.01f) {
            Draw.roundRect(ctx, x, y, w, h, 4, Theme.withAlpha(Theme.SURFACE_HOVER, hoverAnim));
        }
        int ty = y + (h - 8) / 2 + 1;
        Draw.text(ctx, Draw.ellipsize(label, w - SW - 14), x + 6, ty, enabled ? Theme.TEXT : Theme.TEXT_FAINT);
        int sx = x + w - SW - 6;
        int sy = y + (h - SH) / 2;
        int track = Theme.mix(Theme.BORDER, Theme.ACCENT, anim);
        Draw.roundRect(ctx, sx, sy, SW, SH, SH / 2, enabled ? track : Theme.SURFACE);
        int knob = SH - 4;
        int kx = sx + 2 + Math.round((SW - knob - 4) * anim);
        Draw.roundRect(ctx, kx, sy + 2, knob, knob, knob / 2, Theme.mix(0xFFC8C8D8, 0xFFFFFFFF, anim));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && enabled && isMouseOver(mx, my)) {
            value = !value;
            if (onChange != null) {
                onChange.accept(value);
            }
            return true;
        }
        return false;
    }
}
