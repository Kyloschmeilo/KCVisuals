package com.kcvisuals.gui.profiles;

import com.kcvisuals.KCVisuals;
import com.kcvisuals.gui.TabPanel;
import com.kcvisuals.gui.Theme;
import com.kcvisuals.gui.packs.PackNames;
import com.kcvisuals.gui.packs.PackSession;
import com.kcvisuals.gui.widget.Button;
import com.kcvisuals.gui.widget.Draw;
import com.kcvisuals.gui.widget.ScrollPanel;
import com.kcvisuals.gui.widget.TextField;
import com.kcvisuals.pack.Category;
import com.kcvisuals.pack.PackIndex;
import com.kcvisuals.pack.ProfileStore;
import com.kcvisuals.pack.Selection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

/** Profiles tab: list of saved profiles with New / Duplicate / Rename / Delete / Activate. */
public class ProfilesTab implements TabPanel {
    private static final int BAR_H = 20;
    private static final int ROW_H = 24;
    private static final long CONFIRM_MS = 3000;

    private record Info(String name, Selection selection, int dropped) {}

    private final PackSession session;
    private final TextField name;
    private final Button create, duplicate, rename, activate, delete;
    private final ProfileList list;
    private final PickList picks;
    private final List<Info> profiles = new ArrayList<>();
    private String selected;
    private int builtRevision = -1;
    private long confirmDeleteUntil;
    private long confirmActivateUntil;
    private String message;
    private int messageColor;
    private long messageUntil;
    private int x, y, w, h;
    private int listX, listY, listW, listH, detX, detY, detW, detH;

    public ProfilesTab(PackSession session) {
        this.session = session;
        this.name = new TextField(0, 0, 0, BAR_H, "Profilname…").maxLength(40).onEnter(this::doCreate);
        this.create = new Button(0, 0, 0, BAR_H, "Neu", Button.Style.SECONDARY, this::doCreate);
        this.duplicate = new Button(0, 0, 0, BAR_H, "Duplizieren", Button.Style.SECONDARY, this::doDuplicate);
        this.rename = new Button(0, 0, 0, BAR_H, "Umbenennen", Button.Style.SECONDARY, this::doRename);
        this.activate = new Button(0, 0, 0, BAR_H, "Aktivieren", Button.Style.PRIMARY, this::doActivate);
        this.delete = new Button(0, 0, 0, BAR_H, "Löschen", Button.Style.DANGER, this::doDelete);
        this.list = new ProfileList();
        this.picks = new PickList();
    }

    @Override
    public void init(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        int bw1 = Button.widthFor("Neu") + 6, bw2 = Button.widthFor("Duplizieren"), bw3 = Button.widthFor("Umbenennen");
        int right = x + w;
        rename.setBounds(right - bw3, y, bw3, BAR_H);
        duplicate.setBounds(rename.x - 4 - bw2, y, bw2, BAR_H);
        create.setBounds(duplicate.x - 4 - bw1, y, bw1, BAR_H);
        name.setBounds(x, y, Math.max(60, Math.min(180, create.x - 8 - x)), BAR_H);

        int bodyY = y + BAR_H + 8;
        int bodyH = h - BAR_H - 8;
        listX = x;
        listY = bodyY;
        listW = Math.max(130, w * 42 / 100);
        listH = bodyH;
        list.setBounds(listX + 2, listY + 4, listW - 4, listH - 8);
        detX = listX + listW + 8;
        detY = bodyY;
        detW = w - listW - 8;
        detH = bodyH;
        int aw = Math.min(Button.widthFor("Änderungen verwerfen?"), Math.max(Button.widthFor("Aktivieren") + 6, (w - Math.max(130, w * 42 / 100) - 8) / 2 - 12));
        int dw = Button.widthFor("Wirklich löschen?");
        activate.setBounds(detX + detW - 8 - aw, detY + detH - 8 - BAR_H, aw, BAR_H);
        delete.setBounds(activate.x - 4 - dw, activate.y, dw, BAR_H);
        picks.setBounds(detX + 6, detY + 34, detW - 10, detH - 34 - BAR_H - 16);
    }

