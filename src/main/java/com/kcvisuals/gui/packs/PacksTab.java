package com.kcvisuals.gui.packs;

import com.kcvisuals.gui.TabPanel;
import com.kcvisuals.gui.Theme;
import com.kcvisuals.gui.widget.Button;
import com.kcvisuals.gui.widget.Draw;
import com.kcvisuals.gui.widget.TextField;
import com.kcvisuals.pack.Category;
import com.kcvisuals.pack.PackIndex;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

/**
 * Packs tab: search + actions on top, categories on the left, pack tiles for the selected category on the right.
 * Clicking a tile only changes the in-memory selection; "Anwenden" saves it to the active profile and applies it.
 */
public class PacksTab implements TabPanel {
    private static final int BAR_H = 20;

    private final PackSession session;
    private final TextField search;
    private final Button apply, reset, rescan;
    private final CategoryList categories;
    private final PackGrid grid;
    private int x, y, w, h;
    private int cardX, cardY, cardW, cardH;
    private int gridCardX, gridCardY, gridCardW, gridCardH;

    public PacksTab(PackSession session) {
        this.session = session;
        this.grid = new PackGrid(session);
        this.categories = new CategoryList(session, grid::setCategory);
        grid.setCategory(categories.selected());
        this.search = new TextField(0, 0, 0, BAR_H, "Packs durchsuchen…").searchIcon(true).onChange(grid::setQuery);
        this.apply = new Button(0, 0, 0, BAR_H, "Anwenden", Button.Style.PRIMARY, session::apply);
        this.reset = new Button(0, 0, 0, BAR_H, "Zurücksetzen", Button.Style.SECONDARY, session::resetSelection)
                .tooltip("Alle Kategorien auf Vanilla (erst mit „Anwenden“ aktiv)");
        this.rescan = new Button(0, 0, 0, BAR_H, "Packs neu einlesen", Button.Style.SECONDARY, session::rescan);
    }

    public CategoryList categories() {
        return categories;
    }

    public PackGrid grid() {
        return grid;
    }

    @Override
    public void init(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        int aw = Button.widthFor("Anwenden") + 8;
        int rw = Button.widthFor("Zurücksetzen");
        int sw = Button.widthFor("Packs neu einlesen");
        int right = x + w;
        apply.setBounds(right - aw, y, aw, BAR_H);
        reset.setBounds(apply.x - 4 - rw, y, rw, BAR_H);
        rescan.setBounds(reset.x - 4 - sw, y, sw, BAR_H);
        int searchW = Math.max(60, rescan.x - 8 - x);
        search.setBounds(x, y, Math.min(searchW, 180), BAR_H);
        if (searchW < 100) {
            rescan.label("Neu einlesen");
        }

        int bodyY = y + BAR_H + 8;
        int bodyH = h - BAR_H - 8;
        int catW = Math.max(112, Math.min(150, w * 28 / 100));
        cardX = x;
        cardY = bodyY;
        cardW = catW;
        cardH = bodyH;
        categories.setBounds(cardX + 2, cardY + 4, cardW - 4, cardH - 8);
        gridCardX = x + catW + 8;
        gridCardY = bodyY;
        gridCardW = w - catW - 8;
        gridCardH = bodyH;
        grid.setBounds(gridCardX + 6, gridCardY + 26, gridCardW - 10, gridCardH - 30);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        session.ensureLoaded();
        apply.enabled = session.index() != null && !session.isApplying();
        apply.label(session.isApplying() ? "Wird angewendet…" : "Anwenden");
        rescan.enabled = !session.isLoading();
        reset.enabled = session.index() != null && !session.selection().isEmpty();

        search.render(ctx, mx, my, delta);
        rescan.render(ctx, mx, my, delta);
        reset.render(ctx, mx, my, delta);
        apply.render(ctx, mx, my, delta);
        if (session.isDirty() && !session.isApplying()) {
            // unsaved-changes dot on the apply button
            Draw.roundRect(ctx, apply.x + apply.w - 5, apply.y - 2, 6, 6, 3, Theme.WARNING);
        }

        Draw.roundRect(ctx, cardX, cardY, cardW, cardH, 6, Theme.withAlpha(Theme.SURFACE, 0.55f));
        Draw.roundRect(ctx, gridCardX, gridCardY, gridCardW, gridCardH, 6, Theme.withAlpha(Theme.SURFACE, 0.55f));

        PackIndex idx = session.index();
        if (idx == null) {
            drawLoading(ctx);
            return;
        }
        categories.render(ctx, mx, my, delta);
        drawGridHeader(ctx);
        if (idx.packs().isEmpty()) {
            int cx = gridCardX + gridCardW / 2;
            int cy = gridCardY + gridCardH / 2 - 10;
            Draw.textCentered(ctx, "Keine Packs gefunden", cx, cy, Theme.TEXT);
            Draw.textCentered(ctx, Draw.ellipsize("Lege .zip-Packs in kcvisuals/packs ab", gridCardW - 12), cx, cy + 12,
                    Theme.TEXT_DIM);
        }
        grid.render(ctx, mx, my, delta);
        if (session.loadError() != null) {
            Draw.text(ctx, session.loadError(), gridCardX + 8, gridCardY + gridCardH - 12, Theme.DANGER);
        }
    }

