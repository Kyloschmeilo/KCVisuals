package com.kcvisuals.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** One draggable HUD widget. Positions live in {@link HudLayout}; elements only know their size and how to draw. */
public abstract class HudElement {
    private final String id;
    private final String name;
    private final String description;
    private boolean enabled;

    protected HudElement(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    protected static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }

    /** Computes this frame's state once; visible/width/height/render only read it. Called before every frame. */
    public void prepare(boolean preview) {}

    /** Drops references to world objects (called on disconnect). */
    public void reset() {}

    /**
     * Whether there is anything to draw right now. In {@code preview} (HUD editor) every element is shown with
     * sample data so it can be positioned.
     */
    public boolean visible(boolean preview) {
        return true;
    }

    /** Width in scaled pixels; only called when {@link #visible} is true. */
    public abstract int width(boolean preview);

    public abstract int height(boolean preview);

    public abstract void render(DrawContext ctx, int x, int y, boolean preview);
}
