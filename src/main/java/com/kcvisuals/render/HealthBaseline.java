package com.kcvisuals.render;

import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;

/**
 * Per-tick health bookkeeping for damage numbers. Only entities observed as visible keep a baseline; an entity that is
 * not visible (or not observed at all) in a tick loses it, so a drop is only ever reported between two consecutive
 * visible observations and never against an old baseline from before it was hidden.
 */
public final class HealthBaseline {
    /** Smallest health decrease that counts as a hit. */
    public static final float MIN_DROP = 0.005f;

    private Int2FloatOpenHashMap last = new Int2FloatOpenHashMap();
    private Int2FloatOpenHashMap current = new Int2FloatOpenHashMap();

    /**
     * Records one entity for the current tick. Returns the health lost since the previous tick's visible observation,
     * or 0 if there is none (first sighting, healed, not visible).
     */
    public float observe(int id, boolean visible, float health) {
        if (!visible) {
            return 0f;
        }
        current.put(id, health);
        if (!last.containsKey(id)) {
            return 0f;
        }
        float drop = last.get(id) - health;
        return drop >= MIN_DROP ? drop : 0f;
    }

    /** Ends the tick: only this tick's visible observations remain as baselines. */
    public void endTick() {
        Int2FloatOpenHashMap t = last;
        last = current;
        current = t;
        current.clear();
    }

    public void clear() {
        last.clear();
        current.clear();
    }

    /** Number of entities with a baseline (for tests). */
    public int size() {
        return last.size();
    }
}
