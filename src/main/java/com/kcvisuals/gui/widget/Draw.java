package com.kcvisuals.gui.widget;

import com.kcvisuals.gui.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Drawing helpers shared by all KC Visuals widgets. Rounded shapes are rasterised at real screen-pixel resolution
 * (the GUI matrix is temporarily scaled by 1/guiScale), so corners stay smooth at every GUI scale.
 */
public final class Draw {
    private Draw() {}

    public static TextRenderer font() {
        return MinecraftClient.getInstance().textRenderer;
    }

    private static int scale() {
        return Math.max(1, MinecraftClient.getInstance().getWindow().getScaleFactor());
    }

    /** Filled rounded rectangle; {@code r} is the corner radius in GUI pixels. */
    public static void roundRect(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) {
            return;
        }
        int s = scale();
        int px = x * s, py = y * s, pw = w * s, ph = h * s;
        int pr = Shapes.clampRadius(r * s, pw, ph);
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().scale(1f / s, 1f / s);
        int row = 0;
        while (row < ph) {
            int inset = Shapes.insetAt(row, ph, pr);
            int end = row + 1;
            while (end < ph && Shapes.insetAt(end, ph, pr) == inset) {
                end++;
            }
            ctx.fill(px + inset, py + row, px + pw - inset, py + end, color);
            row = end;
        }
        ctx.getMatrices().popMatrix();
    }

    /** Rounded outline of thickness {@code t} GUI pixels (rendered as {@code t * guiScale} screen pixels). */
    public static void roundBorder(DrawContext ctx, int x, int y, int w, int h, int r, int t, int color) {
        roundBorderPx(ctx, x, y, w, h, r, t * scale(), color);
    }

    /** Rounded outline whose thickness is given in real screen pixels (1 = hairline). */
    public static void roundBorderPx(DrawContext ctx, int x, int y, int w, int h, int r, int tPx, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) {
            return;
        }
        int s = scale();
        int px = x * s, py = y * s, pw = w * s, ph = h * s;
        int pr = Shapes.clampRadius(r * s, pw, ph);
        int ir = Math.max(0, pr - tPx);
        int iw = pw - 2 * tPx, ih = ph - 2 * tPx;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().scale(1f / s, 1f / s);
        int row = 0;
        while (row < ph) {
            int outer = Shapes.insetAt(row, ph, pr);
            boolean innerRow = row >= tPx && row < ph - tPx && iw > 0 && ih > 0;
            int inner = innerRow ? tPx + Shapes.insetAt(row - tPx, ih, ir) : -1;
            int end = row + 1;
            while (end < ph && Shapes.insetAt(end, ph, pr) == outer) {
                boolean ir2 = end >= tPx && end < ph - tPx && iw > 0 && ih > 0;
                int in2 = ir2 ? tPx + Shapes.insetAt(end - tPx, ih, ir) : -1;
                if (in2 != inner) {
                    break;
                }
                end++;
            }
            if (inner < 0 || inner >= pw - inner) {
                ctx.fill(px + outer, py + row, px + pw - outer, py + end, color);
            } else {
                ctx.fill(px + outer, py + row, px + Math.max(outer, inner), py + end, color);
                ctx.fill(px + pw - Math.max(outer, inner), py + row, px + pw - outer, py + end, color);
            }
            row = end;
        }
        ctx.getMatrices().popMatrix();
    }

    /** Soft drop shadow made of a few translucent, growing rounded rectangles. */
    public static void shadow(DrawContext ctx, int x, int y, int w, int h, int r, float alpha) {
        for (int i = 4; i >= 1; i--) {
            int a = Math.round(alpha * 28 / i);
            roundRect(ctx, x - i, y - i + 2, w + 2 * i, h + 2 * i, r + i, (a << 24));
        }
    }

    /** Single real-pixel horizontal line (separator). */
    public static void hairline(DrawContext ctx, int x1, int x2, int y, int color) {
        int s = scale();
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().scale(1f / s, 1f / s);
        ctx.fill(x1 * s, y * s, x2 * s, y * s + 1, color);
        ctx.getMatrices().popMatrix();
    }

    /** Text without shadow (crisper on dark surfaces). */
    public static void text(DrawContext ctx, String s, int x, int y, int color) {
        ctx.drawText(font(), s, x, y, color, false);
    }

    public static void textCentered(DrawContext ctx, String s, int cx, int y, int color) {
        ctx.drawText(font(), s, cx - font().getWidth(s) / 2, y, color, false);
    }

    /** Trims {@code s} to {@code maxW} pixels, ending it with an ellipsis if it had to be cut. */
    public static String ellipsize(String s, int maxW) {
        TextRenderer f = font();
        if (f.getWidth(s) <= maxW) {
            return s;
        }
        String dots = "…";
        int avail = Math.max(0, maxW - f.getWidth(dots));
        return f.trimToWidth(s, avail) + dots;
    }

    private static long lastFrame;
    private static float frameK = 0.2f;

    /** Call once per frame (KCScreen does) so {@link #approach} animates at the same speed at any frame rate. */
    public static void beginFrame() {
        long now = net.minecraft.util.Util.getMeasuringTimeMs();
        long dt = lastFrame == 0 ? 16 : Math.min(100, now - lastFrame);
        lastFrame = now;
        frameK = (float) (1 - Math.exp(-dt / 55.0));
    }

    /** Per-frame interpolation factor (0..1) for custom smoothing. */
    public static float smoothing() {
        return frameK;
    }

    /** Exponential approach used for hover / selection animations (about 55 ms time constant). */
    public static float approach(float current, float target) {
        float k = frameK;
        float next = current + (target - current) * k;
        return Math.abs(next - target) < 0.002f ? target : next;
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    /** Accent colour at full or reduced alpha. */
    public static int accent(float alpha) {
        return Theme.withAlpha(Theme.ACCENT, alpha);
    }
}
