package com.kcvisuals.gui.packs;

import com.kcvisuals.gui.Theme;
import com.kcvisuals.gui.widget.Draw;
import com.kcvisuals.gui.widget.ScrollPanel;
import com.kcvisuals.pack.Category;
import com.kcvisuals.pack.PackIndex;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.gui.DrawContext;

/** Left column of the Packs tab: categories grouped by {@code group}, each with the number of packs offering it. */
public class CategoryList extends ScrollPanel {
    static final int HEADER_H = 16;
    static final int ROW_H = 17;

    /** A row is either a group header ({@code category == null}) or a category. */
    private record Row(String group, Category category, int y, int h) {}

    private final PackSession session;
    private final Consumer<Category> onSelect;
    private final List<Row> rows = new ArrayList<>();
    private final Map<String, Float> hover = new HashMap<>();
    private Map<String, Integer> counts = Map.of();
    private int builtRevision = -1;
    private Category selected;
    private int height;
    private float selY = -1;

    public CategoryList(PackSession session, Consumer<Category> onSelect) {
        super(0, 0, 0, 0);
        this.session = session;
        this.onSelect = onSelect;
        rebuild();
    }

    public Category selected() {
        return selected;
    }

    public void select(Category c) {
        selected = c;
        for (Row r : rows) {
            if (r.category() == c) {
                ensureVisible(r.y() - 2, r.h() + 4);
            }
        }
        if (onSelect != null) {
            onSelect.accept(c);
        }
    }

    /** Moves the selection by {@code delta} categories (keyboard navigation). */
    public void move(int delta) {
        List<Category> cats = session.registry().all();
        int i = Math.max(0, cats.indexOf(selected));
        select(cats.get(Math.floorMod(i + delta, cats.size())));
    }

    private void rebuild() {
        rows.clear();
        Map<String, List<Category>> groups = new LinkedHashMap<>();
        for (Category c : session.registry().all()) {
            groups.computeIfAbsent(c.group(), g -> new ArrayList<>()).add(c);
        }
        int cy = 2;
        boolean firstGroup = true;
        for (var g : groups.entrySet()) {
            if (!firstGroup) {
                cy += 4;
            }
            firstGroup = false;
            rows.add(new Row(g.getKey(), null, cy, HEADER_H));
            cy += HEADER_H;
            for (Category c : g.getValue()) {
                rows.add(new Row(g.getKey(), c, cy, ROW_H));
                cy += ROW_H + 1;
            }
        }
        height = cy + 2;
        if (selected == null && !session.registry().all().isEmpty()) {
            selected = session.registry().all().get(0);
        }
        PackIndex idx = session.index();
        Map<String, Integer> m = new HashMap<>();
        if (idx != null) {
            for (PackIndex.PackInfo info : idx.packs().values()) {
                for (String cat : info.categories()) {
                    m.merge(cat, 1, Integer::sum);
                }
            }
        }
        counts = m;
        builtRevision = session.revision();
    }

    @Override
    protected int contentHeight() {
        return height;
    }

    @Override
    protected void renderContent(DrawContext ctx, int mx, int my, float delta, int top, boolean inside) {
        if (builtRevision != session.revision()) {
            rebuild();
        }
        boolean loaded = session.index() != null;
        // animated selection highlight
        for (Row r : rows) {
            if (r.category() == selected) {
                selY = selY < 0 ? r.y() : selY + (r.y() - selY) * Draw.smoothing() * 1.6f;
                if (Math.abs(selY - r.y()) < 0.5f) {
                    selY = r.y();
                }
            }
        }
        if (selected != null && selY >= 0) {
            int sy = top + Math.round(selY);
            Draw.roundRect(ctx, x + 2, sy, w - 9, ROW_H, 4, Theme.withAlpha(Theme.ACCENT, 0.20f));
            Draw.roundRect(ctx, x + 2, sy + 4, 2, ROW_H - 8, 1, Theme.ACCENT);
        }
        for (Row r : rows) {
            int ry = top + r.y();
            if (ry + r.h() < y || ry > y + h) {
                continue;
            }
            if (r.category() == null) {
                Draw.text(ctx, r.group().toUpperCase(java.util.Locale.GERMAN), x + 6, ry + 6, Theme.TEXT_FAINT);
                continue;
            }
            Category c = r.category();
            boolean sel = c == selected;
            boolean over = inside && Draw.inside(mx, my, x + 2, ry, w - 9, r.h());
            float hv = Draw.approach(hover.getOrDefault(c.id(), 0f), over && !sel ? 1f : 0f);
            hover.put(c.id(), hv);
            if (hv > 0.01f) {
                Draw.roundRect(ctx, x + 2, ry, w - 9, r.h(), 4, Theme.withAlpha(Theme.SURFACE_HOVER, hv));
            }
            int count = counts.getOrDefault(c.id(), 0);
            String cnt = loaded ? String.valueOf(count) : "…";
            int cw = Draw.font().getWidth(cnt);
            int right = x + w - 12;
            boolean picked = session.selection().packFor(c.id()) != null;
            int nameMax = right - cw - 12 - (x + 10) - (picked ? 6 : 0);
            int color = sel ? 0xFFFFFFFF : (count == 0 && loaded ? Theme.TEXT_FAINT : Theme.mix(Theme.TEXT_DIM, Theme.TEXT, hv));
            Draw.text(ctx, Draw.ellipsize(c.displayName(), nameMax), x + 10, ry + (r.h() - 8) / 2 + 1, color);
            // count pill
            int pillW = cw + 8;
            int px = right - pillW;
            Draw.roundRect(ctx, px, ry + 3, pillW, r.h() - 6, 5,
                    sel ? Theme.withAlpha(Theme.ACCENT, 0.45f) : Theme.withAlpha(Theme.BORDER, 0.9f));
            Draw.text(ctx, cnt, px + 4, ry + (r.h() - 8) / 2 + 1, sel ? 0xFFFFFFFF : Theme.TEXT_DIM);
            if (picked) {
                Draw.roundRect(ctx, px - 7, ry + r.h() / 2 - 2, 4, 4, 2, Theme.ACCENT);
            }
        }
    }

    @Override
    protected boolean contentClicked(double mx, double cy, int button) {
        if (button != 0) {
            return false;
        }
        for (Row r : rows) {
            if (r.category() != null && cy >= r.y() && cy < r.y() + r.h()) {
                if (r.category() != selected) {
                    net.minecraft.client.MinecraftClient.getInstance().getSoundManager().play(
                            net.minecraft.client.sound.PositionedSoundInstance.ui(
                                    net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.35f));
                }
                select(r.category());
                return true;
            }
        }
        return false;
    }
}
