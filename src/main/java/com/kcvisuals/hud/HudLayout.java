package com.kcvisuals.hud;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where each HUD element sits. A placement is an anchor on the screen (0, 0.5 or 1 per axis) plus an offset in scaled
 * pixels, so an element anchored to the right edge stays at the right edge when the window or GUI scale changes.
 * Pure Java (no Minecraft types) so it can be unit tested.
 */
public final class HudLayout {
    public record Placement(double ax, double ay, int ox, int oy) {}

    private final Map<String, Placement> defaults = new LinkedHashMap<>();
    private final Map<String, Placement> current = new LinkedHashMap<>();

    public void setDefault(String id, double ax, double ay, int ox, int oy) {
        Placement p = new Placement(nearestAnchor(ax), nearestAnchor(ay), ox, oy);
        defaults.put(id, p);
        current.putIfAbsent(id, p);
    }

    public Placement get(String id) {
        Placement p = current.get(id);
        return p != null ? p : new Placement(0, 0, 0, 0);
    }

    public void set(String id, double ax, double ay, int ox, int oy) {
        current.put(id, new Placement(nearestAnchor(ax), nearestAnchor(ay), ox, oy));
    }

    public void reset() {
        current.clear();
        current.putAll(defaults);
    }

    public void reset(String id) {
        Placement d = defaults.get(id);
        if (d != null) {
            current.put(id, d);
        }
    }

    /** Screen rectangle of an element of size {@code w x h}, always fully inside {@code screenW x screenH}. */
    public Rect resolve(String id, int w, int h, int screenW, int screenH) {
        Placement p = get(id);
        int x = (int) Math.round(p.ax() * (screenW - w)) + p.ox();
        int y = (int) Math.round(p.ay() * (screenH - h)) + p.oy();
        return new Rect(clamp(x, 0, screenW - w), clamp(y, 0, screenH - h), w, h);
    }

    /**
     * Stores a drop of the element's top-left corner at {@code (x, y)}: clamps it on screen, picks the nearest anchor on
     * each axis (by the element's centre) and keeps the remaining distance as the offset.
     */
    public void drop(String id, int w, int h, int screenW, int screenH, int x, int y) {
        x = clamp(x, 0, screenW - w);
        y = clamp(y, 0, screenH - h);
        double ax = nearestAnchor((x + w / 2.0) / screenW);
        double ay = nearestAnchor((y + h / 2.0) / screenH);
        int ox = x - (int) Math.round(ax * (screenW - w));
        int oy = y - (int) Math.round(ay * (screenH - h));
        current.put(id, new Placement(ax, ay, ox, oy));
    }

    public static double nearestAnchor(double v) {
        if (Double.isNaN(v) || v < 0.25) {
            return 0;
        }
        return v < 0.75 ? 0.5 : 1;
    }

    public static int snapToGrid(int v, int grid) {
        if (grid <= 1) {
            return v;
        }
        return (int) (Math.floorDiv(v + grid / 2, grid) * (long) grid);
    }

    /**
     * Position of an element edge while dragging: sticks to the screen edges and the centre when within
     * {@code threshold}, otherwise rounds to {@code grid} (0 = no grid).
     */
    public static int snapAxis(int pos, int size, int screen, int grid, int threshold) {
        int[] candidates = {0, (screen - size) / 2, screen - size};
        int best = pos;
        int bestDist = threshold + 1;
        for (int c : candidates) {
            int d = Math.abs(pos - c);
            if (d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        return bestDist <= threshold ? best : snapToGrid(pos, grid);
    }

    private static int clamp(int v, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, v));
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        for (Map.Entry<String, Placement> e : current.entrySet()) {
            JsonObject p = new JsonObject();
            p.addProperty("ax", e.getValue().ax());
            p.addProperty("ay", e.getValue().ay());
            p.addProperty("ox", e.getValue().ox());
            p.addProperty("oy", e.getValue().oy());
            o.add(e.getKey(), p);
        }
        return o;
    }

    /** Lenient: unknown ids and malformed entries are ignored, odd anchors are coerced. */
    public void fromJson(JsonObject o) {
        if (o == null) {
            return;
        }
        for (String id : defaults.keySet()) {
            JsonElement el = o.get(id);
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            JsonObject p = el.getAsJsonObject();
            Placement d = defaults.get(id);
            current.put(id, new Placement(
                    num(p, "ax", d.ax(), true), num(p, "ay", d.ay(), true),
                    (int) num(p, "ox", d.ox(), false), (int) num(p, "oy", d.oy(), false)));
        }
    }

    private static double num(JsonObject o, String key, double fallback, boolean anchor) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        double v = e.getAsDouble();
        return anchor ? nearestAnchor(v) : v;
    }
}
