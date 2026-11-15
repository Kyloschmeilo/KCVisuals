package com.kcvisuals.hud;

/** Integer rectangle in scaled GUI pixels. */
public record Rect(int x, int y, int w, int h) {
    public boolean contains(double px, double py) {
        return px >= x && py >= y && px < x + w && py < y + h;
    }
}