    private void refresh() {
        profiles.clear();
        ProfileStore store = session.store();
        PackIndex idx = session.index() != null ? session.index() : new PackIndex(Map.of());
        List<String> names = new ArrayList<>(store.list());
        String active = activeName();
        if (names.stream().noneMatch(n -> n.equalsIgnoreCase(active))) {
            names.add(0, active);
        }
        for (String n : names) {
            try {
                ProfileStore.LoadResult r = store.load(n, idx);
                profiles.add(new Info(n, r.selection(), r.droppedCategories().size()));
            } catch (RuntimeException e) {
                KCVisuals.LOGGER.warn("Skipping profile '{}'", n, e);
            }
        }
        if (selected == null || find(selected) == null) {
            selected = active;
        }
        builtRevision = session.revision();
    }

    private String activeName() {
        String a = session.activeProfile();
        return a != null ? a : session.store().active();
    }

    private Info find(String n) {
        for (Info i : profiles) {
            if (i.name().equals(n)) {
                return i;
            }
        }
        return null;
    }

    private boolean exists(String n) {
        return profiles.stream().anyMatch(i -> i.name().equalsIgnoreCase(n));
    }

    private void flash(String msg, int color) {
        message = msg;
        messageColor = color;
        messageUntil = Util.getMeasuringTimeMs() + 3500;
    }

    /** Validated name from the text field, or null (with an error message shown). */
    private String newName() {
        String n = name.text().strip();
        if (n.isEmpty()) {
            flash("Bitte oben einen Namen eingeben", Theme.WARNING);
            name.setFocused(true);
            return null;
        }
        if (exists(n)) {
            flash("Ein Profil „" + n + "“ gibt es schon", Theme.DANGER);
            return null;
        }
        return n;
    }

    private void doCreate() {
        String n = newName();
        if (n == null) {
            return;
        }
        try {
            session.store().save(n, Selection.empty());
            selected = n;
            name.setText("");
            session.touch();
            flash("Profil „" + n + "“ angelegt", Theme.SUCCESS);
        } catch (IllegalArgumentException e) {
            flash("Ungültiger Name (nicht erlaubt: / \\ : * ? \" < > |)", Theme.DANGER);
        } catch (RuntimeException e) {
            KCVisuals.LOGGER.error("Creating profile failed", e);
            flash("Speichern fehlgeschlagen", Theme.DANGER);
        }
    }

    private void doDuplicate() {
        Info src = find(selected);
        if (src == null) {
            return;
        }
        String n = name.text().isBlank() ? uniqueCopyName(src.name()) : newName();
        if (n == null) {
            return;
        }
        try {
            // save (not file copy) so a profile that only exists implicitly can be duplicated too
            session.store().save(n, src.selection());
            selected = n;
            name.setText("");
            session.touch();
            flash("Kopie „" + n + "“ erstellt", Theme.SUCCESS);
        } catch (IllegalArgumentException e) {
            flash("Ungültiger Name (nicht erlaubt: / \\ : * ? \" < > |)", Theme.DANGER);
        } catch (RuntimeException e) {
            KCVisuals.LOGGER.error("Duplicating profile failed", e);
            flash("Duplizieren fehlgeschlagen", Theme.DANGER);
        }
    }

    private String uniqueCopyName(String base) {
        String n = base + " Kopie";
        int i = 2;
        while (exists(n)) {
            n = base + " Kopie " + i++;
        }
        return n;
    }

