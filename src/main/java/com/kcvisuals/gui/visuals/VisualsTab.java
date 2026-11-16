package com.kcvisuals.gui.visuals;

import com.kcvisuals.KCVisuals;
import com.kcvisuals.gui.TabPanel;
import com.kcvisuals.gui.Theme;
import com.kcvisuals.gui.widget.ColorPicker;
import com.kcvisuals.gui.widget.Draw;
import com.kcvisuals.gui.widget.ScrollPanel;
import com.kcvisuals.gui.widget.Slider;
import com.kcvisuals.gui.widget.Toggle;
import com.kcvisuals.gui.widget.Widget;
import com.kcvisuals.module.Module;
import com.kcvisuals.module.ModuleManager;
import com.kcvisuals.module.setting.BoolSetting;
import com.kcvisuals.module.setting.ColorSetting;
import com.kcvisuals.module.setting.ModeSetting;
import com.kcvisuals.module.setting.NumberSetting;
import com.kcvisuals.module.setting.Setting;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.DrawContext;

/** Visuals tab: one card per module (name, description, on/off switch); clicking a card expands its settings. */
public class VisualsTab implements TabPanel {
    private static final int HEAD_H = 30;
    private static final int ROW_H = 22;
    private static final int SLIDER_H = 26;
    private static final int OPT_H = 16;
    private static final int GAP = 6;
    private static final int PAD = 6;
    private static final int SWITCH_W = 44;

    private final ModuleManager modules;
    private final Map<Module, Card> cards = new IdentityHashMap<>();
    private final List<Card> order = new ArrayList<>();
    private final ModuleList list = new ModuleList();
    private ColorPicker accent;
    private int x, y, w, h;

    public VisualsTab(ModuleManager modules) {
        this.modules = modules;
        this.accent = new ColorPicker(0, 0, 0, ROW_H, "Akzentfarbe", Theme.ACCENT, this::onAccent);
        for (Module m : modules.all()) {
            Card c = new Card(m);
            cards.put(m, c);
            order.add(c);
        }
    }

    private void onAccent(int v) {
        int opaque = v | 0xFF000000;
        Theme.ACCENT = opaque;
        KCVisuals.config().setAccent(opaque);
        if (v != opaque) {
            accent.setColor(opaque); // the accent has no transparency
        }
    }

    /** Expands a module's card (used for tests / screenshots). */
    public void expand(Module m) {
        Card c = cards.get(m);
        if (c != null) {
            c.expanded = true;
        }
    }

