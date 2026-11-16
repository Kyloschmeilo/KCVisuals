package com.kcvisuals.render;

import static org.junit.jupiter.api.Assertions.*;

import com.kcvisuals.render.CrosshairGeometry.Rect;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CrosshairGeometryTest {
    private static Set<Long> pixels(List<Rect> rects) {
        Set<Long> s = new HashSet<>();
        for (Rect r : rects) {
            for (int y = r.y0(); y < r.y1(); y++) {
                for (int x = r.x0(); x < r.x1(); x++) {
                    s.add(((long) x << 32) ^ (y & 0xFFFFFFFFL));
                }
            }
        }
        return s;
    }

    private static boolean has(Set<Long> px, int x, int y) {
        return px.contains(((long) x << 32) ^ (y & 0xFFFFFFFFL));
    }

    /** Mirror around the centre span [lo, lo+t): x → (2*lo + t - 1) - x. */
    private static void assertSymmetric(Set<Long> px, int t) {
        int sum = 2 * CrosshairGeometry.lo(t) + t - 1;
        for (long p : px) {
            int x = (int) (p >> 32), y = (int) p;
            assertTrue(has(px, sum - x, y), "x mirror of " + x + "," + y);
            assertTrue(has(px, x, sum - y), "y mirror of " + x + "," + y);
            assertTrue(has(px, y, x), "diagonal mirror of " + x + "," + y);
        }
    }

    @Test
    void crossHasFourArmsWithGapAndThickness() {
        for (int t = 1; t <= 4; t++) {
            List<Rect> r = CrosshairGeometry.build(CrosshairGeometry.CROSS, 5, 3, t);
            assertEquals(4, r.size());
            for (Rect a : r) {
                int w = a.x1() - a.x0(), h = a.y1() - a.y0();
                assertEquals(5 * t, w * h, "arm area");
                assertTrue(w == t || h == t);
            }
            Set<Long> px = pixels(r);
            assertEquals(4 * 5 * t, px.size(), "arms must not overlap");
            assertSymmetric(px, t);
            // centre span and gap are empty
            int lo = CrosshairGeometry.lo(t);
            for (int d = 0; d < 3; d++) {
                assertFalse(has(px, lo + t + d, lo), "gap right");
                assertFalse(has(px, lo - 1 - d, lo), "gap left");
            }
            assertFalse(has(px, lo, lo), "centre");
        }
    }

    @Test
    void zeroGapTouchesCentre() {
        Set<Long> px = pixels(CrosshairGeometry.build(CrosshairGeometry.CROSS, 4, 0, 1));
        assertTrue(has(px, 1, 0));
        assertTrue(has(px, -1, 0));
        assertFalse(has(px, 0, 0));
    }

    @Test
    void dotIsCentredSquare() {
        assertEquals(List.of(new Rect(0, 0, 1, 1)), CrosshairGeometry.build(CrosshairGeometry.DOT, 9, 9, 1));
        assertEquals(List.of(new Rect(-1, -1, 1, 1)), CrosshairGeometry.build(CrosshairGeometry.DOT, 9, 9, 2));
        assertEquals(List.of(new Rect(-1, -1, 2, 2)), CrosshairGeometry.build(CrosshairGeometry.DOT, 9, 9, 3));
    }

    @Test
    void plusDotIsCrossPlusDot() {
        List<Rect> r = CrosshairGeometry.build(CrosshairGeometry.PLUS_DOT, 5, 2, 2);
        assertEquals(5, r.size());
        assertTrue(r.contains(new Rect(-1, -1, 1, 1)));
    }

    @Test
    void circleIsSymmetricRingWithinRadius() {
        for (int t = 1; t <= 3; t++) {
            List<Rect> r = CrosshairGeometry.build(CrosshairGeometry.CIRCLE, 5, 2, t);
            Set<Long> px = pixels(r);
            assertFalse(px.isEmpty());
            assertSymmetric(px, t);
            int lo = CrosshairGeometry.lo(t);
            double c = lo + t / 2.0;
            for (long p : px) {
                int x = (int) (p >> 32), y = (int) p;
                double d = Math.hypot(x + 0.5 - c, y + 0.5 - c);
                assertTrue(d < 7.0 + 1e-9 && d >= 7.0 - t - 1e-9, "pixel " + x + "," + y + " at " + d);
            }
            assertFalse(has(px, lo, lo), "ring is hollow");
            // every rect is a single-row run
            for (Rect a : r) {
                assertEquals(1, a.y1() - a.y0());
            }
        }
    }

    @Test
    void unknownModeFallsBackToCrossAndInputsClamp() {
        assertEquals(CrosshairGeometry.build(CrosshairGeometry.CROSS, 1, 0, 1),
                CrosshairGeometry.build("???", 0, -5, 0));
    }

    @Test
    void growExpandsEverySide() {
        assertEquals(new Rect(-2, -3, 4, 5), new Rect(-1, -2, 3, 4).grow(1));
    }
}
