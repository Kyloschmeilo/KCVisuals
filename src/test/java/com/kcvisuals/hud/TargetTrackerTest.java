package com.kcvisuals.hud;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TargetTrackerTest {
    @Test
    void expiredTargetIsCleared() {
        TargetTracker<String> t = new TargetTracker<>(8000);
        t.set("zombie", 1000);
        assertEquals("zombie", t.current(8999, s -> true));
        assertNull(t.current(9001, s -> true));
        assertFalse(t.holds());
    }

    @Test
    void invalidTargetIsDropped() {
        TargetTracker<String> t = new TargetTracker<>(8000);
        t.set("dead", 0);
        assertNull(t.current(10, s -> false));
        assertFalse(t.holds());
    }

    @Test
    void clearDropsReference() {
        TargetTracker<String> t = new TargetTracker<>(8000);
        t.set("x", 0);
        t.clear();
        assertNull(t.current(1, s -> true));
    }
}
