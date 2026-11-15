package com.kcvisuals.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.List;

public class ModeSetting extends Setting<String> {
    private final List<String> modes;

    public ModeSetting(String key, String name, String def, String... modes) {
        super(key, name, def);
        this.modes = List.of(modes);
        if (!this.modes.contains(def)) {
            throw new IllegalArgumentException("default not in modes: " + def);
        }
    }

    public List<String> modes() {
        return modes;
    }

    @Override
    public void set(String v) {
        value = v != null && modes.contains(v) ? v : defaultValue();
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement e) {
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()
                && modes.contains(e.getAsString())) {
            value = e.getAsString();
        }
    }
}
