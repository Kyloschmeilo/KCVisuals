package com.kcvisuals.gui;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

public class KCScreen extends Screen {
    public enum Tab {
        PACKS("Packs"), VISUALS("Visuals"), HUD("HUD"), PROFILES("Profile");

        public final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private static final long FADE_MS = 150;
    private static final int HEADER_H = 40;
    private static final int TAB_W = 90;
    private static final int TAB_H = 20;

    private final Screen parent;
    private final Map<Tab, TabPanel> panels = new EnumMap<>(Tab.class);
    private Tab active = Tab.PACKS;
    private long openedAt;
    private int panelX, panelY, panelW, panelH;
    private int tabsX, tabsY;

    public KCScreen(Screen parent) {
        super(Text.literal("KC Visuals"));
        this.parent = parent;
    }

    public void registerTab(Tab tab, TabPanel panel) {
        panels.put(tab, panel);
        if (width > 0) {
            panel.init(panelX + 8, panelY + HEADER_H + 8, panelW - 16, panelH - HEADER_H - 16);
        }
    }

    public Tab activeTab() {
        return active;
    }

    public void setActiveTab(Tab tab) {
        active = tab;
    }

    @Override
    protected void init() {
        openedAt = Util.getMeasuringTimeMs();
        panelW = Math.min(width - 20, 420);
        panelH = Math.min(height - 20, 260);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        tabsX = panelX + 8;
        tabsY = panelY + HEADER_H - TAB_H;
        for (TabPanel p : panels.values()) {
            p.init(panelX + 8, panelY + HEADER_H + 8, panelW - 16, panelH - HEADER_H - 16);
        }
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Own background is drawn in render(); skip the vanilla blur.
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float fade = Math.min(1f, (Util.getMeasuringTimeMs() - openedAt) / (float) FADE_MS);
        ctx.fill(0, 0, width, height, Theme.withAlpha(Theme.BG, fade));
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, Theme.withAlpha(Theme.PANEL, fade));
        ctx.drawText(textRenderer, "KC Visuals", panelX + 8, panelY + 6, Theme.withAlpha(Theme.TEXT, fade), true);

        for (Tab t : Tab.values()) {
            int x = tabsX + t.ordinal() * TAB_W;
            boolean sel = t == active;
            int color = sel ? Theme.withAlpha(Theme.ACCENT, fade) : Theme.withAlpha(Theme.TEXT, fade * 0.7f);
            int tw = textRenderer.getWidth(t.label);
            ctx.drawText(textRenderer, t.label, x + (TAB_W - tw) / 2, tabsY + 6, color, true);
            if (sel) {
                ctx.fill(x + 4, tabsY + TAB_H - 2, x + TAB_W - 4, tabsY + TAB_H, color);
            }
        }

        TabPanel p = panels.get(active);
        if (p != null) {
            p.render(ctx, mouseX, mouseY, delta);
        } else {
            String empty = "Noch keine Inhalte";
            ctx.drawText(textRenderer, empty, panelX + (panelW - textRenderer.getWidth(empty)) / 2,
                    panelY + HEADER_H + 40, Theme.withAlpha(Theme.TEXT, fade * 0.5f), true);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x();
        double my = click.y();
        if (click.button() == 0 && my >= tabsY && my < tabsY + TAB_H && mx >= tabsX) {
            int idx = (int) ((mx - tabsX) / TAB_W);
            if (idx >= 0 && idx < Tab.values().length) {
                active = Tab.values()[idx];
                return true;
            }
        }
        TabPanel p = panels.get(active);
        if (p != null && p.mouseClicked(mx, my, click.button())) {
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
