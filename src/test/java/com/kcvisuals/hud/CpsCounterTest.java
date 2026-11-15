package com.kcvisuals.hud;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CpsCounterTest {
    @Test
    void countsClicksInLastSecond() {
        CpsCounter c = new CpsCounter();
        c.click(0);
        c.click(100);
        c.click(500);
        assertEquals(3, c.cps(600));
        assertEquals(2, c.cps(1050));
        assertEquals(1, c.cps(1400));
        assertEquals(0, c.cps(1600));
    }

    @Test
    void emptyIsZero() {
        assertEquals(0, new CpsCounter().cps(12345));
    }

    @Test
    void manyClicksDoNotGrowUnbounded() {
        CpsCounter c = new CpsCounter();
        for (int i = 0; i < 10_000; i++) {
            c.click(i);
        }
        assertEquals(1000, c.cps(9_999));
    }
}
