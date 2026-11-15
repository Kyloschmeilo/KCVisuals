package com.kcvisuals.hud;

import com.kcvisuals.gui.Theme;
import com.kcvisuals.gui.widget.Draw;
import net.minecraft.client.gui.DrawContext;

/** Shared look of all HUD elements: rounded translucent card with a hairline border. */
public final class HudStyle {
    private HudStyle() {}

    public static void panel(DrawContext ctx, int x, int y, int w, int h) {
        int bg = HudManager.get().background();
        Draw.roundRect(ctx, x, y, w, h, 4, bg);
        Draw.roundBorderPx(ctx, x, y, w, h, 4, 1,
                Theme.withAlpha(Theme.BORDER, Math.min(1f, ((bg >>> 24) & 0xFF) / 160f)));
    }

    /** Accent bar on the left edge of a card. */
    public static void accentBar(DrawContext ctx, int x, int y, int h) {
        Draw.roundRect(ctx, x + 2, y + 3, 2, Math.max(2, h - 6), 1, Theme.ACCENT);
    }
}
