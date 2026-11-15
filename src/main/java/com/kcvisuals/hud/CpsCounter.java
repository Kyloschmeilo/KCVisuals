package com.kcvisuals.hud;

import java.util.ArrayDeque;

/** Clicks per second: number of clicks within the last 1000 ms. Time is passed in so it stays testable. */
public final class CpsCounter {
    private static final long WINDOW_MS = 1000;
    private final ArrayDeque<Long> clicks = new ArrayDeque<>();

    public void click(long nowMs) {
        clicks.addLast(nowMs);
        prune(nowMs);
    }

    public int cps(long nowMs) {
        prune(nowMs);
        return clicks.size();
    }

    private void prune(long nowMs) {
        while (!clicks.isEmpty() && nowMs - clicks.peekFirst() >= WINDOW_MS) {
            clicks.pollFirst();
        }
    }
}
