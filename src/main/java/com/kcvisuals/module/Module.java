package com.kcvisuals.module;

import com.kcvisuals.module.setting.Setting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Module {
    private final String id;
    private final String name;
    private final String description;
    private final List<Setting<?>> settings = new ArrayList<>();
    private boolean enabled;

    protected Module(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    protected <S extends Setting<?>> S add(S setting) {
        settings.add(setting);
        return setting;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean on) {
        if (on == enabled) {
            return;
        }
        enabled = on;
        if (on) {
            onEnable();
        } else {
            onDisable();
        }
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public List<Setting<?>> settings() {
        return Collections.unmodifiableList(settings);
    }

    public void onEnable() {}

    public void onDisable() {}
}
