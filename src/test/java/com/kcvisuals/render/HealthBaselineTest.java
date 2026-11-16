package com.kcvisuals.render;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HealthBaselineTest {
    @Test
    void reportsDropBetweenVisibleTicks() {
        HealthBaseline b = new HealthBaseline();
        assertEquals(0f, b.observe(1, true, 20f)); // first sighting: no baseline yet
        b.endTick();
        assertEquals(1.5f, b.observe(1, true, 18.5f), 1e-6);
        b.endTick();
        assertEquals(0f, b.observe(1, true, 19f)); // healing is not a hit
        b.endTick();
        assertEquals(0f, b.observe(1, true, 19f));
    }

    @Test
    void invisibleEntityResetsBaseline() {
        HealthBaseline b = new HealthBaseline();
        b.observe(7, true, 20f);
        b.endTick();
        // hit while hidden (behind a wall / outside the view)
        assertEquals(0f, b.observe(7, false, 12f));
        b.endTick();
        assertEquals(0, b.size());
        // visible again: the hidden drop is never reported
        assertEquals(0f, b.observe(7, true, 12f));
        b.endTick();
        assertEquals(2f, b.observe(7, true, 10f), 1e-6);
    }

    @Test
    void unobservedEntityIsForgotten() {
        HealthBaseline b = new HealthBaseline();
        b.observe(3, true, 20f);
        b.endTick();
        b.endTick(); // out of range: not observed this tick
        assertEquals(0f, b.observe(3, true, 5f));
    }

    @Test
    void tinyChangesAreIgnored() {
        HealthBaseline b = new HealthBaseline();
        b.observe(1, true, 20f);
        b.endTick();
        assertEquals(0f, b.observe(1, true, 19.999f));
    }
}