    private void doRename() {
        Info src = find(selected);
        if (src == null) {
            return;
        }
        String n = name.text().strip();
        if (!n.isEmpty() && !n.equals(src.name()) && n.equalsIgnoreCase(src.name())) {
            // case-only rename: fine on any file system via a temporary name below
        } else {
            n = newName();
            if (n == null) {
                return;
            }
        }
        try {
            ProfileStore store = session.store();
            store.save(n, src.selection());
            if (!n.equalsIgnoreCase(src.name())) {
                store.delete(src.name());
            }
            if (src.name().equals(activeName())) {
                store.setActive(n);
                session.setActiveProfileName(n);
            }
            selected = n;
            name.setText("");
            session.touch();
            flash("Umbenannt in „" + n + "“", Theme.SUCCESS);
        } catch (IllegalArgumentException e) {
            flash("Ungültiger Name (nicht erlaubt: / \\ : * ? \" < > |)", Theme.DANGER);
        } catch (RuntimeException e) {
            KCVisuals.LOGGER.error("Renaming profile failed", e);
            flash("Umbenennen fehlgeschlagen", Theme.DANGER);
        }
    }

    private void doDelete() {
        Info src = find(selected);
        if (src == null) {
            return;
        }
        if (src.name().equals(activeName())) {
            flash("Das aktive Profil kann nicht gelöscht werden", Theme.WARNING);
            return;
        }
        long now = Util.getMeasuringTimeMs();
        if (now > confirmDeleteUntil) {
            confirmDeleteUntil = now + CONFIRM_MS;
            return;
        }
        confirmDeleteUntil = 0;
        try {
            session.store().delete(src.name());
            selected = activeName();
            session.touch();
            flash("Profil „" + src.name() + "“ gelöscht", Theme.SUCCESS);
        } catch (RuntimeException e) {
            KCVisuals.LOGGER.error("Deleting profile failed", e);
            flash("Löschen fehlgeschlagen", Theme.DANGER);
        }
    }

    private void doActivate() {
        if (selected == null) {
            return;
        }
        long now = Util.getMeasuringTimeMs();
        if (session.isDirty() && now > confirmActivateUntil) {
            // un-applied picks in the Packs tab would be replaced by the profile: ask once more
            confirmActivateUntil = now + CONFIRM_MS;
            return;
        }
        confirmActivateUntil = 0;
        session.activate(selected);
    }

    /** Selects a profile row programmatically (dev checks / keyboard). */
    public void selectProfile(String n) {
        selected = n;
        confirmDeleteUntil = 0;
        confirmActivateUntil = 0;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        session.ensureLoaded();
        if (builtRevision != session.revision()) {
            refresh();
        }
        Info sel = find(selected);
        boolean confirming = Util.getMeasuringTimeMs() < confirmDeleteUntil;
        delete.label(confirming ? "Wirklich löschen?" : "Löschen");
        delete.style = confirming ? Button.Style.DANGER : Button.Style.SECONDARY;
        boolean isActive = sel != null && sel.name().equals(activeName());
        delete.enabled = sel != null && !isActive;
        activate.enabled = sel != null && session.index() != null && !session.isApplying() && !session.isSelectionUnknown();
        boolean confirmActivate = Util.getMeasuringTimeMs() < confirmActivateUntil;
        activate.label(session.isApplying() ? "Wird angewendet…"
                : confirmActivate ? "Änderungen verwerfen?" : (isActive ? "Neu anwenden" : "Aktivieren"));
        activate.style = confirmActivate ? Button.Style.DANGER : Button.Style.PRIMARY;
        duplicate.enabled = rename.enabled = sel != null;

        name.render(ctx, mx, my, delta);
        create.render(ctx, mx, my, delta);
        duplicate.render(ctx, mx, my, delta);
        rename.render(ctx, mx, my, delta);

        Draw.roundRect(ctx, listX, listY, listW, listH, 6, Theme.withAlpha(Theme.SURFACE, 0.55f));
        Draw.roundRect(ctx, detX, detY, detW, detH, 6, Theme.withAlpha(Theme.SURFACE, 0.55f));
        list.render(ctx, mx, my, delta);

        if (sel != null) {
            int hx = detX + 10, hy = detY + 9;
            String title = Draw.ellipsize(sel.name(), detW - 70);
            Draw.text(ctx, title, hx, hy, Theme.TEXT);
            if (isActive) {
                int bx = hx + Draw.font().getWidth(title) + 8;
                Draw.roundRect(ctx, bx - 1, hy - 3, Draw.font().getWidth("Aktiv") + 10, 14, 7, Theme.withAlpha(Theme.ACCENT, 0.3f));
                Draw.text(ctx, "Aktiv", bx + 4, hy, 0xFFFFFFFF);
            }
            int n = sel.selection().picks().size();
            String sub = n == 0 ? "Alles Vanilla" : (n == 1 ? "1 Kategorie angepasst" : n + " Kategorien angepasst");
            if (sel.dropped() > 0) {
                sub += " · " + sel.dropped() + " mit fehlendem Pack";
            }
            Draw.text(ctx, Draw.ellipsize(sub, detW - 20), hx, hy + 12, sel.dropped() > 0 ? Theme.WARNING : Theme.TEXT_FAINT);
            Draw.hairline(ctx, detX + 8, detX + detW - 8, detY + 31, Theme.BORDER);
            picks.render(ctx, mx, my, delta);
        }
        delete.render(ctx, mx, my, delta);
        activate.render(ctx, mx, my, delta);

        if (message != null && Util.getMeasuringTimeMs() < messageUntil) {
            Draw.text(ctx, Draw.ellipsize(message, detW - 16 - activate.w - delete.w),
                    detX + 10, activate.y + 6, messageColor);
        } else if (session.isSelectionUnknown()) {
            Draw.text(ctx, Draw.ellipsize("Packs nicht eingelesen – im Packs-Tab neu einlesen", detW - 24 - activate.w - delete.w),
                    detX + 10, activate.y + 6, Theme.DANGER);
        } else if (session.isDirty()) {
            Draw.text(ctx, Draw.ellipsize("Ungespeicherte Änderungen im Packs-Tab", detW - 24 - activate.w - delete.w),
                    detX + 10, activate.y + 6, Theme.TEXT_FAINT);
        }
    }

