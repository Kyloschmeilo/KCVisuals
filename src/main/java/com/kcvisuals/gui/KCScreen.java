package com.kcvisuals.gui;

import com.kcvisuals.KCVisuals;
import com.kcvisuals.gui.packs.PackSession;
import com.kcvisuals.gui.packs.PacksTab;
import com.kcvisuals.gui.profiles.ProfilesTab;
import com.kcvisuals.gui.widget.Draw;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
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

    private static final long FADE_MS = 160;
    private static final int HEADER_H = 32;
    private static final int TAB_H = 18;
    private static final int TAB_PAD = 12;
    private static final int PAD = 10;

    private final Screen parent;
    private final Map<Tab, TabPanel> panels = new EnumMap<>(Tab.class);
    private final PackSession packSession;
    private Tab active = Tab.PACKS;
    private long openedAt;
    private int panelX, panelY, panelW, panelH;
    private final int[] tabX = new int[Tab.values().length];
    private final int[] tabW = new int[Tab.values().length];
    private int tabsY;
    private float pillX = -1, pillW;
    private final float[] tabHover = new float[Tab.values().length];

    public KCScreen(Screen parent) {
        super(Text.literal("KC Visuals"));
        this.parent = parent;
        this.packSession = new PackSession(KCVisuals.pathsOrDefault());
        registerTab(Tab.PACKS, new PacksTab(packSession));
        registerTab(Tab.VISUALS, new com.kcvisuals.gui.visuals.VisualsTab(KCVisuals.modules()));
        registerTab(Tab.HUD, new com.kcvisuals.gui.hud.HudTab(parent));
        registerTab(Tab.PROFILES, new ProfilesTab(packSession));
    }

    /** Pack index, selection and active profile shared by the pack-related tabs. */
    public PackSession packSession() {
        return packSession;
    }

    public TabPanel panel(Tab tab) {
        return panels.get(tab);
    }

    public void registerTab(Tab tab, TabPanel panel) {
        panels.put(tab, panel);
        if (width > 0) {
            panel.init(contentX(), contentY(), contentW(), contentH());
        }
    }

    public Tab activeTab() {
        return active;
    }

    public void setActiveTab(Tab tab) {
        active = tab;
    }

    private int contentX() {
        return panelX + PAD;
    }

    private int contentY() {
        return panelY + HEADER_H + 8;
    }

    private int contentW() {
        return panelW - 2 * PAD;
    }

    private int contentH() {
        return panelH - HEADER_H - 8 - PAD;
    }

    @Override
    protected void init() {
        openedAt = Util.getMeasuringTimeMs();
        panelW = Math.min(width - 16, 600);
        panelH = Math.min(height - 16, 360);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        tabsY = panelY + (HEADER_H - TAB_H) / 2 + 1;
        int right = panelX + panelW - PAD;
        for (int i = Tab.values().length - 1; i >= 0; i--) {
            int tw = textRenderer.getWidth(Tab.values()[i].label) + 2 * TAB_PAD;
            right -= tw;
            tabX[i] = right;
            tabW[i] = tw;
            right -= 2;
        }
        pillX = -1;
        for (TabPanel p : panels.values()) {
            p.init(contentX(), contentY(), contentW(), contentH());
        }
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Own background is drawn in render(); skip the vanilla blur.
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Draw.beginFrame();
        float fade = Math.min(1f, (Util.getMeasuringTimeMs() - openedAt) / (float) FADE_MS);
        float ease = 1 - (1 - fade) * (1 - fade);
        ctx.fill(0, 0, width, height, Theme.withAlpha(Theme.BG, ease));

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(0, (1 - ease) * 8);
        Draw.shadow(ctx, panelX, panelY, panelW, panelH, 10, ease);
        Draw.roundRect(ctx, panelX, panelY, panelW, panelH, 10, Theme.withAlpha(Theme.PANEL, ease));
        Draw.roundBorderPx(ctx, panelX, panelY, panelW, panelH, 10, 1, Theme.withAlpha(Theme.BORDER, ease));

        // brand
        int bx = panelX + PAD, by = panelY + (HEADER_H - 12) / 2;
        Draw.roundRect(ctx, bx, by, 12, 12, 4, Theme.withAlpha(Theme.ACCENT, ease));
        Draw.roundRect(ctx, bx + 3, by + 3, 6, 6, 2, Theme.withAlpha(0xFFFFFFFF, ease * 0.9f));
        ctx.drawText(textRenderer, "KC", bx + 18, by + 2, Theme.withAlpha(Theme.ACCENT, ease), false);
        ctx.drawText(textRenderer, "Visuals", bx + 18 + textRenderer.getWidth("KC "), by + 2,
                Theme.withAlpha(Theme.TEXT, ease), false);

        // tabs: segmented control with a sliding pill
        int segX = tabX[0] - 3, segW = tabX[tabX.length - 1] + tabW[tabW.length - 1] - tabX[0] + 6;
        Draw.roundRect(ctx, segX, tabsY - 3, segW, TAB_H + 6, 8, Theme.withAlpha(Theme.SURFACE, ease));
        int ai = active.ordinal();
        if (pillX < 0) {
            pillX = tabX[ai];
            pillW = tabW[ai];
        } else {
            float k = Draw.smoothing() * 1.4f;
            pillX += (tabX[ai] - pillX) * Math.min(1, k);
            pillW += (tabW[ai] - pillW) * Math.min(1, k);
        }
        Draw.roundRect(ctx, Math.round(pillX), tabsY, Math.round(pillW), TAB_H, 6, Theme.withAlpha(Theme.ACCENT, ease));
        for (Tab t : Tab.values()) {
            int i = t.ordinal();
            boolean over = Draw.inside(mouseX, mouseY, tabX[i], tabsY, tabW[i], TAB_H);
            tabHover[i] = Draw.approach(tabHover[i], over && t != active ? 1f : 0f);
            if (tabHover[i] > 0.01f) {
                Draw.roundRect(ctx, tabX[i], tabsY, tabW[i], TAB_H, 6, Theme.withAlpha(Theme.SURFACE_HOVER, tabHover[i] * ease));
            }
            int color = t == active ? 0xFFFFFFFF : Theme.mix(Theme.TEXT_DIM, Theme.TEXT, tabHover[i]);
            int tw = textRenderer.getWidth(t.label);
            ctx.drawText(textRenderer, t.label, tabX[i] + (tabW[i] - tw) / 2, tabsY + 5, Theme.withAlpha(color, ease), false);
        }
        Draw.hairline(ctx, panelX + 1, panelX + panelW - 1, panelY + HEADER_H, Theme.withAlpha(Theme.BORDER, ease));

        TabPanel p = panels.get(active);
        if (p != null) {
            p.render(ctx, mouseX, mouseY, delta);
        } else {
            String empty = "Noch keine Inhalte";
            Draw.textCentered(ctx, empty, panelX + panelW / 2, panelY + panelH / 2, Theme.withAlpha(Theme.TEXT_FAINT, ease));
        }
        ctx.getMatrices().popMatrix();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x();
        double my = click.y();
        if (click.button() == 0) {
            for (Tab t : Tab.values()) {
                int i = t.ordinal();
                if (Draw.inside(mx, my, tabX[i], tabsY, tabW[i], TAB_H)) {
                    active = t;
                    return true;
                }
            }
        }
        TabPanel p = panels.get(active);
        if (p != null && p.mouseClicked(mx, my, click.button())) {
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        TabPanel p = panels.get(active);
        if (p != null && p.mouseReleased(click.x(), click.y(), click.button())) {
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        TabPanel p = panels.get(active);
        if (p != null && p.mouseDragged(click.x(), click.y(), click.button(), dx, dy)) {
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        TabPanel p = panels.get(active);
        if (p != null && p.mouseScrolled(mx, my, vertical)) {
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        TabPanel p = panels.get(active);
        if (p != null && p.keyPressed(input.key(), input.scancode(), input.modifiers())) {
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        TabPanel p = panels.get(active);
        if (p != null) {
            boolean any = false;
            for (char c : Character.toChars(input.codepoint())) {
                any |= p.charTyped(c, input.modifiers());
            }
            if (any) {
                return true;
            }
        }
        return super.charTyped(input);
    }

    @Override
    public void removed() {
        for (TabPanel p : panels.values()) {
            p.close();
        }
        packSession.close();
        KCVisuals.saveConfig();
        super.removed();
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
