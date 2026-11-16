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

/**
 * Grid of 48x48 pack tiles for one category (double width for the end crystal: placed and item view): Vanilla first,
 * then every pack that contains the category. Item models are shown as 3D renders that turn while hovered.
 */
public class PackGrid extends ScrollPanel {
    static final int TILE = 48;
    static final int PREVIEW = 32;
    static final int LABEL_H = 12;
    static final int GAP = 6;
    /** Preview box of each half of a double-width (end crystal) tile. */
    static final int DUAL_PREVIEW = 30;
    /** Hover turn speed: about 12 frames per second. */
    static final long FRAME_MS = 83;

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
    /** Tile whose turn animation is shown (pack id + category id), and when its first frame appeared. */
    private String hoverKey;
    private long stripStart = -1;

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
        return GridLayout.forCategory(w - 8, category == null ? null : category.id(), TILE, TILE + LABEL_H, GAP);
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
        int hoveredIdx = inside ? g.indexAt(mx - ox, my - oy, tiles.size()) : -1;
        Tile hovered = hoveredIdx < 0 ? null : tiles.get(hoveredIdx);
        String hoverKey = hovered == null ? null : hovered.packId() + '\u0000' + category.id();
        if (!java.util.Objects.equals(hoverKey, this.hoverKey)) {
            // a different tile (or none): drop the old turn animation, the new one starts at its first frame
            session.previews().clearHover();
            this.hoverKey = hoverKey;
            stripStart = -1;
        }
        for (int i = 0; i < tiles.size(); i++) {
            Tile t = tiles.get(i);
            int tx = ox + g.cellX(i);
            int ty = oy + g.cellY(i);
            if (ty + g.cellH() < y || ty > y + h) {
                continue;
            }
            boolean sel = t.packId().equals(selected);
            boolean over = i == hoveredIdx;
            float hv = Draw.approach(hover.getOrDefault(t.packId(), 0f), over ? 1f : 0f);
            hover.put(t.packId(), hv);
            float sv = Draw.approach(selAnim.getOrDefault(t.packId(), sel ? 1f : 0f), sel ? 1f : 0f);
            selAnim.put(t.packId(), sv);
            drawTile(ctx, t, tx, ty, g.cellW(), hv, sv, over);
        }
        if (tiles.size() == 1) {
            String msg = query.isBlank() ? "Kein Pack enthält diese Kategorie" : "Keine Treffer für „" + query + "“";
            int ty = oy + g.cellH() + 14;
            Draw.textCentered(ctx, Draw.ellipsize(msg, w - 16), x + w / 2, ty, Theme.TEXT_FAINT);
        }
        if (hovered != null && Draw.font().getWidth(hovered.name()) > g.cellW() + GAP) {
            ctx.drawTooltip(Draw.font(), Text.literal(hovered.name()), mx, my);
        }
    }

    private void drawTile(DrawContext ctx, Tile t, int tx, int ty, int tw, float hv, float sv, boolean over) {
        int lift = Math.round(hv * 1.5f);
        int by = ty - lift;
        if (sv > 0.01f) {
            // soft accent glow behind the selected tile
            Draw.roundRect(ctx, tx - 2, by - 2, tw + 4, TILE + 4, 8, Theme.withAlpha(Theme.ACCENT, 0.22f * sv));
        }
        int bg = Theme.mix(Theme.SURFACE, Theme.SURFACE_HOVER, hv);
        bg = Theme.mix(bg, Theme.mix(Theme.SURFACE, Theme.ACCENT, 0.18f), sv);
        Draw.roundRect(ctx, tx, by, tw, TILE, 6, bg);
        if (sv > 0.01f) {
            Draw.roundBorder(ctx, tx, by, tw, TILE, 6, 1, Theme.withAlpha(Theme.ACCENT, sv));
        } else {
            Draw.roundBorderPx(ctx, tx, by, tw, TILE, 6, 1, Theme.mix(Theme.BORDER, Theme.TEXT_FAINT, hv));
        }

        if (GridLayout.isDoubleWidth(category.id())) {
            // end crystal: placed entity on the left, inventory item on the right, tiny captions below
            int half = tw / 2;
            int cy = by + 19;
            int size = DUAL_PREVIEW + Math.round(hv * 2);
            drawView(ctx, t, PreviewCache.View.PLACED, tx + half / 2, cy, size, over);
            drawView(ctx, t, PreviewCache.View.ITEM, tx + half + half / 2, cy, size, over);
            ctx.fill(tx + half, by + 8, tx + half + 1, by + TILE - 8, Theme.withAlpha(Theme.BORDER, 0.8f));
            caption(ctx, "Platziert", tx + half / 2, by + TILE - 9);
            caption(ctx, "Item", tx + half + half / 2, by + TILE - 9);
        } else {
            drawView(ctx, t, PreviewCache.View.ITEM, tx + tw / 2, by + TILE / 2, PREVIEW + Math.round(hv * 2), over);
        }

        if (sv > 0.01f) {
            // check badge, top right
            int bx = tx + tw - 11, bYy = by + 3;
            Draw.roundRect(ctx, bx, bYy, 8, 8, 4, Theme.withAlpha(Theme.ACCENT, sv));
            int c = Theme.withAlpha(0xFFFFFFFF, sv);
            ctx.fill(bx + 2, bYy + 4, bx + 3, bYy + 5, c);
            ctx.fill(bx + 3, bYy + 5, bx + 4, bYy + 6, c);
            ctx.fill(bx + 4, bYy + 4, bx + 5, bYy + 5, c);
            ctx.fill(bx + 5, bYy + 3, bx + 6, bYy + 4, c);
        }
        String label = Draw.ellipsize(t.name(), tw + GAP - 2);
        int color = Theme.mix(Theme.mix(Theme.TEXT_DIM, Theme.TEXT, hv), 0xFFFFFFFF, sv);
        Draw.textCentered(ctx, label, tx + tw / 2, ty + TILE + 3, color);
    }

    /**
     * One preview centred on (cx, cy) in a {@code size} box: the 3D render (turning while hovered), the 2D texture, a
     * loading pulse, or the dimmed Vanilla image with a "3D"/"Modell" badge when the pack has no usable image.
     */
    private void drawView(DrawContext ctx, Tile t, PreviewCache.View view, int cx, int cy, int size, boolean over) {
        PreviewCache previews = session.previews();
        PreviewCache.Preview p = previews.preview(t.packId(), category, view);
        if (p != null) {
            if (over && p.model()) {
                PreviewCache.Strip s = previews.hoverStrip(t.packId(), category, view);
                if (s != null) {
                    long now = Util.getMeasuringTimeMs();
                    if (stripStart < 0) {
                        stripStart = now;
                    }
                    int frame = (int) ((now - stripStart) / FRAME_MS % s.frames());
                    ctx.drawTexture(RenderPipelines.GUI_TEXTURED, s.id(), cx - size / 2, cy - size / 2, 0, frame * s.size(),
                            size, size, s.size(), s.size(), s.size(), s.size() * s.frames());
                    return;
                }
            }
            int[] fitted = fit(p.width(), p.height(), size);
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, p.id(), cx - fitted[0] / 2, cy - fitted[1] / 2, 0, 0,
                    fitted[0], fitted[1], p.width(), p.height(), p.width(), p.height());
        } else if (previews.isLoading(t.packId(), category, view)) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(Util.getMeasuringTimeMs() / 180.0);
            Draw.roundRect(ctx, cx - size / 2 + 4, cy - size / 2 + 4, size - 8, size - 8, 4,
                    Theme.withAlpha(Theme.SURFACE_HOVER, 0.5f + 0.5f * pulse));
        } else if (view == PreviewCache.View.ITEM) {
            // No usable texture: models only ("Modell") or a UV atlas of a 3D item model that could not be rendered
            // ("3D"). Show the Vanilla image dimmed with a badge instead.
            String badge = previews.isModel3d(t.packId(), category) ? "3D" : "Modell";
            PreviewCache.Preview v = previews.preview(PreviewCache.VANILLA, category);
            if (v != null) {
                int[] fitted = fit(v.width(), v.height(), size);
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, v.id(), cx - fitted[0] / 2, cy - fitted[1] / 2, 0, 0,
                        fitted[0], fitted[1], v.width(), v.height(), v.width(), v.height(), 0x59FFFFFF);
            }
            int bw = Math.min(TILE - 8, Draw.font().getWidth(badge) + 8);
            Draw.roundRect(ctx, cx - bw / 2, cy + 12, bw, 9, 4, Theme.withAlpha(Theme.BORDER, 0.95f));
            Draw.textCentered(ctx, badge, cx, cy + 13, Theme.TEXT_DIM);
        }
    }

    /** Small (3/4 size) centred caption. */
    private static void caption(DrawContext ctx, String s, int cx, int y) {
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(cx, y);
        ctx.getMatrices().scale(0.75f, 0.75f);
        Draw.textCentered(ctx, s, 0, 0, Theme.TEXT_FAINT);
        ctx.getMatrices().popMatrix();
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
