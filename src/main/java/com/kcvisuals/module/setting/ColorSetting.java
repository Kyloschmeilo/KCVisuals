package com.kcvisuals.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** ARGB colour stored as a signed int. */
public class ColorSetting extends Setting<Integer> {
    public ColorSetting(String key, String name, int argb) {
        super(key, name, argb);
    }

    @Override
    public void set(Integer v) {
        value = v == null ? defaultValue() : v;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement e) {
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
            value = (int) e.getAsLong();
        }
    }
}
