package com.kcvisuals.gui.hud;

import com.kcvisuals.gui.TabPanel;
import com.kcvisuals.gui.Theme;
import com.kcvisuals.gui.widget.Button;
import com.kcvisuals.gui.widget.ColorPicker;
import com.kcvisuals.gui.widget.Draw;
import com.kcvisuals.gui.widget.ScrollPanel;
import com.kcvisuals.gui.widget.Toggle;
import com.kcvisuals.hud.HudElement;
import com.kcvisuals.hud.HudManager;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

/** HUD tab: edit button, one switch card per element and the shared card background (colour + alpha). */
public class HudTab implements TabPanel {
    private static final int CARD_H = 30;
    private static final int GAP = 6;
    private static final int ROW_H = 22;
    private static final int SWITCH_W = 44;
    private static final int EDIT_H = 26;

    private final Screen kcParent;
    private final HudManager hud = HudManager.get();
    private final Button edit;
    private final Map<HudElement, Toggle> switches = new IdentityHashMap<>();
    private final List<HudElement> order = new ArrayList<>();
    private final ColorPicker background;
    private final Toggle grid;
    private final Body body = new Body();
    private int x, y, w, h;

    /** @param kcParent the screen that was open before the KC menu; handed back when the editor closes. */
    public HudTab(Screen kcParent) {
        this.kcParent = kcParent;
        edit = new Button(0, 0, 0, EDIT_H, "HUD bearbeiten", Button.Style.PRIMARY, () -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.setScreen(new HudEditorScreen(this.kcParent));
        });
        edit.tooltip("Elemente per Drag & Drop verschieben");
        for (HudElement e : hud.elements()) {
            order.add(e);
            switches.put(e, new Toggle(0, 0, SWITCH_W, CARD_H, "", e.enabled(), e::setEnabled));
        }
        background = new ColorPicker(0, 0, 0, ROW_H, "Hintergrundfarbe", hud.background(), hud::setBackground);
        grid = new Toggle(0, 0, 0, ROW_H, "Am Raster einrasten", hud.gridSnap(), hud::setGridSnap);
    }

    @Override
    public void init(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        body.setBounds(x, y, w, h);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        body.render(ctx, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return body.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean any = body.mouseReleased(mx, my, button);
        any |= background.mouseReleased(mx, my, button);
        return any;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return body.mouseDragged(mx, my, button, dx, dy) || background.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        return body.mouseScrolled(mx, my, amount);
    }

    private final class Body extends ScrollPanel {
        Body() {
            super(0, 0, 0, 0);
        }

        private int bgRowH() {
            return ROW_H + (background.isExpanded() ? ColorPicker.EXPANDED_H + 4 : 0);
        }

        @Override
        protected int contentHeight() {
            return EDIT_H + GAP + 12 + order.size() * (CARD_H + GAP) + 12 + ROW_H + 2 + bgRowH() + 4;
        }

        private int cw() {
            return w - 8;
        }

        private int sectionY1(int top) {
            return top + EDIT_H + GAP;
        }

        private int cardsY(int top) {
            return sectionY1(top) + 12;
        }

        private int sectionY2(int top) {
            return cardsY(top) + order.size() * (CARD_H + GAP);
        }

        private void layout(int top) {
            edit.setBounds(x, top, cw(), EDIT_H);
            int cy = cardsY(top);
            for (HudElement e : order) {
                switches.get(e).setBounds(x + cw() - SWITCH_W - 4, cy, SWITCH_W, CARD_H);
                cy += CARD_H + GAP;
            }
            int ry = sectionY2(top) + 12;
            grid.setBounds(x + 4, ry, cw() - 8, ROW_H);
            background.setBounds(x + 4, ry + ROW_H + 2, cw() - 8, ROW_H);
        }

        @Override
        protected void renderContent(DrawContext ctx, int mx, int my, float delta, int top, boolean inside) {
            layout(top);
            edit.render(ctx, mx, my, delta);
            Draw.text(ctx, "ELEMENTE", x + 2, sectionY1(top) + 2, Theme.TEXT_FAINT);
            int cy = cardsY(top);
            for (HudElement e : order) {
                Toggle t = switches.get(e);
                t.setValue(e.enabled());
                Draw.roundRect(ctx, x, cy, cw(), CARD_H, 6, Theme.withAlpha(Theme.SURFACE, 0.75f));
                if (e.enabled()) {
                    Draw.roundRect(ctx, x, cy + 6, 2, CARD_H - 12, 1, Theme.ACCENT);
                }
                int textRight = x + cw() - SWITCH_W - 10;
                Draw.text(ctx, Draw.ellipsize(e.name(), textRight - x - 14), x + 14, cy + 6, Theme.TEXT);
                Draw.text(ctx, Draw.ellipsize(e.description(), textRight - x - 14), x + 14, cy + 17, Theme.TEXT_DIM);
                t.render(ctx, mx, my, delta);
                cy += CARD_H + GAP;
            }
            Draw.text(ctx, "DARSTELLUNG", x + 2, sectionY2(top) + 2, Theme.TEXT_FAINT);
            Draw.roundRect(ctx, x, sectionY2(top) + 10, cw(), ROW_H + 2 + bgRowH() + 2, 6,
                    Theme.withAlpha(Theme.SURFACE, 0.75f));
            grid.render(ctx, mx, my, delta);
            background.render(ctx, mx, my, delta);
        }

        @Override
        protected boolean contentClicked(double mx, double cy, int button) {
            double my = cy + y - scroll();
            layout(y - scroll());
            if (edit.mouseClicked(mx, my, button)) {
                return true;
            }
            for (HudElement e : order) {
                if (switches.get(e).mouseClicked(mx, my, button)) {
                    return true;
                }
            }
            return grid.mouseClicked(mx, my, button) || background.mouseClicked(mx, my, button);
        }
    }
}
