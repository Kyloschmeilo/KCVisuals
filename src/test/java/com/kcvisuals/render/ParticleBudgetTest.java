package com.kcvisuals.render;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ParticleBudgetTest {
    @Test
    void fullAndZero() {
        ParticleBudget b = new ParticleBudget();
        for (int i = 0; i < 10; i++) {
            assertTrue(b.allow(1.0));
            assertFalse(b.allow(0.0));
        }
        assertEquals(37, b.take(37, 1.0));
        assertEquals(0, b.take(37, 0.0));
        assertEquals(0, b.take(0, 0.5));
    }

    @Test
    void singleSpawnsAccumulate() {
        ParticleBudget b = new ParticleBudget();
        int n = 0;
        for (int i = 0; i < 100; i++) {
            if (b.allow(0.25)) {
                n++;
            }
        }
        assertEquals(25, n);
        b.reset();
        n = 0;
        for (int i = 0; i < 30; i++) {
            if (b.allow(0.1)) {
                n++;
            }
        }
        assertEquals(3, n, "10 % of 30 single spawns despite each rounding to 0");
    }

    @Test
    void batchesCarryRemainder() {
        ParticleBudget b = new ParticleBudget();
        int total = 0;
        for (int i = 0; i < 10; i++) {
            int got = b.take(3, 0.5);
            assertTrue(got >= 1 && got <= 2);
            total += got;
        }
        assertEquals(15, total);
        assertEquals(50, new ParticleBudget().take(100, 0.5));
        assertEquals(33, new ParticleBudget().take(100, 0.33));
    }

    @Test
    void clampsMultiplier() {
        assertEquals(1.0, ParticleBudget.clamp(Double.NaN));
        assertEquals(0.0, ParticleBudget.clamp(-1));
        assertEquals(1.0, ParticleBudget.clamp(4));
        assertEquals(5, new ParticleBudget().take(5, 7.0));
    }
}
