package com.kcvisuals.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ModuleManager {
    private final Map<String, Module> byId = new LinkedHashMap<>();

    public <T extends Module> T register(T module) {
        if (byId.putIfAbsent(module.id(), module) != null) {
            throw new IllegalArgumentException("duplicate module id " + module.id());
        }
        return module;
    }

    public List<Module> all() {
        return Collections.unmodifiableList(new ArrayList<>(byId.values()));
    }

    public Module byId(String id) {
        return byId.get(id);
    }

    public <T extends Module> T get(Class<T> type) {
        for (Module m : byId.values()) {
            if (type.isInstance(m)) {
                return type.cast(m);
            }
        }
        return null;
    }
}
