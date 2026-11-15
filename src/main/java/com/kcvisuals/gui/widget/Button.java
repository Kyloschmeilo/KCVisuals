package com.kcvisuals.gui.widget;

import com.kcvisuals.gui.Theme;
import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

/** Rounded push button with hover/press animation. */
public class Button extends Widget {
    public enum Style { PRIMARY, SECONDARY, DANGER, GHOST }

    private Supplier<String> label;
    private Runnable onClick;
    public Style style;
    private String tooltip;

    public Button(int x, int y, int w, int h, String label, Style style, Runnable onClick) {
        super(x, y, w, h);
        this.label = () -> label;
        this.style = style;
        this.onClick = onClick;
    }

    public Button label(Supplier<String> label) {
        this.label = label;
        return this;
    }

    public Button label(String label) {
        this.label = () -> label;
        return this;
    }

    public String label() {
        return label.get();
    }

    public Button tooltip(String tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    public void onClick(Runnable onClick) {
        this.onClick = onClick;
    }

    /** Width that fits {@code label} with comfortable padding. */
    public static int widthFor(String label) {
        return Draw.font().getWidth(label) + 16;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        if (!visible) {
            return;
        }
        boolean hovered = updateHover(mx, my);
        int base, hover, text;
        switch (style) {
            case PRIMARY -> {
                base = Theme.ACCENT;
                hover = Theme.mix(Theme.ACCENT, 0xFFFFFFFF, 0.15f);
                text = 0xFFFFFFFF;
            }
            case DANGER -> {
                base = Theme.withAlpha(Theme.DANGER, 0.85f);
                hover = Theme.DANGER;
                text = 0xFFFFFFFF;
            }
            case GHOST -> {
                base = 0x00000000;
                hover = Theme.SURFACE_HOVER;
                text = Theme.TEXT_DIM;
            }
            default -> {
                base = Theme.SURFACE;
                hover = Theme.SURFACE_HOVER;
                text = Theme.TEXT;
            }
        }
        int bg = Theme.mix(base, hover, hoverAnim);
        if (!enabled) {
            bg = Theme.withAlpha(Theme.SURFACE, 0.6f);
            text = Theme.TEXT_FAINT;
        }
        Draw.roundRect(ctx, x, y, w, h, 4, bg);
        if (style == Style.SECONDARY && enabled) {
            Draw.roundBorderPx(ctx, x, y, w, h, 4, 1, Theme.mix(Theme.BORDER, Theme.ACCENT, hoverAnim * 0.6f));
        }
        if (style == Style.GHOST && enabled) {
            text = Theme.mix(Theme.TEXT_DIM, Theme.TEXT, hoverAnim);
        }
        String s = Draw.ellipsize(label.get(), w - 6);
        Draw.textCentered(ctx, s, x + w / 2, y + (h - 8) / 2 + 1, text);
        if (hovered && tooltip != null) {
            ctx.drawTooltip(Draw.font(), net.minecraft.text.Text.literal(tooltip), mx, my);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && enabled && isMouseOver(mx, my)) {
            MinecraftClient.getInstance().getSoundManager()
                    .play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1f));
            if (onClick != null) {
                onClick.run();
            }
            return true;
        }
        return false;
    }
}
