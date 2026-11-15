package com.kcvisuals.gui.widget;

import com.kcvisuals.gui.Theme;
import net.minecraft.client.gui.DrawContext;

/**
 * Clipped, smoothly scrolling viewport. Subclasses draw their content with {@link #renderContent} at
 * {@code y - scroll()} and receive clicks in content coordinates via {@link #contentClicked}.
 */
public abstract class ScrollPanel extends Widget {
    private static final int BAR_W = 3;
    private static final double STEP = 24;

    private double target;
    private double scroll;
    private boolean draggingBar;
    private double dragOffset;
    private float barHover;

    protected ScrollPanel(int x, int y, int w, int h) {
        super(x, y, w, h);
    }

    /** Total height of the content in GUI pixels. */
    protected abstract int contentHeight();

    /** Draws the content; {@code top} is the y of content row 0 (already offset by the scroll). */
    protected abstract void renderContent(DrawContext ctx, int mx, int my, float delta, int top, boolean mouseInside);

    /** A click inside the viewport; {@code cy} is relative to the content origin. */
    protected boolean contentClicked(double mx, double cy, int button) {
        return false;
    }

    public int scroll() {
        return (int) Math.round(scroll);
    }

    public void scrollTo(double y) {
        target = clamp(y, contentHeight(), h);
    }

    public void scrollToTop() {
        target = scroll = 0;
    }

    /** Scrolls the minimum amount so that rows [cy, cy + ch) are visible. */
    public void ensureVisible(int cy, int ch) {
        if (cy < target) {
            target = cy;
        } else if (cy + ch > target + h) {
            target = cy + ch - h;
        }
        target = clamp(target, contentHeight(), h);
    }

    public static double maxScroll(int contentHeight, int viewHeight) {
        return Math.max(0, contentHeight - viewHeight);
    }

    public static double clamp(double value, int contentHeight, int viewHeight) {
        return Math.max(0, Math.min(value, maxScroll(contentHeight, viewHeight)));
    }

    /** Scrollbar thumb {top offset, height} within a track of {@code viewHeight}. */
    public static int[] thumb(double scroll, int contentHeight, int viewHeight) {
        if (contentHeight <= viewHeight || viewHeight <= 0) {
            return new int[] {0, viewHeight};
        }
        int th = Math.max(16, (int) ((long) viewHeight * viewHeight / contentHeight));
        th = Math.min(th, viewHeight);
        double max = maxScroll(contentHeight, viewHeight);
        int top = (int) Math.round((viewHeight - th) * (scroll / max));
        return new int[] {top, th};
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        if (!visible) {
            return;
        }
        int ch = contentHeight();
        target = clamp(target, ch, h);
        scroll += (target - scroll) * Draw.smoothing();
        if (Math.abs(target - scroll) < 0.3) {
            scroll = target;
        }
        boolean inside = isMouseOver(mx, my);
        ctx.enableScissor(x, y, x + w, y + h);
        renderContent(ctx, mx, my, delta, y - scroll(), inside);
        ctx.disableScissor();

        if (ch > h) {
            int[] t = thumb(scroll, ch, h);
            int bx = x + w - BAR_W - 1;
            boolean overBar = Draw.inside(mx, my, bx - 3, y, BAR_W + 4, h);
            barHover = Draw.approach(barHover, overBar || draggingBar ? 1f : (inside ? 0.5f : 0.2f));
            Draw.roundRect(ctx, bx, y + t[0], BAR_W, t[1], 2,
                    Theme.withAlpha(Theme.mix(Theme.TEXT_FAINT, Theme.ACCENT, barHover), 0.35f + 0.65f * barHover));
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!isMouseOver(mx, my)) {
            return false;
        }
        int ch = contentHeight();
        if (button == 0 && ch > h && mx >= x + w - BAR_W - 4) {
            int[] t = thumb(scroll, ch, h);
            double rel = my - y;
            if (rel < t[0] || rel > t[0] + t[1]) {
                // jump so the thumb centres on the cursor
                double frac = (rel - t[1] / 2.0) / Math.max(1, h - t[1]);
                target = scroll = clamp(frac * maxScroll(ch, h), ch, h);
                t = thumb(scroll, ch, h);
            }
            draggingBar = true;
            dragOffset = rel - t[0];
            return true;
        }
        return contentClicked(mx, my - y + scroll, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!draggingBar) {
            return false;
        }
        int ch = contentHeight();
        int[] t = thumb(scroll, ch, h);
        double frac = (my - y - dragOffset) / Math.max(1, h - t[1]);
        target = scroll = clamp(frac * maxScroll(ch, h), ch, h);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean was = draggingBar;
        draggingBar = false;
        return was;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!isMouseOver(mx, my) || contentHeight() <= h) {
            return false;
        }
        target = clamp(target - amount * STEP, contentHeight(), h);
        return true;
    }
}