    /** Left column: one row per profile with pick count and an "active" marker. */
    private final class ProfileList extends ScrollPanel {
        private final Map<String, Float> hover = new HashMap<>();

        ProfileList() {
            super(0, 0, 0, 0);
        }

        @Override
        protected int contentHeight() {
            return profiles.size() * (ROW_H + 2) + 2;
        }

        @Override
        protected void renderContent(DrawContext ctx, int mx, int my, float delta, int top, boolean inside) {
            String active = activeName();
            for (int i = 0; i < profiles.size(); i++) {
                Info p = profiles.get(i);
                int ry = top + 2 + i * (ROW_H + 2);
                boolean sel = p.name().equals(selected);
                boolean over = inside && Draw.inside(mx, my, x + 2, ry, w - 9, ROW_H);
                float hv = Draw.approach(hover.getOrDefault(p.name(), 0f), over && !sel ? 1f : 0f);
                hover.put(p.name(), hv);
                int rx = x + 2, rw = w - 9;
                if (sel) {
                    Draw.roundRect(ctx, rx, ry, rw, ROW_H, 5, Theme.withAlpha(Theme.ACCENT, 0.20f));
                    Draw.roundRect(ctx, rx, ry + 6, 2, ROW_H - 12, 1, Theme.ACCENT);
                } else if (hv > 0.01f) {
                    Draw.roundRect(ctx, rx, ry, rw, ROW_H, 5, Theme.withAlpha(Theme.SURFACE_HOVER, hv));
                }
                boolean act = p.name().equals(active);
                // avatar circle with the initial
                int ax = rx + 6, ay = ry + 4;
                Draw.roundRect(ctx, ax, ay, 16, 16, 8, act ? Theme.ACCENT : Theme.BORDER);
                String initial = p.name().isEmpty() ? "?" : p.name().substring(0, 1).toUpperCase(Locale.GERMAN);
                Draw.textCentered(ctx, initial, ax + 8, ay + 4, 0xFFFFFFFF);
                int tx = ax + 22;
                int n = p.selection().picks().size();
                String sub = n == 0 ? "Vanilla" : (n == 1 ? "1 Kategorie" : n + " Kategorien");
                Draw.text(ctx, Draw.ellipsize(p.name(), rw - (tx - rx) - (act ? 40 : 8)), tx, ry + 3,
                        sel ? 0xFFFFFFFF : Theme.mix(Theme.TEXT_DIM, Theme.TEXT, hv));
                Draw.text(ctx, sub, tx, ry + 13, Theme.TEXT_FAINT);
                if (act) {
                    String a = "Aktiv";
                    int aw = Draw.font().getWidth(a);
                    Draw.roundRect(ctx, rx + rw - aw - 14, ry + 6, aw + 8, 12, 6, Theme.withAlpha(Theme.SUCCESS, 0.25f));
                    Draw.text(ctx, a, rx + rw - aw - 10, ry + 8, Theme.SUCCESS);
                }
            }
        }

