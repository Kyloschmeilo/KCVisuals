package com.kcvisuals.gui;

import net.minecraft.client.gui.DrawContext;

public interface TabPanel {
    void init(int x, int y, int w, int h);

    void render(DrawContext ctx, int mouseX, int mouseY, float delta);

    default boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return false;
    }

    default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    default boolean charTyped(char chr, int modifiers) {
        return false;
    }
}
