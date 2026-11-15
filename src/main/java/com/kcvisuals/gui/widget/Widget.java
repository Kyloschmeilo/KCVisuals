package com.kcvisuals.gui.widget;

import net.minecraft.client.gui.DrawContext;

/** Minimal retained widget: position, visibility, and the input callbacks a {@code TabPanel} forwards. */
public abstract class Widget {
    public int x, y, w, h;
    public boolean visible = true;
    public boolean enabled = true;
    /** Hover animation state, 0..1. */
    protected float hoverAnim;

    protected Widget(int x, int y, int w, int h) {
        setBounds(x, y, w, h);
    }

    public Widget setBounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        return this;
    }

    public boolean isMouseOver(double mx, double my) {
        return visible && Draw.inside(mx, my, x, y, w, h);
    }

    /** Updates {@link #hoverAnim}; subclasses call this at the start of {@link #render}. */
    protected boolean updateHover(int mx, int my) {
        boolean hovered = enabled && isMouseOver(mx, my);
        hoverAnim = Draw.approach(hoverAnim, hovered ? 1f : 0f);
        return hovered;
    }

    public abstract void render(DrawContext ctx, int mx, int my, float delta);

    public boolean mouseClicked(double mx, double my, int button) {
        return false;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        return false;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        return false;
    }
}