        @Override
        protected boolean contentClicked(double mx, double cy, int button) {
            int i = (int) ((cy - 2) / (ROW_H + 2));
            if (button == 0 && cy >= 2 && i >= 0 && i < profiles.size()) {
                selected = profiles.get(i).name();
                confirmDeleteUntil = 0;
                confirmActivateUntil = 0;
                return true;
            }
            return false;
        }
    }

    /** Right column: what the selected profile picks, per category. */
    private final class PickList extends ScrollPanel {
        PickList() {
            super(0, 0, 0, 0);
        }

        private List<String[]> rows() {
            Info sel = find(selected);
            List<String[]> rows = new ArrayList<>();
            if (sel == null) {
                return rows;
            }
            PackIndex idx = session.index();
            for (Category c : session.registry().all()) {
                String pack = sel.selection().packFor(c.id());
                if (pack != null) {
                    PackIndex.PackInfo info = idx == null ? null : idx.packs().get(pack);
                    rows.add(new String[] {c.displayName(), PackNames.clean(info == null ? pack : info.displayName())});
                }
            }
            return rows;
        }

        @Override
        protected int contentHeight() {
            return rows().size() * 12 + 4;
        }

        @Override
        protected void renderContent(DrawContext ctx, int mx, int my, float delta, int top, boolean inside) {
            List<String[]> rows = rows();
            if (rows.isEmpty()) {
                Draw.text(ctx, "Keine Kategorie angepasst – im Packs-Tab auswählen.", x + 4, top + 4, Theme.TEXT_FAINT);
                return;
            }
            int col = Math.min(w / 2, 100);
            for (int i = 0; i < rows.size(); i++) {
                int ry = top + 2 + i * 12;
                Draw.text(ctx, Draw.ellipsize(rows.get(i)[0], col - 6), x + 4, ry, Theme.TEXT_DIM);
                Draw.text(ctx, Draw.ellipsize(rows.get(i)[1], w - col - 10), x + col, ry, Theme.TEXT);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (name.mouseClicked(mx, my, button)) {
            return true;
        }
        for (Button b : List.of(create, duplicate, rename, activate, delete)) {
            if (b.mouseClicked(mx, my, button)) {
                return true;
            }
        }
        if (!delete.isMouseOver(mx, my)) {
            confirmDeleteUntil = 0;
        }
        if (!activate.isMouseOver(mx, my)) {
            confirmActivateUntil = 0;
        }
        return list.mouseClicked(mx, my, button) || picks.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return list.mouseReleased(mx, my, button) | picks.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return list.mouseDragged(mx, my, button, dx, dy) || picks.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        return list.mouseScrolled(mx, my, amount) || picks.mouseScrolled(mx, my, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (name.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
            int i = 0;
            for (int k = 0; k < profiles.size(); k++) {
                if (profiles.get(k).name().equals(selected)) {
                    i = k;
                }
            }
            if (!profiles.isEmpty()) {
                i = Math.floorMod(i + (keyCode == GLFW.GLFW_KEY_UP ? -1 : 1), profiles.size());
                selectProfile(profiles.get(i).name());
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return name.charTyped(chr, modifiers);
    }
}
