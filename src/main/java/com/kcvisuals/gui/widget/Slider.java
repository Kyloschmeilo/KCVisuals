package com.kcvisuals.gui.widget;

import com.kcvisuals.gui.Theme;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import net.minecraft.client.gui.DrawContext;

/** Horizontal slider: label + formatted value on top, track below. Snaps to {@code step} when step > 0. */
public class Slider extends Widget {
    private final String label;
    private final double min, max, step;
    private double value;
    private boolean dragging;
    private DoubleFunction<String> format = v -> String.format(java.util.Locale.ROOT, "%.2f", v);
    private final DoubleConsumer onChange;

    public Slider(int x, int y, int w, int h, String label, double min, double max, double step, double value,
            DoubleConsumer onChange) {
        super(x, y, w, h);
        this.label = label;
        this.min = min;
        this.max = max;
        this.step = step;
        this.value = snap(value, min, max, step);
        this.onChange = onChange;
    }

    public Slider format(DoubleFunction<String> format) {
        this.format = format;
        return this;
    }

    public double value() {
        return value;
    }

    public void setValue(double v) {
        value = snap(v, min, max, step);
    }

    /** Clamps into [min, max] and rounds to the nearest multiple of {@code step} from {@code min}. */
    public static double snap(double v, double min, double max, double step) {
        double c = Math.max(min, Math.min(max, v));
        if (step > 0) {
            c = min + Math.round((c - min) / step) * step;
            c = Math.max(min, Math.min(max, c));
        }
        return c;
    }

    private int trackX() {
        return x + 6;
    }

    private int trackW() {
        return w - 12;
    }

    private int trackY() {
        return y + h - 7;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        if (!visible) {
            return;
        }
        updateHover(mx, my);
        Draw.text(ctx, label, x + 6, y + 2, enabled ? Theme.TEXT : Theme.TEXT_FAINT);
        String v = format.apply(value);
        Draw.text(ctx, v, x + w - 6 - Draw.font().getWidth(v), y + 2, Theme.TEXT_DIM);
        double frac = max > min ? (value - min) / (max - min) : 0;
        int tx = trackX(), tw = trackW(), ty = trackY();
        Draw.roundRect(ctx, tx, ty, tw, 3, 1, Theme.BORDER);
        int fillW = (int) Math.round(tw * frac);
        Draw.roundRect(ctx, tx, ty, Math.max(2, fillW), 3, 1, enabled ? Theme.ACCENT : Theme.TEXT_FAINT);
        int knob = 7 + Math.round(hoverAnim + (dragging ? 1 : 0));
        Draw.roundRect(ctx, tx + fillW - knob / 2, ty + 1 - knob / 2, knob, knob, knob / 2, 0xFFFFFFFF);
    }

    private void setFromMouse(double mx) {
        double frac = (mx - trackX()) / Math.max(1, trackW());
        double nv = snap(min + frac * (max - min), min, max, step);
        if (nv != value) {
            value = nv;
            if (onChange != null) {
                onChange.accept(value);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && enabled && isMouseOver(mx, my)) {
            dragging = true;
            setFromMouse(mx);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            setFromMouse(mx);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean was = dragging;
        dragging = false;
        return was;
    }
}
