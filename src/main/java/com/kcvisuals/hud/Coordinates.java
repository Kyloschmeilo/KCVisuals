package com.kcvisuals.hud;

import com.kcvisuals.gui.Theme;
import com.kcvisuals.gui.widget.Draw;
import java.util.Locale;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;

/** XYZ of the player plus the facing direction. */
public final class Coordinates extends HudElement {
    private static final int H = 26;

    public Coordinates() {
        super("coords", "Koordinaten", "XYZ und Blickrichtung");
    }

    private String xyz() {
        PlayerEntity p = mc().player;
        if (p == null) {
            return "X 128,5   Y 64,0   Z -312,2";
        }
        return String.format(Locale.GERMAN, "X %.1f   Y %.1f   Z %.1f", p.getX(), p.getY(), p.getZ());
    }

    private String facing() {
        PlayerEntity p = mc().player;
        if (p == null) {
            return "Blick: Norden (-Z)";
        }
        String dir = switch (p.getHorizontalFacing()) {
            case NORTH -> "Norden (-Z)";
            case SOUTH -> "Süden (+Z)";
            case EAST -> "Osten (+X)";
            case WEST -> "Westen (-X)";
            default -> "";
        };
        return "Blick: " + dir;
    }

    @Override
    public int width(boolean preview) {
        return 10 + Math.max(Draw.font().getWidth(xyz()), Draw.font().getWidth(facing())) + 6;
    }

    @Override
    public int height(boolean preview) {
        return H;
    }

    @Override
    public void render(DrawContext ctx, int x, int y, boolean preview) {
        HudStyle.panel(ctx, x, y, width(preview), H);
        HudStyle.accentBar(ctx, x, y, H);
        Draw.text(ctx, xyz(), x + 10, y + 4, Theme.TEXT);
        Draw.text(ctx, facing(), x + 10, y + 14, Theme.TEXT_DIM);
    }
}