    private void drawGridHeader(DrawContext ctx) {
        Category c = categories.selected();
        if (c == null) {
            return;
        }
        int hx = gridCardX + 10;
        int hy = gridCardY + 9;
        ctx.drawText(Draw.font(), c.displayName(), hx, hy, Theme.TEXT, false);
        int nameW = Draw.font().getWidth(c.displayName());
        int n = grid.matchingPacks();
        String count = n == 1 ? "1 Pack" : n + " Packs";
        Draw.text(ctx, count, hx + nameW + 8, hy, Theme.TEXT_FAINT);
        String pick = session.selection().packFor(c.id());
        String pickName = "Vanilla";
        if (pick != null && session.index() != null) {
            var info = session.index().packs().get(pick);
            pickName = info == null ? pick : PackNames.clean(info.displayName());
        }
        int right = gridCardX + gridCardW - 10;
        int avail = right - (hx + nameW + 8 + Draw.font().getWidth(count) + 12);
        String label = Draw.ellipsize(pickName, Math.max(20, avail - 8));
        int lw = Draw.font().getWidth(label);
        int lx = right - lw;
        Draw.roundRect(ctx, lx - 6, hy - 4, lw + 12, 15, 7, Theme.withAlpha(Theme.ACCENT, pick != null ? 0.28f : 0.10f));
        Draw.text(ctx, label, lx, hy, pick != null ? 0xFFFFFFFF : Theme.TEXT_DIM);
        Draw.hairline(ctx, gridCardX + 8, gridCardX + gridCardW - 8, gridCardY + 22, Theme.BORDER);
    }

    private void drawLoading(DrawContext ctx) {
        int cx = gridCardX + gridCardW / 2;
        int cy = gridCardY + gridCardH / 2;
        long t = Util.getMeasuringTimeMs();
        for (int i = 0; i < 3; i++) {
            float phase = (float) Math.sin(t / 160.0 - i * 0.8);
            int size = 4 + Math.round(Math.max(0, phase) * 2);
            Draw.roundRect(ctx, cx - 14 + i * 12 - size / 2, cy - 12 - size / 2, size, size, size / 2,
                    Theme.withAlpha(Theme.ACCENT, 0.4f + 0.6f * Math.max(0, phase)));
        }
        Draw.textCentered(ctx, "Packs werden eingelesen…", cx, cy, Theme.TEXT_DIM);
        // skeleton rows in the category column
        for (int i = 0; i < 8; i++) {
            float pulse = 0.35f + 0.25f * (float) Math.sin(t / 220.0 - i * 0.5);
            Draw.roundRect(ctx, cardX + 8, cardY + 10 + i * 18, cardW - 40 - (i % 3) * 10, 9, 4,
                    Theme.withAlpha(Theme.SURFACE_HOVER, pulse));
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (search.mouseClicked(mx, my, button)) {
            return true;
        }
        for (Button b : List.of(apply, reset, rescan)) {
            if (b.mouseClicked(mx, my, button)) {
                return true;
            }
        }
        if (session.index() == null) {
            return false;
        }
        return categories.mouseClicked(mx, my, button) || grid.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return categories.mouseReleased(mx, my, button) | grid.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return categories.mouseDragged(mx, my, button, dx, dy) || grid.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        return categories.mouseScrolled(mx, my, amount) || grid.mouseScrolled(mx, my, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (search.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl && keyCode == GLFW.GLFW_KEY_F) {
            search.setFocused(true);
            return true;
        }
        if (session.index() != null && (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN)) {
            categories.move(keyCode == GLFW.GLFW_KEY_UP ? -1 : 1);
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return search.charTyped(chr, modifiers);
    }
}
