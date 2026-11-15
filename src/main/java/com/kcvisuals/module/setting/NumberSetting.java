package com.kcvisuals.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class NumberSetting extends Setting<Double> {
    private final double min, max, step;

    public NumberSetting(String key, String name, double def, double min, double max, double step) {
        super(key, name, clamp(def, min, max, step));
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public double step() {
        return step;
    }

    public float getFloat() {
        return value.floatValue();
    }

    public int getInt() {
        return (int) Math.round(value);
    }

    static double clamp(double v, double min, double max, double step) {
        if (Double.isNaN(v)) {
            v = min;
        }
        double c = Math.max(min, Math.min(max, v));
        if (step > 0) {
            c = min + Math.round((c - min) / step) * step;
            c = Math.max(min, Math.min(max, c));
        }
        return c;
    }

    @Override
    public void set(Double v) {
        value = clamp(v == null ? defaultValue() : v, min, max, step);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement e) {
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
            set(e.getAsDouble());
        }
    }
}
