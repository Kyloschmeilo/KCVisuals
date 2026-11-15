package com.kcvisuals.gui.widget;

import com.kcvisuals.gui.Theme;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

/** Single-line text input with placeholder, caret, horizontal scrolling and clipboard paste. */
public class TextField extends Widget {
    private String text = "";
    private String placeholder = "";
    private int caret;
    private int scrollPx;
    private int maxLength = 64;
    private boolean focused;
    private float focusAnim;
    private Consumer<String> onChange;
    private Runnable onEnter;
    private boolean searchIcon;

    public TextField(int x, int y, int w, int h, String placeholder) {
        super(x, y, w, h);
        this.placeholder = placeholder;
    }

    public TextField onChange(Consumer<String> onChange) {
        this.onChange = onChange;
        return this;
    }

    public TextField onEnter(Runnable onEnter) {
        this.onEnter = onEnter;
        return this;
    }

    public TextField maxLength(int maxLength) {
        this.maxLength = maxLength;
        return this;
    }

    /** Draws a small magnifier glyph on the left (for search fields). */
    public TextField searchIcon(boolean on) {
        this.searchIcon = on;
        return this;
    }

    public String text() {
        return text;
    }

    public void setText(String s) {
        String n = s == null ? "" : (s.length() > maxLength ? s.substring(0, maxLength) : s);
        boolean changed = !n.equals(text);
        text = n;
        caret = Math.min(caret, text.length());
        if (changed && onChange != null) {
            onChange.accept(text);
        }
    }

    public boolean isFocused() {
        return focused;
    }

    public void setFocused(boolean f) {
        focused = f;
        if (f) {
            caret = text.length();
        }
    }

    private int textLeft() {
        return x + (searchIcon ? 16 : 6);
    }

    private int innerWidth() {
        return w - (textLeft() - x) - 6;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        if (!visible) {
            return;
        }
        updateHover(mx, my);
        focusAnim = Draw.approach(focusAnim, focused ? 1f : 0f);
        Draw.roundRect(ctx, x, y, w, h, 4, Theme.mix(Theme.SURFACE, Theme.SURFACE_HOVER, hoverAnim * 0.6f));
        int border = Theme.mix(Theme.mix(Theme.BORDER, Theme.TEXT_FAINT, hoverAnim * 0.5f), Theme.ACCENT, focusAnim);
        Draw.roundBorderPx(ctx, x, y, w, h, 4, 1, border);

        int ty = y + (h - 8) / 2 + 1;
        if (searchIcon) {
            drawMagnifier(ctx, x + 6, y + (h - 8) / 2, Theme.mix(Theme.TEXT_FAINT, Theme.ACCENT, focusAnim));
        }
        int left = textLeft();
        int iw = innerWidth();
        if (text.isEmpty()) {
            if (!focused || placeholder.isEmpty()) {
                Draw.text(ctx, Draw.ellipsize(placeholder, iw), left, ty, Theme.TEXT_FAINT);
            } else {
                Draw.text(ctx, Draw.ellipsize(placeholder, iw), left, ty, Theme.withAlpha(Theme.TEXT_FAINT, 0.6f));
            }
        }
        int caretPx = Draw.font().getWidth(text.substring(0, caret));
        if (caretPx - scrollPx > iw) {
            scrollPx = caretPx - iw;
        } else if (caretPx < scrollPx) {
            scrollPx = caretPx;
        }
        scrollPx = Math.max(0, Math.min(scrollPx, Math.max(0, Draw.font().getWidth(text) - iw)));
        ctx.enableScissor(left, y + 1, left + iw + 1, y + h - 1);
        Draw.text(ctx, text, left - scrollPx, ty, Theme.TEXT);
        if (focused && (Util.getMeasuringTimeMs() / 500) % 2 == 0) {
            int cx = left - scrollPx + caretPx;
            ctx.fill(cx, ty - 1, cx + 1, ty + 9, Theme.ACCENT);
        }
        ctx.disableScissor();
    }

    private static void drawMagnifier(DrawContext ctx, int x, int y, int c) {
        // 7x7 ring + handle, in GUI pixels
        ctx.fill(x + 1, y, x + 5, y + 1, c);
        ctx.fill(x + 1, y + 5, x + 5, y + 6, c);
        ctx.fill(x, y + 1, x + 1, y + 5, c);
        ctx.fill(x + 5, y + 1, x + 6, y + 5, c);
        ctx.fill(x + 5, y + 5, x + 6, y + 6, c);
        ctx.fill(x + 6, y + 6, x + 7, y + 7, c);
        ctx.fill(x + 7, y + 7, x + 8, y + 8, c);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        boolean over = isMouseOver(mx, my);
        if (!over) {
            focused = false;
            return false;
        }
        focused = true;
        if (button == 1) {
            setText("");
            caret = 0;
            return true;
        }
        int rel = (int) mx - textLeft() + scrollPx;
        int best = text.length();
        for (int i = 0; i <= text.length(); i++) {
            if (Draw.font().getWidth(text.substring(0, i)) >= rel) {
                best = i;
                break;
            }
        }
        caret = best;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!focused) {
            return false;
        }
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE -> focused = false;
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (onEnter != null) {
                    onEnter.run();
                }
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (caret > 0) {
                    int from = ctrl ? wordStart(caret) : caret - 1;
                    String n = text.substring(0, from) + text.substring(caret);
                    caret = from;
                    setText(n);
                }
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (caret < text.length()) {
                    setText(text.substring(0, caret) + text.substring(caret + 1));
                }
            }
            case GLFW.GLFW_KEY_LEFT -> caret = Math.max(0, ctrl ? wordStart(caret) : caret - 1);
            case GLFW.GLFW_KEY_RIGHT -> caret = Math.min(text.length(), caret + 1);
            case GLFW.GLFW_KEY_HOME -> caret = 0;
            case GLFW.GLFW_KEY_END -> caret = text.length();
            case GLFW.GLFW_KEY_V -> {
                if (!ctrl) {
                    return true;
                }
                String clip = MinecraftClient.getInstance().keyboard.getClipboard();
                insert(clip.replaceAll("[\\r\\n\\t]", " "));
            }
            case GLFW.GLFW_KEY_A -> {
                if (ctrl) {
                    caret = text.length();
                }
            }
            default -> {
                return true; // swallow everything else while typing (e.g. inventory key)
            }
        }
        return true;
    }

    private int wordStart(int from) {
        int i = from - 1;
        while (i > 0 && text.charAt(i - 1) == ' ') {
            i--;
        }
        while (i > 0 && text.charAt(i - 1) != ' ') {
            i--;
        }
        return Math.max(0, i);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!focused || chr < 32 || chr == 127 || chr == '§') {
            return false;
        }
        insert(String.valueOf(chr));
        return true;
    }

    private void insert(String s) {
        int room = maxLength - text.length();
        if (room <= 0 || s.isEmpty()) {
            return;
        }
        if (s.length() > room) {
            s = s.substring(0, room);
        }
        String n = text.substring(0, caret) + s + text.substring(caret);
        caret += s.length();
        setText(n);
    }
}
