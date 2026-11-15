package com.kcvisuals.gui.hud;

import com.kcvisuals.KCVisuals;
import com.kcvisuals.gui.KCScreen;
import com.kcvisuals.gui.Theme;
import com.kcvisuals.gui.widget.Button;
import com.kcvisuals.gui.widget.Draw;
import com.kcvisuals.hud.HudElement;
import com.kcvisuals.hud.HudLayout;
import com.kcvisuals.hud.HudManager;
import com.kcvisuals.hud.Rect;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Drag & drop editor: every enabled HUD element is shown with sample data, can be dragged anywhere (snapping to a
 * grid, the screen edges and the centre) and is re-anchored to the nearest edge on drop.
 */
public class HudEditorScreen extends Screen {
    public static final int GRID = 8;
    private static final int SNAP_EDGE = 4;
    private static final int BTN_H = 20;

    private final Screen kcParent;
    private final HudManager hud = HudManager.get();
    private final List<Button> buttons = new ArrayList<>();
    private Button gridButton;
    private HudElement dragging;
    private int grabDx, grabDy;
    private Rect dragRect;
    private boolean snappedX, snappedY;
    private int toolbarW;

    /** @param kcParent the screen the KC menu was opened over; the menu is reopened with it on close. */
    public HudEditorScreen(Screen kcParent) {
        super(Text.literal("HUD bearbeiten"));
        this.kcParent = kcParent;
    }

    @Override
    protected void init() {
        buttons.clear();
        String reset = "Zurücksetzen";
        String done = "Fertig";
        int rw = Button.widthFor(reset) + 8, gw = 96, dw = Button.widthFor(done) + 16;
        int total = rw + gw + dw + 8;
        toolbarW = total;
        int bx = (width - total) / 2;
        int by = height - 56;
        buttons.add(new Button(bx, by, rw, BTN_H, reset, Button.Style.DANGER, () -> {
            hud.layout().reset();
            dragging = null;
        }));
        gridButton = new Button(bx + rw + 4, by, gw, BTN_H, "", Button.Style.SECONDARY, () -> {
            hud.setGridSnap(!hud.gridSnap());
        });
        gridButton.label(() -> "Raster: " + (hud.gridSnap() ? "An" : "Aus"));
        buttons.add(gridButton);
        buttons.add(new Button(bx + rw + gw + 8, by, dw, BTN_H, done, Button.Style.PRIMARY, this::close));
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // no vanilla blur: the HUD behind the editor must stay readable
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        Draw.beginFrame();
        MinecraftClient mc = MinecraftClient.getInstance();
        ctx.fill(0, 0, width, height, mc.world == null ? 0xE0101018 : 0x70101018);
        if (hud.gridSnap()) {
            int dot = Theme.withAlpha(Theme.TEXT, 0.10f);
            for (int gx = GRID; gx < width; gx += GRID) {
                for (int gy = GRID; gy < height; gy += GRID) {
                    if (gx % (GRID * 4) == 0 && gy % (GRID * 4) == 0) {
                        ctx.fill(gx, gy, gx + 1, gy + 1, dot);
                    }
                }
            }
        }
        // centre guides while an element is snapped onto them
        if (dragging != null && dragRect != null) {
            int guide = Theme.withAlpha(Theme.ACCENT, 0.55f);
            if (snappedX && Math.abs(dragRect.x() + dragRect.w() / 2 - width / 2) <= 1) {
                ctx.fill(width / 2, 0, width / 2 + 1, height, guide);
            }
            if (snappedY && Math.abs(dragRect.y() + dragRect.h() / 2 - height / 2) <= 1) {
                ctx.fill(0, height / 2, width, height / 2 + 1, guide);
            }
        }
        boolean any = false;
        HudElement hover = dragging != null ? dragging : elementAt(mx, my);
        for (HudElement e : hud.elements()) {
            if (!e.enabled()) {
                continue;
            }
            Rect r = rectFor(e);
            if (r == null) {
                continue;
            }
            any = true;
            e.render(ctx, r.x(), r.y(), true);
            boolean active = e == hover;
            Draw.roundBorderPx(ctx, r.x() - 1, r.y() - 1, r.w() + 2, r.h() + 2, 5, 1,
                    active ? Theme.ACCENT : Theme.withAlpha(Theme.ACCENT, 0.4f));
            if (active) {
                String n = e.name();
                int ty = r.y() > 12 ? r.y() - 11 : r.y() + r.h() + 3;
                Draw.text(ctx, n, r.x(), ty, Theme.ACCENT);
            }
        }
        String hint = any ? "Ziehen: verschieben  |  Rechtsklick: zurücksetzen  |  Esc: fertig"
                : "Keine Elemente aktiv. Aktiviere sie im HUD-Tab.";
        int pw = Math.max(toolbarW, Draw.font().getWidth(hint)) + 16;
        Draw.roundRect(ctx, (width - pw) / 2, height - 82, pw, 56, 8, Theme.withAlpha(Theme.PANEL, 0.92f));
        Draw.roundBorderPx(ctx, (width - pw) / 2, height - 82, pw, 56, 8, 1, Theme.BORDER);
        Draw.textCentered(ctx, hint, width / 2, height - 77, Theme.TEXT_DIM);
        for (Button b : buttons) {
            b.render(ctx, mx, my, delta);
        }
    }

    private Rect rectFor(HudElement e) {
        if (e == dragging && dragRect != null) {
            return dragRect;
        }
        return hud.rectOf(e, width, height, true);
    }

    private HudElement elementAt(double mx, double my) {
        List<HudElement> list = hud.elements();
        for (int i = list.size() - 1; i >= 0; i--) {
            HudElement e = list.get(i);
            if (!e.enabled()) {
                continue;
            }
            Rect r = hud.rectOf(e, width, height, true);
            if (r != null && r.contains(mx, my)) {
                return e;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        for (Button b : buttons) {
            if (b.mouseClicked(mx, my, click.button())) {
                return true;
            }
        }
        HudElement e = elementAt(mx, my);
        if (e != null) {
            if (click.button() == 0) {
                Rect r = hud.rectOf(e, width, height, true);
                dragging = e;
                dragRect = r;
                grabDx = (int) mx - r.x();
                grabDy = (int) my - r.y();
            } else if (click.button() == 1) {
                hud.layout().reset(e.id());
            }
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (dragging == null || click.button() != 0) {
            return super.mouseDragged(click, dx, dy);
        }
        int w = dragRect.w(), h = dragRect.h();
        int grid = hud.gridSnap() ? GRID : 0;
        int nx = (int) click.x() - grabDx;
        int ny = (int) click.y() - grabDy;
        int sx = HudLayout.snapAxis(nx, w, width, grid, SNAP_EDGE);
        int sy = HudLayout.snapAxis(ny, h, height, grid, SNAP_EDGE);
        snappedX = sx != nx;
        snappedY = sy != ny;
        nx = Math.max(0, Math.min(width - w, sx));
        ny = Math.max(0, Math.min(height - h, sy));
        dragRect = new Rect(nx, ny, w, h);
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragging != null && click.button() == 0) {
            if (dragRect != null) {
                hud.layout().drop(dragging.id(), dragRect.w(), dragRect.h(), width, height, dragRect.x(), dragRect.y());
            }
            dragging = null;
            dragRect = null;
            snappedX = snappedY = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public void removed() {
        KCVisuals.saveConfig();
        super.removed();
    }

    @Override
    public void close() {
        MinecraftClient mc = MinecraftClient.getInstance();
        KCScreen menu = new KCScreen(kcParent);
        menu.setActiveTab(KCScreen.Tab.HUD);
        mc.setScreen(menu);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
