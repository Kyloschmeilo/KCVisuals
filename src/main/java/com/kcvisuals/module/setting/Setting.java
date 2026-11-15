package com.kcvisuals.module.setting;

import com.google.gson.JsonElement;

/** One persisted, user-editable value of a module. Pure Java so it can be unit tested. */
public abstract class Setting<T> {
    private final String key;
    private final String name;
    protected T value;
    private final T defaultValue;

    protected Setting(String key, String name, T defaultValue) {
        this.key = key;
        this.name = name;
        this.value = defaultValue;
        this.defaultValue = defaultValue;
    }

    public String key() {
        return key;
    }

    public String name() {
        return name;
    }

    public T get() {
        return value;
    }

    public T defaultValue() {
        return defaultValue;
    }

    public abstract void set(T v);

    public void reset() {
        set(defaultValue);
    }

    public abstract JsonElement toJson();

    /** Lenient: wrong types or out-of-range values are coerced or ignored, never thrown. */
    public abstract void fromJson(JsonElement e);
}
