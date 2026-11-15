package com.kcvisuals.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class BoolSetting extends Setting<Boolean> {
    public BoolSetting(String key, String name, boolean def) {
        super(key, name, def);
    }

    @Override
    public void set(Boolean v) {
        value = v != null && v;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement e) {
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) {
            value = e.getAsBoolean();
        }
    }
}
