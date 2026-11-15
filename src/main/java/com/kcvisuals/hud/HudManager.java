package com.kcvisuals.hud;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.DrawContext;

/** Owns the HUD elements, their layout and shared style, and (de)serialises all of it into the config hud slot. */
public final class HudManager {
    private static final HudManager INSTANCE = new HudManager();

    public static HudManager get() {
        return INSTANCE;
    }

    private final List<HudElement> elements = new ArrayList<>();
    private final HudLayout layout = new HudLayout();
    private int background = 0xB0141420;
    private boolean gridSnap = true;

    private HudManager() {
        add(new Watermark(), 0, 0, 4, 4, true);
        add(new PotionHud(), 0, 0, 4, 26, false);
        add(new TargetHud(), 0.5, 0.5, 0, 40, false);
        add(new Keystrokes(), 1, 1, -6, -6, false);
        add(new ArmorTotemCounter(), 1, 0.5, -4, 0, false);
        add(new Coordinates(), 1, 0, -4, 4, false);
    }

    private void add(HudElement e, double ax, double ay, int ox, int oy, boolean on) {
        e.setEnabled(on);
        elements.add(e);
        layout.setDefault(e.id(), ax, ay, ox, oy);
    }

    public List<HudElement> elements() {
        return Collections.unmodifiableList(elements);
    }

    public HudLayout layout() {
        return layout;
    }

    /** Background colour of all cards, ARGB (alpha is user adjustable). */
    public int background() {
        return background;
    }

    public void setBackground(int argb) {
        background = argb;
    }

    public boolean gridSnap() {
        return gridSnap;
    }

    public void setGridSnap(boolean on) {
        gridSnap = on;
    }

    /** Where {@code e} is drawn on a {@code sw x sh} screen, or null when it has nothing to show. */
    public Rect rectOf(HudElement e, int sw, int sh, boolean preview) {
        if (!e.visible(preview)) {
            return null;
        }
        return layout.resolve(e.id(), e.width(preview), e.height(preview), sw, sh);
    }

    /** Draws every enabled, visible element. */
    public void renderAll(DrawContext ctx, int sw, int sh, boolean preview) {
        for (HudElement e : elements) {
            if (!e.enabled()) {
                continue;
            }
            Rect r = rectOf(e, sw, sh, preview);
            if (r != null) {
                e.render(ctx, r.x(), r.y(), preview);
            }
        }
    }

    public void enableAll(boolean on) {
        for (HudElement e : elements) {
            e.setEnabled(on);
        }
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.add("layout", layout.toJson());
        JsonObject en = new JsonObject();
        for (HudElement e : elements) {
            en.addProperty(e.id(), e.enabled());
        }
        o.add("enabled", en);
        o.addProperty("background", background);
        o.addProperty("gridSnap", gridSnap);
        return o;
    }

    /** Lenient: anything missing or malformed keeps its current/default value. */
    public void fromJson(JsonObject o) {
        if (o == null) {
            return;
        }
        JsonElement l = o.get("layout");
        if (l != null && l.isJsonObject()) {
            layout.fromJson(l.getAsJsonObject());
        }
        JsonElement en = o.get("enabled");
        if (en != null && en.isJsonObject()) {
            for (HudElement e : elements) {
                JsonElement v = en.getAsJsonObject().get(e.id());
                if (v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean()) {
                    e.setEnabled(v.getAsBoolean());
                }
            }
        }
        JsonElement bg = o.get("background");
        if (bg != null && bg.isJsonPrimitive() && bg.getAsJsonPrimitive().isNumber()) {
            background = (int) bg.getAsLong();
        }
        JsonElement gs = o.get("gridSnap");
        if (gs != null && gs.isJsonPrimitive() && gs.getAsJsonPrimitive().isBoolean()) {
            gridSnap = gs.getAsBoolean();
        }
    }
}
