package com.kcvisuals.render;

/**
 * Deterministic particle thinning for a 0–1 multiplier. Single spawns are let through by accumulating the multiplier
 * (50 % lets every second one through, 25 % every fourth, ...) so small counts are not lost to rounding; batch counts
 * are scaled with the same carry.
 */
public final class ParticleBudget {
    private double carry;

    /** Clamps a multiplier to 0–1 (NaN → 1). */
    public static double clamp(double multiplier) {
        if (Double.isNaN(multiplier)) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, multiplier));
    }

    /** Whether the next single particle may spawn. */
    public boolean allow(double multiplier) {
        return take(1, multiplier) == 1;
    }

    /** How many of {@code count} requested particles may spawn. */
    public int take(int count, double multiplier) {
        double m = clamp(multiplier);
        if (count <= 0 || m <= 0.0) {
            return 0;
        }
        if (m >= 1.0) {
            return count;
        }
        double want = count * m + carry;
        int n = (int) Math.floor(want + 1e-9);
        carry = want - n;
        return Math.min(count, n);
    }

    public void reset() {
        carry = 0;
    }
}
