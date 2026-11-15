package com.kcvisuals.gui.widget;

import com.kcvisuals.gui.Theme;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.DrawContext;

/**
 * Compact colour row (label + swatch + hex) that expands below itself into a saturation/value square and a hue strip.
 * The row height is {@link #h}; when expanded the widget additionally covers {@link #EXPANDED_H} pixels below it, so
 * owners should either leave room or draw it last.
 */
public class ColorPicker extends Widget {
    public static final int EXPANDED_H = 70;

    private final String label;
    private float hue, sat, val;
    private int alpha = 0xFF;
    private boolean expanded;
    private int dragging; // 0 none, 1 square, 2 hue
    private final IntConsumer onChange;

    public ColorPicker(int x, int y, int w, int h, String label, int argb, IntConsumer onChange) {
        super(x, y, w, h);
        this.label = label;
        this.onChange = onChange;
        setColor(argb);
    }

    public int color() {
        return (alpha << 24) | (hsvToRgb(hue, sat, val) & 0xFFFFFF);
    }

    public void setColor(int argb) {
        alpha = (argb >>> 24) & 0xFF;
        float[] hsv = rgbToHsv(argb);
        hue = hsv[0];
        sat = hsv[1];
        val = hsv[2];
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean e) {
        expanded = e;
    }

    /** HSV (each 0..1) to opaque RGB. */
    public static int hsvToRgb(float h, float s, float v) {
        h = (h % 1f + 1f) % 1f;
        float hh = h * 6f;
        int i = (int) Math.floor(hh) % 6;
        float f = hh - (float) Math.floor(hh);
        float p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        float r, g, b;
        switch (i) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return 0xFF000000 | (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    /** RGB to {hue, saturation, value}, each 0..1. */
    public static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d == 0) {
            h = 0;
        } else if (max == r) {
            h = ((g - b) / d) / 6f;
        } else if (max == g) {
            h = ((b - r) / d + 2) / 6f;
        } else {
            h = ((r - g) / d + 4) / 6f;
        }
        h = (h % 1f + 1f) % 1f;
        return new float[] {h, max == 0 ? 0 : d / max, max};
    }

    private int squareX() {
        return x + 6;
    }

    private int squareY() {
        return y + h + 4;
    }

    private int squareSize() {
        return EXPANDED_H - 8;
    }

    private int hueX() {
        return squareX() + squareSize() + 6;
    }

    private static final int HUE_W = 10;

    @Override
    public boolean isMouseOver(double mx, double my) {
        return visible && (Draw.inside(mx, my, x, y, w, h) || (expanded && Draw.inside(mx, my, x, y + h, w, EXPANDED_H)));
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        if (!visible) {
            return;
        }
        hoverAnim = Draw.approach(hoverAnim, Draw.inside(mx, my, x, y, w, h) ? 1f : 0f);
        if (hoverAnim > 0.01f) {
            Draw.roundRect(ctx, x, y, w, h, 4, Theme.withAlpha(Theme.SURFACE_HOVER, hoverAnim));
        }
        int ty = y + (h - 8) / 2 + 1;
        Draw.text(ctx, label, x + 6, ty, Theme.TEXT);
        String hex = String.format("#%06X", color() & 0xFFFFFF);
        int sw = 14;
        int sx = x + w - sw - 6;
        Draw.text(ctx, hex, sx - 6 - Draw.font().getWidth(hex), ty, Theme.TEXT_DIM);
        Draw.roundRect(ctx, sx, y + (h - 10) / 2, sw, 10, 3, color() | 0xFF000000);
        Draw.roundBorderPx(ctx, sx, y + (h - 10) / 2, sw, 10, 3, 1, 0x40FFFFFF);
        if (!expanded) {
            return;
        }
        Draw.roundRect(ctx, x, y + h, w, EXPANDED_H, 5, Theme.SURFACE);
        int qx = squareX(), qy = squareY(), qs = squareSize();
        int pure = hsvToRgb(hue, 1, 1);
        for (int i = 0; i < qs; i++) {
            float s = i / (float) (qs - 1);
            int top = hsvToRgb(hue, s, 1);
            ctx.fillGradient(qx + i, qy, qx + i + 1, qy + qs, top, 0xFF000000);
        }
        int cx = qx + Math.round(sat * (qs - 1));
        int cy = qy + Math.round((1 - val) * (qs - 1));
        ctx.fill(cx - 2, cy - 2, cx + 3, cy - 1, 0xFFFFFFFF);
        ctx.fill(cx - 2, cy + 2, cx + 3, cy + 3, 0xFFFFFFFF);
        ctx.fill(cx - 2, cy - 1, cx - 1, cy + 2, 0xFFFFFFFF);
        ctx.fill(cx + 2, cy - 1, cx + 3, cy + 2, 0xFFFFFFFF);
        int hx = hueX();
        for (int j = 0; j < qs; j++) {
            ctx.fill(hx, qy + j, hx + HUE_W, qy + j + 1, hsvToRgb(j / (float) qs, 1, 1));
        }
        int hy = qy + Math.round(hue * (qs - 1));
        ctx.fill(hx - 1, hy - 1, hx + HUE_W + 1, hy + 2, 0xFFFFFFFF);
        ctx.fill(hx, hy, hx + HUE_W, hy + 1, pure);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || !enabled || !visible) {
            return false;
        }
        if (Draw.inside(mx, my, x, y, w, h)) {
            expanded = !expanded;
            return true;
        }
        if (!expanded) {
            return false;
        }
        if (Draw.inside(mx, my, squareX(), squareY(), squareSize(), squareSize())) {
            dragging = 1;
            update(mx, my);
            return true;
        }
        if (Draw.inside(mx, my, hueX() - 1, squareY(), HUE_W + 2, squareSize())) {
            dragging = 2;
            update(mx, my);
            return true;
        }
        return Draw.inside(mx, my, x, y + h, w, EXPANDED_H);
    }

    private void update(double mx, double my) {
        int qs = squareSize();
        float fy = (float) Math.max(0, Math.min(1, (my - squareY()) / (qs - 1)));
        if (dragging == 1) {
            sat = (float) Math.max(0, Math.min(1, (mx - squareX()) / (qs - 1)));
            val = 1 - fy;
        } else if (dragging == 2) {
            hue = Math.min(0.999f, fy);
        }
        if (onChange != null) {
            onChange.accept(color());
        }
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != 0) {
            update(mx, my);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean was = dragging != 0;
        dragging = 0;
        return was;
    }
}
