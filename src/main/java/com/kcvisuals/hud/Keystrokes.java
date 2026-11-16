package com.kcvisuals.hud;

import com.kcvisuals.gui.Theme;
import com.kcvisuals.gui.widget.Draw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

/** WASD plus left/right mouse button with clicks per second. */
public final class Keystrokes extends HudElement {
    private static final int KEY = 22;
    private static final int GAP = 2;
    private static final int W = KEY * 3 + GAP * 2;
    private static final int H = KEY * 3 + GAP * 2 + 2;

    private static final CpsCounter LMB = new CpsCounter();
    private static final CpsCounter RMB = new CpsCounter();
    private static boolean lmbDown, rmbDown;
    private static boolean seeded;
    private final boolean[] down = new boolean[6];
    private int lc, rc;
    private boolean preview;

    /** pressed-animation state per key: W A S D LMB RMB */
    private final float[] anim = new float[6];

    public Keystrokes() {
        super("keystrokes", "Tastenanzeige", "WASD, Maustasten und CPS");
    }

    /** Called once per rendered frame; detects mouse button presses by their rising edge. */
    public static void poll() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null) {
            seeded = false;
            return;
        }
        long handle = mc.getWindow().getHandle();
        long now = Util.getMeasuringTimeMs();
        boolean l = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean r = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        if (!seeded) {
            // first poll after a screen closed: adopt the current state so a held button is not counted as a click
            seeded = true;
            lmbDown = l;
            rmbDown = r;
            return;
        }
        if (l && !lmbDown) {
            LMB.click(now);
        }
        if (r && !rmbDown) {
            RMB.click(now);
        }
        lmbDown = l;
        rmbDown = r;
    }

    @Override
    public void prepare(boolean preview) {
        this.preview = preview;
        GameOptions o = mc().options;
        long now = Util.getMeasuringTimeMs();
        down[0] = preview || o.forwardKey.isPressed();
        down[1] = o.leftKey.isPressed();
        down[2] = o.backKey.isPressed();
        down[3] = o.rightKey.isPressed();
        down[4] = lmbDown || preview;
        down[5] = rmbDown;
        lc = preview ? 8 : LMB.cps(now);
        rc = preview ? 0 : RMB.cps(now);
    }

    @Override
    public int width(boolean preview) {
        return W;
    }

    @Override
    public int height(boolean preview) {
        return H;
    }

    @Override
    public void render(DrawContext ctx, int x, int y, boolean preview) {
        key(ctx, 0, x + KEY + GAP, y, KEY, KEY, "W", null, down[0]);
        int row2 = y + KEY + GAP;
        key(ctx, 1, x, row2, KEY, KEY, "A", null, down[1]);
        key(ctx, 2, x + KEY + GAP, row2, KEY, KEY, "S", null, down[2]);
        key(ctx, 3, x + 2 * (KEY + GAP), row2, KEY, KEY, "D", null, down[3]);
        int row3 = row2 + KEY + GAP;
        int mw = (W - GAP) / 2;
        key(ctx, 4, x, row3, mw, KEY + 2, "LMB", lc + " CPS", down[4]);
        key(ctx, 5, x + W - mw, row3, mw, KEY + 2, "RMB", rc + " CPS", down[5]);
    }

    private void key(DrawContext ctx, int i, int x, int y, int w, int h, String label, String sub, boolean down) {
        anim[i] = Draw.approach(anim[i], down ? 1f : 0f);
        HudStyle.panel(ctx, x, y, w, h);
        if (anim[i] > 0.01f) {
            Draw.roundRect(ctx, x, y, w, h, 4, Theme.withAlpha(Theme.ACCENT, 0.55f * anim[i]));
        }
        int color = Theme.mix(Theme.TEXT_DIM, 0xFFFFFFFF, anim[i]);
        int lw = Draw.font().getWidth(label);
        if (sub == null) {
            Draw.text(ctx, label, x + (w - lw) / 2, y + (h - 8) / 2 + 1, color);
        } else {
            Draw.text(ctx, label, x + (w - lw) / 2, y + 4, color);
            Draw.text(ctx, sub, x + (w - Draw.font().getWidth(sub)) / 2, y + 13, Theme.mix(Theme.TEXT_FAINT, Theme.TEXT, anim[i]));
        }
    }
}
