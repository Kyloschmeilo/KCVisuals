package com.kcvisuals.render;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure projectile integrator (no Minecraft types). Per tick, like vanilla thrown entities: velocity.y -= gravity,
 * velocity *= drag, position += velocity. Block collision is done by the caller (raycast per segment).
 */
public final class TrajectorySim {
    public record Vec(double x, double y, double z) {}

    /** Launch speed, gravity per tick, drag per tick and pitch offset (degrees) of one projectile kind. */
    public record Kind(double speed, double gravity, double drag, float pitchOffset) {}

    public static final Kind PEARL = new Kind(1.5, 0.03, 0.99, 0f);
    public static final Kind SNOWBALL = PEARL;
    public static final Kind EGG = PEARL;
    public static final Kind ARROW = new Kind(3.0, 0.05, 0.99, 0f);
    public static final Kind CROSSBOW_ARROW = new Kind(3.15, 0.05, 0.99, 0f);
    public static final Kind TRIDENT = new Kind(2.5, 0.05, 0.99, 0f);
    public static final Kind SPLASH_POTION = new Kind(0.5, 0.05, 0.99, -20f);
    public static final Kind XP_BOTTLE = new Kind(0.7, 0.07, 0.99, -20f);
    public static final Kind WIND_CHARGE = new Kind(1.5, 0.0, 1.0, 0f);

    private TrajectorySim() {}

    /** Bow arrow speed for a pull progress in [0, 1]. */
    public static double arrowSpeed(double charge) {
        return ARROW.speed() * charge;
    }

    /** Positions from {@code start} (index 0) through {@code maxSteps} ticks. */
    public static List<Vec> simulate(Vec start, Vec velocity, double gravity, double drag, int maxSteps) {
        double[] buf = new double[(maxSteps + 1) * 3];
        int n = simulateInto(buf, start.x(), start.y(), start.z(), velocity.x(), velocity.y(), velocity.z(), gravity, drag, maxSteps);
        List<Vec> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new Vec(buf[i * 3], buf[i * 3 + 1], buf[i * 3 + 2]));
        }
        return out;
    }

    /**
     * Allocation-free variant: writes xyz triples into {@code out} and returns the number of points
     * ({@code min(maxSteps + 1, out.length / 3)}).
     */
    public static int simulateInto(double[] out, double x, double y, double z, double vx, double vy, double vz,
                                   double gravity, double drag, int maxSteps) {
        int n = Math.min(maxSteps + 1, out.length / 3);
        if (n <= 0) {
            return 0;
        }
        out[0] = x;
        out[1] = y;
        out[2] = z;
        for (int i = 1; i < n; i++) {
            vy -= gravity;
            vx *= drag;
            vy *= drag;
            vz *= drag;
            x += vx;
            y += vy;
            z += vz;
            out[i * 3] = x;
            out[i * 3 + 1] = y;
            out[i * 3 + 2] = z;
        }
        return n;
    }
}
