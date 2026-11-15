package com.kcvisuals.gui.packs;

import com.kcvisuals.gui.Theme;
import com.kcvisuals.gui.widget.Draw;
import com.kcvisuals.gui.widget.ScrollPanel;
import com.kcvisuals.pack.Category;
import com.kcvisuals.pack.PackIndex;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/** Grid of 48x48 pack tiles for one category: Vanilla first, then every pack that contains the category. */
public class PackGrid extends ScrollPanel {
    static final int TILE = 48;
    static final int PREVIEW = 32;
    static final int LABEL_H = 12;
    static final int GAP = 6;

    /** One tile; {@code packId} is {@link PreviewCache#VANILLA} for the Vanilla tile. */
    record Tile(String packId, String name) {}

    private final PackSession session;
    private final List<Tile> tiles = new ArrayList<>();
    private final Map<String, Float> hover = new HashMap<>();
    private final Map<String, Float> selAnim = new HashMap<>();
    private Category category;
    private String query = "";
    private int builtRevision = -1;
    private Category builtCategory;
    private String builtQuery;
    private int matchingPacks;

    public PackGrid(PackSession session) {
        super(0, 0, 0, 0);
        this.session = session;
    }

    public void setCategory(Category c) {
        if (c != category) {
            category = c;
            scrollToTop();
            selAnim.clear();
        }
    }

    public void setQuery(String q) {
        query = q == null ? "" : q;
        scrollToTop();
    }

    /** Packs (excluding Vanilla) that contain the category and match the search. */
    public int matchingPacks() {
        rebuildIfNeeded();
        return matchingPacks;
    }

    private GridLayout layout() {
        return GridLayout.fit(w - 8, TILE, TILE + LABEL_H, GAP, GAP);
    }

    private void rebuildIfNeeded() {
        if (builtRevision == session.revision() && builtCategory == category && query.equals(builtQuery)) {
            return;
        }
        tiles.clear();
        matchingPacks = 0;
        PackIndex idx = session.index();
        if (category != null && idx != null) {
            tiles.add(new Tile(PreviewCache.VANILLA, "Vanilla"));
            for (PackIndex.PackInfo info : idx.packsFor(category.id())) {
                String name = PackNames.clean(info.displayName());
                if (PackNames.matches(name, query)) {
                    tiles.add(new Tile(info.id(), name));
                    matchingPacks++;
                }
            }
        }
        builtRevision = session.revision();
        builtCategory = category;
        builtQuery = query;
    }

    @Override
    protected int contentHeight() {
        rebuildIfNeeded();
        return layout().contentHeight(tiles.size()) + 8;
    }

    private String selectedPack() {
        String p = category == null ? null : session.selection().packFor(category.id());
        return p == null ? PreviewCache.VANILLA : p;
    }

    @Override
    protected void renderContent(DrawContext ctx, int mx, int my, float delta, int top, boolean inside) {
        rebuildIfNeeded();
        if (category == null) {
            return;
        }
        GridLayout g = layout();
        int ox = x + 4;
        int oy = top + 4;
        String selected = selectedPack();
        Tile hovered = null;
        for (int i = 0; i < tiles.size(); i++) {
            Tile t = tiles.get(i);
            int tx = ox + g.cellX(i);
            int ty = oy + g.cellY(i);
            if (ty + g.cellH() < y || ty > y + h) {
                continue;
            }
            boolean sel = t.packId().equals(selected);
            boolean over = inside && Draw.inside(mx, my, tx, ty, TILE, g.cellH());
            if (over) {
                hovered = t;
            }
            float hv = Draw.approach(hover.getOrDefault(t.packId(), 0f), over ? 1f : 0f);
            hover.put(t.packId(), hv);
            float sv = Draw.approach(selAnim.getOrDefault(t.packId(), sel ? 1f : 0f), sel ? 1f : 0f);
            selAnim.put(t.packId(), sv);
            drawTile(ctx, t, tx, ty, hv, sv);
        }
        if (tiles.size() == 1) {
            String msg = query.isBlank() ? "Kein Pack enthält diese Kategorie" : "Keine Treffer für „" + query + "“";
            int ty = oy + g.cellH() + 14;
            Draw.textCentered(ctx, Draw.ellipsize(msg, w - 16), x + w / 2, ty, Theme.TEXT_FAINT);
        }
        if (hovered != null && Draw.font().getWidth(hovered.name()) > TILE + GAP) {
            ctx.drawTooltip(Draw.font(), Text.literal(hovered.name()), mx, my);
        }
    }

