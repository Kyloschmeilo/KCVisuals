package com.kcvisuals.hud;

import com.kcvisuals.gui.Theme;
import com.kcvisuals.gui.widget.Draw;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.StringHelper;

/** Active status effects with icon, name + level and remaining time. */
public final class PotionHud extends HudElement {
    private static final int ROW_H = 22;
    private static final int PAD = 4;
    private static final int ICON = 16;
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    public PotionHud() {
        super("potions", "Statuseffekte", "Effekte mit Symbol und Restzeit");
    }

    private final List<StatusEffectInstance> list = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();
    private final List<String> times = new ArrayList<>();
    private int width = 60;

    @Override
    public void prepare(boolean preview) {
        fill(preview);
        labels.clear();
        times.clear();
        int max = 40;
        for (StatusEffectInstance e : list) {
            String l = label(e), t = time(e);
            labels.add(l);
            times.add(t);
            max = Math.max(max, Math.max(Draw.font().getWidth(l), Draw.font().getWidth(t)));
        }
        width = PAD + ICON + 5 + max + PAD + 2;
    }

    @Override
    public void reset() {
        list.clear();
        labels.clear();
        times.clear();
    }

    private void fill(boolean preview) {
        list.clear();
        if (preview || mc().player == null) {
            list.add(new StatusEffectInstance(StatusEffects.SPEED, 20 * 92, 1));
            list.add(new StatusEffectInstance(StatusEffects.STRENGTH, 20 * 45, 0));
            list.add(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 20 * 8, 0));
        } else {
            list.addAll(mc().player.getStatusEffects());
            list.sort(Comparator.comparingInt(e -> e.isInfinite() ? Integer.MAX_VALUE : e.getDuration()));
        }
    }

    private static String label(StatusEffectInstance e) {
        String n = e.getEffectType().value().getName().getString();
        int lvl = e.getAmplifier() + 1;
        if (lvl > 1) {
            n += " " + (lvl < ROMAN.length ? ROMAN[lvl] : String.valueOf(lvl));
        }
        return n;
    }

    private static String time(StatusEffectInstance e) {
        return e.isInfinite() ? "**:**" : StringHelper.formatTicks(e.getDuration(), 20f);
    }

    @Override
    public boolean visible(boolean preview) {
        return !list.isEmpty();
    }

    @Override
    public int width(boolean preview) {
        return width;
    }

    @Override
    public int height(boolean preview) {
        return list.size() * ROW_H + 2;
    }

    @Override
    public void render(DrawContext ctx, int x, int y, boolean preview) {
        int w = width;
        HudStyle.panel(ctx, x, y, w, list.size() * ROW_H + 2);
        int ry = y + 1;
        for (int i = 0; i < list.size(); i++) {
            StatusEffectInstance e = list.get(i);
            ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, InGameHud.getEffectTexture(e.getEffectType()),
                    x + PAD, ry + (ROW_H - ICON) / 2, ICON, ICON);
            boolean low = !e.isInfinite() && e.getDuration() < 20 * 10;
            Draw.text(ctx, labels.get(i), x + PAD + ICON + 5, ry + 4, Theme.TEXT);
            Draw.text(ctx, times.get(i), x + PAD + ICON + 5, ry + 13, low ? Theme.WARNING : Theme.TEXT_DIM);
            ry += ROW_H;
        }
    }
}
