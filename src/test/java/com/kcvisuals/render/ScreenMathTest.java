package com.kcvisuals.render;

import static org.junit.jupiter.api.Assertions.*;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

class ScreenMathTest {
    @Test
    void stretchClampsToRange() {
        assertEquals(1f, ScreenMath.clampStretch(0.5));
        assertEquals(1.6f, ScreenMath.clampStretch(3.0));
        assertEquals(1.33f, ScreenMath.clampStretch(1.33), 1e-6);
        assertEquals(1f, ScreenMath.clampStretch(Double.NaN));
    }

    @Test
    void stretchedWidthMatchesMatrixScale() {
        float fov = (float) Math.toRadians(70);
        int w = 1920, h = 1080;
        float f = 1.33f;
        Matrix4f scaled = new Matrix4f().perspective(fov, (float) w / h, 0.05f, 100f).scaleLocal(f, 1f, 1f);
        Matrix4f viaWidth = new Matrix4f().perspective(fov, (float) ScreenMath.stretchedWidth(w, f) / h, 0.05f, 100f);
        Vector4f p = new Vector4f(1.5f, 0.7f, -4f, 1f);
        Vector4f a = scaled.transform(new Vector4f(p)), b = viaWidth.transform(new Vector4f(p));
        assertEquals(a.x / a.w, b.x / b.w, 2e-3, "same horizontal stretch");
        assertEquals(a.y / a.w, b.y / b.w, 1e-6, "vertical unchanged");
        // stretched x is wider than unstretched
        Vector4f o = new Matrix4f().perspective(fov, (float) w / h, 0.05f, 100f).transform(new Vector4f(p));
        assertEquals(f, (a.x / a.w) / (o.x / o.w), 1e-5);
        assertEquals(1920, ScreenMath.stretchedWidth(1920, 1.0));
        assertEquals(1200, ScreenMath.stretchedWidth(1920, 1.6));
        assertTrue(ScreenMath.stretchedWidth(1, 1.6) >= 1);
    }

    @Test
    void fireDropRange() {
        assertEquals(0f, ScreenMath.fireDrop(1.0), 1e-6);
        assertEquals(-ScreenMath.FIRE_MAX_DROP, ScreenMath.fireDrop(0.0), 1e-6);
        assertEquals(-0.3f, ScreenMath.fireDrop(0.4), 1e-6);
        assertEquals(0f, ScreenMath.fireDrop(5.0), 1e-6);
    }

    @Test
    void swingDurationScalesAndNeverHitsZero() {
        assertEquals(6, ScreenMath.swingDuration(6, 1.0));
        assertEquals(3, ScreenMath.swingDuration(6, 2.0));
        assertEquals(12, ScreenMath.swingDuration(6, 0.5));
        assertEquals(1, ScreenMath.swingDuration(1, 2.0));
        assertEquals(1, ScreenMath.swingDuration(0, 1.0));
        assertEquals(6, ScreenMath.swingDuration(6, 0.0));
    }

    @Test
    void fullbrightBoostsAndYieldsToDarkness() {
        // no darkness: vanilla arg == user gamma
        assertEquals(ScreenMath.FULLBRIGHT_GAMMA, ScreenMath.fullbrightGamma(0.5f, 0.5f, 1.0), 1e-6);
        assertEquals(0.5f + (ScreenMath.FULLBRIGHT_GAMMA - 0.5f) * 0.5f, ScreenMath.fullbrightGamma(0.5f, 0.5f, 0.5), 1e-5);
        // full darkness: exactly vanilla
        assertEquals(-0.5f, ScreenMath.fullbrightGamma(-0.5f, 0.5f, 1.0), 1e-6);
        // partial darkness lies between
        float half = ScreenMath.fullbrightGamma(0.0f, 0.5f, 1.0);
        assertTrue(half > 0.0f && half < ScreenMath.FULLBRIGHT_GAMMA);
        // monotone: more darkness, less boost
        float last = Float.MAX_VALUE;
        for (int i = 0; i <= 10; i++) {
            float d = i / 10f;
            float v = ScreenMath.fullbrightGamma(1.0f - d, 1.0f, 1.0);
            assertTrue(v <= last + 1e-6, "darkness " + d);
            last = v;
        }
    }

    @Test
    void hurtTilt() {
        assertEquals(0.0, ScreenMath.hurtTilt(1.0, true));
        assertEquals(0.7, ScreenMath.hurtTilt(0.7, false));
    }
}