    private void drawTile(DrawContext ctx, Tile t, int tx, int ty, float hv, float sv) {
        int lift = Math.round(hv * 1.5f);
        int by = ty - lift;
        if (sv > 0.01f) {
            // soft accent glow behind the selected tile
            Draw.roundRect(ctx, tx - 2, by - 2, TILE + 4, TILE + 4, 8, Theme.withAlpha(Theme.ACCENT, 0.22f * sv));
        }
        int bg = Theme.mix(Theme.SURFACE, Theme.SURFACE_HOVER, hv);
        bg = Theme.mix(bg, Theme.mix(Theme.SURFACE, Theme.ACCENT, 0.18f), sv);
        Draw.roundRect(ctx, tx, by, TILE, TILE, 6, bg);
        if (sv > 0.01f) {
            Draw.roundBorder(ctx, tx, by, TILE, TILE, 6, 1, Theme.withAlpha(Theme.ACCENT, sv));
        } else {
            Draw.roundBorderPx(ctx, tx, by, TILE, TILE, 6, 1, Theme.mix(Theme.BORDER, Theme.TEXT_FAINT, hv));
        }

        PreviewCache.Preview p = session.previews().preview(t.packId(), category);
        int cx = tx + TILE / 2, cy = by + TILE / 2;
        if (p != null) {
            int[] size = fit(p.width(), p.height(), PREVIEW + Math.round(hv * 2));
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, p.id(), cx - size[0] / 2, cy - size[1] / 2, 0, 0,
                    size[0], size[1], p.width(), p.height(), p.width(), p.height());
        } else if (session.previews().isLoading(t.packId(), category)) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(Util.getMeasuringTimeMs() / 180.0);
            Draw.roundRect(ctx, cx - PREVIEW / 2 + 4, cy - PREVIEW / 2 + 4, PREVIEW - 8, PREVIEW - 8, 4,
                    Theme.withAlpha(Theme.SURFACE_HOVER, 0.5f + 0.5f * pulse));
        } else {
            // The pack changes this category without a texture (models only): show the Vanilla image dimmed.
            PreviewCache.Preview v = session.previews().preview(PreviewCache.VANILLA, category);
            if (v != null) {
                int[] size = fit(v.width(), v.height(), PREVIEW);
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, v.id(), cx - size[0] / 2, cy - size[1] / 2, 0, 0,
                        size[0], size[1], v.width(), v.height(), v.width(), v.height(), 0x59FFFFFF);
            }
            Draw.roundRect(ctx, tx + 4, by + TILE - 12, TILE - 8, 9, 4, Theme.withAlpha(Theme.BORDER, 0.95f));
            Draw.textCentered(ctx, "Modell", cx, by + TILE - 11, Theme.TEXT_DIM);
        }
        if (sv > 0.01f) {
            // check badge, top right
            int bx = tx + TILE - 11, bYy = by + 3;
            Draw.roundRect(ctx, bx, bYy, 8, 8, 4, Theme.withAlpha(Theme.ACCENT, sv));
            int c = Theme.withAlpha(0xFFFFFFFF, sv);
            ctx.fill(bx + 2, bYy + 4, bx + 3, bYy + 5, c);
            ctx.fill(bx + 3, bYy + 5, bx + 4, bYy + 6, c);
            ctx.fill(bx + 4, bYy + 4, bx + 5, bYy + 5, c);
            ctx.fill(bx + 5, bYy + 3, bx + 6, bYy + 4, c);
        }
        String label = Draw.ellipsize(t.name(), TILE + GAP - 2);
        int color = Theme.mix(Theme.mix(Theme.TEXT_DIM, Theme.TEXT, hv), 0xFFFFFFFF, sv);
        Draw.textCentered(ctx, label, tx + TILE / 2, ty + TILE + 3, color);
    }

    /** Scales (w, h) so that the longer side equals {@code box}, keeping the aspect ratio. */
    static int[] fit(int w, int h, int box) {
        if (w <= 0 || h <= 0) {
            return new int[] {box, box};
        }
        if (w >= h) {
            return new int[] {box, Math.max(1, Math.round(box * h / (float) w))};
        }
        return new int[] {Math.max(1, Math.round(box * w / (float) h)), box};
    }

    @Override
    protected boolean contentClicked(double mx, double cy, int button) {
        if (button != 0 || category == null) {
            return false;
        }
        rebuildIfNeeded();
        GridLayout g = layout();
        int idx = g.indexAt(mx - x - 4, cy - 4, tiles.size());
        if (idx < 0) {
            return false;
        }
        Tile t = tiles.get(idx);
        String pack = PreviewCache.VANILLA.equals(t.packId()) ? null : t.packId();
        session.select(category.id(), pack);
        MinecraftClient.getInstance().getSoundManager()
                .play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.4f));
        return true;
    }

    /** Selects the tile at {@code index} programmatically (used by keyboard shortcuts and dev checks). */
    public boolean selectIndex(int index) {
        rebuildIfNeeded();
        if (category == null || index < 0 || index >= tiles.size()) {
            return false;
        }
        Tile t = tiles.get(index);
        session.select(category.id(), PreviewCache.VANILLA.equals(t.packId()) ? null : t.packId());
        return true;
    }
}
