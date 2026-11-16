package com.kcvisuals.render;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HitMathTest {
    @Test
    void formatsTwoDecimalsWithMinus() {
        assertEquals("-1.00", HitMath.formatDamage(1.0));
        assertEquals("-0.50", HitMath.formatDamage(0.5));
        assertEquals("-12.35", HitMath.formatDamage(12.345678));
        assertEquals("-3.00", HitMath.formatDamage(2.999));
    }

    @Test
    void formatIgnoresLocale() {
        java.util.Locale old = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY);
            assertEquals("-4.25", HitMath.formatDamage(4.25));
        } finally {
            java.util.Locale.setDefault(old);
        }
    }

    @Test
    void numberRisesMonotonicallyAndFadesOut() {
        assertEquals(0.0, HitMath.rise(0f), 1e-6);
        float last = -1f;
        for (int i = 0; i <= 20; i++) {
            float r = HitMath.rise(i / 20f);
            assertTrue(r >= last, "rise must not go down at " + i);
            last = r;
        }
        assertEquals(HitMath.RISE_BLOCKS, HitMath.rise(1f), 1e-6);
        assertEquals(1f, HitMath.fade(0f), 1e-6);
        assertEquals(1f, HitMath.fade(0.5f), 1e-6);
        assertEquals(0f, HitMath.fade(1f), 1e-6);
        assertTrue(HitMath.fade(0.8f) > 0f && HitMath.fade(0.8f) < 1f);
    }

    @Test
    void popScaleSettlesToOne() {
        assertTrue(HitMath.pop(0f) > 1f);
        assertEquals(1f, HitMath.pop(0.3f), 1e-6);
        assertEquals(1f, HitMath.pop(1f), 1e-6);
    }

    @Test
    void overlayAlphaIsInvertedTintStrength() {
        // vanilla hurt overlay texel 0xB2FF0000 == 30 % red tint
        assertEquals(0xB2FF0000, HitMath.overlayTexel(0x4DFF0000));
        assertEquals(0x009B5CFF, HitMath.overlayTexel(0xFF9B5CFF));
        assertEquals(0xFF9B5CFF, HitMath.overlayTexel(0x009B5CFF));
    }

    @Test
    void colorLerpEndpointsAndMiddle() {
        assertEquals(0xFFFF0000, Shapes.lerpColor(0xFFFF0000, 0xFF0000FF, 0f));
        assertEquals(0xFF0000FF, Shapes.lerpColor(0xFFFF0000, 0xFF0000FF, 1f));
        assertEquals(0xFF800080, Shapes.lerpColor(0xFFFF0000, 0xFF0000FF, 0.5f));
        assertEquals(0xFF0000FF, Shapes.lerpColor(0xFFFF0000, 0xFF0000FF, 7f));
    }
}
