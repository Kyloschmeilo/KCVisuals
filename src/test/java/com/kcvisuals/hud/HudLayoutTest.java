package com.kcvisuals.hud;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class HudLayoutTest {
    private static HudLayout layout() {
        HudLayout l = new HudLayout();
        l.setDefault("a", 1, 0, -4, 4);
        return l;
    }
    @Test
    void jsonRoundTripKeepsPositions() {
        HudLayout a = layout();
        a.drop("a", 100, 20, 854, 480, 300, 200);
        HudLayout.Placement moved = a.get("a");
        HudLayout b = layout();
        b.fromJson(com.google.gson.JsonParser.parseString(a.toJson().toString()).getAsJsonObject());
        assertEquals(moved, b.get("a"));
        assertNotEquals(layout().get("a"), b.get("a"));
    }

    @Test
    void anchorRightStaysRight() {
        HudLayout l = layout();
        Rect big = l.resolve("a", 100, 20, 1920, 1080);
        Rect small = l.resolve("a", 100, 20, 854, 480);
        assertEquals(1920 - 4, big.x() + big.w());
        assertEquals(854 - 4, small.x() + small.w());
        assertEquals(4, small.y());
    }

    @Test
    void clampedWhenTooFar() {
        HudLayout l = layout();
        l.set("a", 0, 0, 5000, 5000);
        Rect r = l.resolve("a", 100, 20, 854, 480);
        assertEquals(854 - 100, r.x());
        assertEquals(480 - 20, r.y());
        l.set("a", 1, 1, -9000, -9000);
        r = l.resolve("a", 100, 20, 854, 480);
        assertEquals(0, r.x());
        assertEquals(0, r.y());
        // wider than the screen: pinned to the left edge instead of going negative
        r = l.resolve("a", 900, 20, 854, 480);
        assertEquals(0, r.x());
    }

    @Test
    void snapsToNearestAnchorOnDrop() {
        HudLayout l = layout();
        l.drop("a", 100, 20, 854, 480, 854 - 100 - 10, 30);
        HudLayout.Placement p = l.get("a");
        assertEquals(1.0, p.ax());
        assertEquals(-10, p.ox());
        assertEquals(0.0, p.ay());
        assertEquals(30, p.oy());
        Rect r = l.resolve("a", 100, 20, 1920, 1080);
        assertEquals(1920 - 10, r.x() + r.w());

        l.drop("a", 100, 20, 854, 480, 854 / 2 - 50 + 3, 480 / 2 - 10 - 2);
        p = l.get("a");
        assertEquals(0.5, p.ax());
        assertEquals(0.5, p.ay());
        assertEquals(3, p.ox());
        assertEquals(-2, p.oy());

        l.drop("a", 100, 20, 854, 480, 12, 480 - 20 - 6);
        p = l.get("a");
        assertEquals(0.0, p.ax());
        assertEquals(1.0, p.ay());
        assertEquals(12, p.ox());
        assertEquals(-6, p.oy());
    }

    @Test
    void dropOutsideScreenIsClamped() {
        HudLayout l = layout();
        l.drop("a", 100, 20, 854, 480, -300, 9999);
        Rect r = l.resolve("a", 100, 20, 854, 480);
        assertEquals(0, r.x());
        assertEquals(460, r.y());
    }

    @Test
    void gridSnapRoundsToNearestMultiple() {
        assertEquals(8, HudLayout.snapToGrid(9, 4));
        assertEquals(12, HudLayout.snapToGrid(10, 4));
        assertEquals(-4, HudLayout.snapToGrid(-3, 4));
        assertEquals(7, HudLayout.snapToGrid(7, 0));
    }

    @Test
    void resetRestoresDefaults() {
        HudLayout l = layout();
        l.set("a", 0.5, 0.5, 9, 9);
        l.reset();
        assertEquals(1.0, l.get("a").ax());
        assertEquals(-4, l.get("a").ox());
    }

    @Test
    void jsonRoundTripAndLenientLoad() {
        HudLayout l = layout();
        l.set("a", 0.5, 1, 7, -8);
        JsonObject o = l.toJson();
        HudLayout m = layout();
        m.fromJson(o);
        assertEquals(l.get("a"), m.get("a"));

        JsonObject bad = new JsonObject();
        bad.addProperty("a", 5);
        bad.add("unknown", new JsonObject());
        HudLayout n = layout();
        n.fromJson(bad);
        assertEquals(1.0, n.get("a").ax());
        // anchors other than 0 / 0.5 / 1 are coerced to the nearest one
        JsonObject odd = new JsonObject();
        JsonObject a = new JsonObject();
        a.addProperty("ax", 0.9);
        a.addProperty("ay", -3);
        odd.add("a", a);
        n.fromJson(odd);
        assertEquals(1.0, n.get("a").ax());
        assertEquals(0.0, n.get("a").ay());
    }

    @Test
    void unknownHudElementIgnored() {
        // old configs may still carry the removed "target" element
        JsonObject old = new JsonObject();
        JsonObject target = new JsonObject();
        target.addProperty("ax", 0.5);
        target.addProperty("ay", 0.5);
        target.addProperty("ox", 0);
        target.addProperty("oy", 40);
        old.add("target", target);
        HudLayout l = layout();
        l.fromJson(old);
        assertNull(l.toJson().get("target"));
        assertEquals(1.0, l.get("a").ax());
    }

    @Test
    void edgeAndCenterSnapBeatGrid() {
        // 854 wide screen, 100 wide element: edges at 0 and 754, centre at 377
        assertEquals(0, HudLayout.snapAxis(3, 100, 854, 8, 4));
        assertEquals(754, HudLayout.snapAxis(751, 100, 854, 8, 4));
        assertEquals(377, HudLayout.snapAxis(380, 100, 854, 8, 4));
        assertEquals(96, HudLayout.snapAxis(97, 100, 854, 8, 4));
        assertEquals(97, HudLayout.snapAxis(97, 100, 854, 0, 4));
    }
}
