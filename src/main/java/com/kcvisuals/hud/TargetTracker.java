package com.kcvisuals.hud;

import java.util.function.Predicate;

/** Remembers the last target for a limited time; the reference is dropped as soon as it expires or becomes invalid. */
public final class TargetTracker<T> {
    private final long showMs;
    private T target;
    private long setAt;

    public TargetTracker(long showMs) {
        this.showMs = showMs;
    }

    public void set(T t, long nowMs) {
        target = t;
        setAt = nowMs;
    }

    /** The target if it is still fresh and {@code valid}; otherwise clears it and returns null. */
    public T current(long nowMs, Predicate<T> valid) {
        if (target == null) {
            return null;
        }
        if (nowMs - setAt > showMs || !valid.test(target)) {
            target = null;
            return null;
        }
        return target;
    }

    public void clear() {
        target = null;
    }

    public boolean holds() {
        return target != null;
    }
}
