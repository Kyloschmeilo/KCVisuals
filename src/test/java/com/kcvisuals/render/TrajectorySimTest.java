package com.kcvisuals.render;

import static org.junit.jupiter.api.Assertions.*;

import com.kcvisuals.render.TrajectorySim.Vec;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrajectorySimTest {
    @Test
    void pearlFallsAndSlows() {
        Vec v = new Vec(1.2, 0.6, 0.4);
        List<Vec> pts = TrajectorySim.simulate(new Vec(0, 64, 0), v, 0.03, 0.99, 40);
        assertEquals(41, pts.size());
        double lastDy = Double.POSITIVE_INFINITY;
        for (int i = 1; i < pts.size(); i++) {
            double dy = pts.get(i).y() - pts.get(i - 1).y();
            assertTrue(dy < lastDy, "y velocity must decrease at step " + i);
            lastDy = dy;
        }
        Vec a = pts.get(9), b = pts.get(10);
        double h = Math.hypot(b.x() - a.x(), b.z() - a.z());
        assertEquals(Math.hypot(v.x(), v.z()) * Math.pow(0.99, 10), h, 1e-6);
    }

    @Test
    void windChargeIsStraight() {
        Vec v = new Vec(0.9, 0.3, -1.1);
        List<Vec> pts = TrajectorySim.simulate(new Vec(5, 70, 5), v, 0.0, 1.0, 30);
        Vec s = pts.get(0);
        for (int i = 1; i < pts.size(); i++) {
            Vec p = pts.get(i);
            assertEquals(s.x() + v.x() * i, p.x(), 1e-9);
            assertEquals(s.y() + v.y() * i, p.y(), 1e-9);
            assertEquals(s.z() + v.z() * i, p.z(), 1e-9);
        }
    }

    @Test
    void presetsMatchVanillaConstants() {
        assertEquals(0.03, TrajectorySim.PEARL.gravity());
        assertEquals(0.99, TrajectorySim.PEARL.drag());
        assertEquals(0.05, TrajectorySim.ARROW.gravity());
        assertEquals(3.0, TrajectorySim.arrowSpeed(1.0), 1e-9);
        assertEquals(2.5, TrajectorySim.TRIDENT.speed());
        assertEquals(0.5, TrajectorySim.SPLASH_POTION.speed());
        assertEquals(0.0, TrajectorySim.WIND_CHARGE.gravity());
    }
}