    @Override
    public void init(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        list.setBounds(x, y, w, h);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        if (order.isEmpty()) {
            Draw.textCentered(ctx, "Noch keine Module", x + w / 2, y + h / 2 - 4, Theme.TEXT_FAINT);
            return;
        }
        list.render(ctx, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return list.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean any = list.mouseReleased(mx, my, button) | accent.mouseReleased(mx, my, button);
        for (Card c : order) {
            for (Widget wd : c.widgets) {
                any |= wd.mouseReleased(mx, my, button);
            }
        }
        return any;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (list.mouseDragged(mx, my, button, dx, dy) || accent.mouseDragged(mx, my, button, dx, dy)) {
            return true;
        }
        for (Card c : order) {
            for (Widget wd : c.widgets) {
                if (wd.mouseDragged(mx, my, button, dx, dy)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        return list.mouseScrolled(mx, my, amount);
    }

    private final class ModuleList extends ScrollPanel {
        ModuleList() {
            super(0, 0, 0, 0);
        }

        private int accentHeight() {
            return ROW_H + 8 + (accent.isExpanded() ? ColorPicker.EXPANDED_H + 4 : 0) + GAP;
        }

        @Override
        protected int contentHeight() {
            int total = accentHeight();
            for (Card c : order) {
                total += c.height() + GAP;
            }
            return Math.max(0, total - GAP);
        }

        /** Positions every card and its widgets for the given content origin; returns nothing, mutates bounds. */
        private void layout(int top) {
            int cw = w - 8; // leave room for the scrollbar
            accent.setBounds(x + PAD + 4, top + 4, cw - 2 * PAD - 8, ROW_H);
            int cy = top + accentHeight();
            for (Card c : order) {
                c.layout(x, cy, cw);
                cy += c.height() + GAP;
            }
        }

        @Override
        protected void renderContent(DrawContext ctx, int mx, int my, float delta, int top, boolean inside) {
            layout(top);
            int cw = w - 8;
            Draw.roundRect(ctx, x, top, cw, ROW_H + 8 + (accent.isExpanded() ? ColorPicker.EXPANDED_H + 4 : 0), 6,
                    Theme.withAlpha(Theme.SURFACE, 0.75f));
            accent.render(ctx, mx, my, delta);
            for (Card c : order) {
                c.render(ctx, mx, my, delta);
            }
        }

        @Override
        protected boolean contentClicked(double mx, double cy, int button) {
            layout(y - scroll());
            if (accent.mouseClicked(mx, cy + y - scroll(), button)) {
                return true;
            }
            for (Card c : order) {
                if (c.mouseClicked(mx, cy + y - scroll(), button)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** One module row plus its (lazily positioned) setting widgets. */
    private final class Card {
        final Module module;
        final Toggle power;
        final List<Widget> widgets = new ArrayList<>();
        final List<Integer> offsets = new ArrayList<>();
        boolean expanded;
        float expandAnim;
        int cx, cy, cw;

        Card(Module m) {
            this.module = m;
            this.power = new Toggle(0, 0, SWITCH_W, HEAD_H, "", m.enabled(), v -> m.setEnabled(v));
            for (Setting<?> s : m.settings()) {
                Widget wd = widgetFor(s);
                if (wd != null) {
                    widgets.add(wd);
                }
            }
        }

        private Widget widgetFor(Setting<?> s) {
            if (s instanceof BoolSetting b) {
                return new Toggle(0, 0, 0, ROW_H, b.name(), b.get(), b::set);
            }
            if (s instanceof NumberSetting n) {
                Slider sl = new Slider(0, 0, 0, SLIDER_H, n.name(), n.min(), n.max(), n.step(), n.get(), n::set);
                if (n.step() >= 1 && n.step() == Math.floor(n.step())) {
                    sl.format(v -> String.valueOf(Math.round(v)));
                } else if (n.step() > 0 && n.step() < 0.1) {
                    sl.format(v -> String.format(java.util.Locale.ROOT, "%.2f", v));
                } else {
                    sl.format(v -> String.format(java.util.Locale.ROOT, "%.1f", v));
                }
                return sl;
            }
            if (s instanceof ColorSetting c) {
                return new ColorPicker(0, 0, 0, ROW_H, c.name(), c.get(), c::set);
            }
            if (s instanceof ModeSetting m) {
                return new ModeRow(m);
            }
            return null;
        }

        int bodyHeight() {
            int total = 0;
            for (Widget wd : widgets) {
                total += heightOf(wd) + 2;
            }
            return widgets.isEmpty() ? 0 : total + PAD;
        }

        private int heightOf(Widget wd) {
            if (wd instanceof ColorPicker p) {
                return ROW_H + (p.isExpanded() ? ColorPicker.EXPANDED_H + 4 : 0);
            }
            if (wd instanceof ModeRow m) {
                return m.totalHeight();
            }
            return wd.h;
        }

        int height() {
            return HEAD_H + Math.round(bodyHeight() * expandAnim);
        }

        void layout(int x0, int y0, int w0) {
            cx = x0;
            cy = y0;
            cw = w0;
            power.setBounds(x0 + w0 - SWITCH_W - 4, y0, SWITCH_W, HEAD_H);
            int wy = y0 + HEAD_H + 2;
            for (Widget wd : widgets) {
                wd.setBounds(x0 + PAD, wy, w0 - 2 * PAD, wd instanceof Slider ? SLIDER_H : ROW_H);
                wy += heightOf(wd) + 2;
            }
        }

        void render(DrawContext ctx, int mx, int my, float delta) {
            expandAnim = Draw.approach(expandAnim, expanded ? 1f : 0f);
            if (Math.abs(expandAnim - (expanded ? 1f : 0f)) < 0.01f) {
                expandAnim = expanded ? 1f : 0f;
            }
            power.setValue(module.enabled());
            int ch = height();
            Draw.roundRect(ctx, cx, cy, cw, ch, 6, Theme.withAlpha(Theme.SURFACE, 0.75f));
            if (module.enabled()) {
                Draw.roundRect(ctx, cx, cy + 6, 2, HEAD_H - 12, 1, Theme.ACCENT);
            }
            int textRight = cx + cw - SWITCH_W - 10;
            Draw.text(ctx, Draw.ellipsize(module.name(), textRight - cx - 18), cx + 14, cy + 6, Theme.TEXT);
            Draw.text(ctx, Draw.ellipsize(module.description(), textRight - cx - 18), cx + 14, cy + 17,
                    Theme.TEXT_DIM);
            if (!widgets.isEmpty()) {
                drawChevron(ctx, cx + 5, cy + HEAD_H / 2 - 2, expandAnim);
            }
            power.render(ctx, mx, my, delta);
            if (expandAnim > 0.01f && !widgets.isEmpty()) {
                int bodyTop = cy + HEAD_H;
                Draw.hairline(ctx, cx + 8, cx + cw - 8, bodyTop, Theme.BORDER);
                ctx.enableScissor(cx, bodyTop + 1, cx + cw, cy + ch);
                // draw the picker/mode dropdowns last so their popups overlap following rows
                for (Widget wd : widgets) {
                    wd.render(ctx, mx, my, delta);
                }
                ctx.disableScissor();
            }
        }

        private void drawChevron(DrawContext ctx, int px, int py, float t) {
            int c = Theme.TEXT_DIM;
            for (int i = 0; i < 3; i++) {
                if (t < 0.5f) { // pointing right
                    ctx.fill(px + i, py + i, px + i + 1, py + 5 - i, c);
                } else { // pointing down
                    ctx.fill(px + i, py + i, px + 6 - i, py + i + 1, c);
                }
            }
        }

        boolean mouseClicked(double mx, double my, int button) {
            if (!Draw.inside(mx, my, cx, cy, cw, height())) {
                return false;
            }
            if (power.mouseClicked(mx, my, button)) {
                return true;
            }
            if (my < cy + HEAD_H) {
                if (button == 0 && !widgets.isEmpty()) {
                    expanded = !expanded;
                }
                return true;
            }
            if (expandAnim < 0.99f) {
                return true;
            }
            for (Widget wd : widgets) {
                if (wd.mouseClicked(mx, my, button)) {
                    return true;
                }
            }
            return true;
        }
    }

    /** Mode setting: label + current value pill; clicking opens a list of options below. */
    private static final class ModeRow extends Widget {
        private final ModeSetting setting;
        private boolean open;

        ModeRow(ModeSetting s) {
            super(0, 0, 0, ROW_H);
            this.setting = s;
        }

        int totalHeight() {
            return ROW_H + (open ? setting.modes().size() * OPT_H + 4 : 0);
        }

        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            boolean over = updateHover(mx, my) && my < y + ROW_H;
            if (over && hoverAnim > 0.01f) {
                Draw.roundRect(ctx, x, y, w, ROW_H, 4, Theme.withAlpha(Theme.SURFACE_HOVER, hoverAnim));
            }
            int ty = y + (ROW_H - 8) / 2 + 1;
            Draw.text(ctx, setting.name(), x + 6, ty, Theme.TEXT);
            String v = setting.get();
            int vw = Draw.font().getWidth(v);
            int pw = vw + 16;
            int px = x + w - pw - 6;
            Draw.roundRect(ctx, px, y + 3, pw, ROW_H - 6, 4, Theme.withAlpha(Theme.ACCENT, open ? 0.45f : 0.25f));
            Draw.text(ctx, v, px + 8, ty, 0xFFFFFFFF);
            if (open) {
                int oy = y + ROW_H + 2;
                Draw.roundRect(ctx, x, oy, w, setting.modes().size() * OPT_H + 2, 5, Theme.SURFACE_HOVER);
                for (String m : setting.modes()) {
                    boolean sel = m.equals(setting.get());
                    boolean hov = Draw.inside(mx, my, x, oy + 1, w, OPT_H);
                    if (hov) {
                        Draw.roundRect(ctx, x + 2, oy + 1, w - 4, OPT_H, 3, Theme.withAlpha(Theme.ACCENT, 0.3f));
                    }
                    Draw.text(ctx, m, x + 10, oy + 1 + (OPT_H - 8) / 2 + 1, sel ? Theme.ACCENT : Theme.TEXT);
                    oy += OPT_H;
                }
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button != 0 || !Draw.inside(mx, my, x, y, w, totalHeight())) {
                return false;
            }
            if (my < y + ROW_H) {
                open = !open;
                return true;
            }
            if (open) {
                int idx = (int) ((my - (y + ROW_H + 3)) / OPT_H);
                if (idx >= 0 && idx < setting.modes().size()) {
                    setting.set(setting.modes().get(idx));
                    open = false;
                }
            }
            return true;
        }
    }
}
