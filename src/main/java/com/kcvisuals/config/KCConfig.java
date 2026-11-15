package com.kcvisuals.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kcvisuals.module.Module;
import com.kcvisuals.module.ModuleManager;
import com.kcvisuals.module.setting.Setting;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * config/kcvisuals.json: module enabled flags and settings plus a raw {@code hud} slot that the HUD layout
 * (de)serialises itself into. Unknown keys are ignored, missing keys keep defaults.
 */
public class KCConfig {
    private static final Logger LOG = LoggerFactory.getLogger("KC Visuals");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private JsonObject hud;

    public KCConfig() {
        this(new JsonObject());
    }

    public KCConfig(JsonObject hud) {
        this.hud = hud == null ? new JsonObject() : hud;
    }

    public JsonObject hud() {
        return hud;
    }

    public void setHud(JsonObject hud) {
        this.hud = hud == null ? new JsonObject() : hud;
    }

    public void save(Path file, ModuleManager modules) {
        JsonObject root = new JsonObject();
        JsonObject mods = new JsonObject();
        for (Module m : modules.all()) {
            JsonObject o = new JsonObject();
            o.addProperty("enabled", m.enabled());
            JsonObject s = new JsonObject();
            for (Setting<?> st : m.settings()) {
                s.add(st.key(), st.toJson());
            }
            o.add("settings", s);
            mods.add(m.id(), o);
        }
        root.add("modules", mods);
        root.add("hud", hud);
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOG.error("Konnte Konfiguration nicht speichern", e);
        }
    }

    public static KCConfig load(Path file, ModuleManager modules) {
        KCConfig cfg = new KCConfig();
        if (!Files.isRegularFile(file)) {
            return cfg;
        }
        JsonObject root;
        try {
            JsonElement el = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!el.isJsonObject()) {
                throw new IllegalStateException("root is not an object");
            }
            root = el.getAsJsonObject();
        } catch (Exception e) {
            LOG.error("Konfiguration beschaedigt, verwende Standardwerte (Sicherung: kcvisuals.json.bak)", e);
            backup(file);
            return cfg;
        }
        // The hud slot is loaded first and on its own: a broken module must never cost the user their layout.
        try {
            JsonElement h = root.get("hud");
            if (h != null && h.isJsonObject()) {
                cfg.hud = h.getAsJsonObject();
            }
        } catch (RuntimeException e) {
            LOG.error("Fehler beim Lesen der HUD-Konfiguration", e);
        }
        JsonElement mods = root.get("modules");
        if (mods != null && mods.isJsonObject()) {
            for (Module m : modules.all()) {
                try {
                    loadModule(m, mods.getAsJsonObject().get(m.id()));
                } catch (RuntimeException e) {
                    LOG.error("Fehler beim Lesen des Moduls {}", m.id(), e);
                }
            }
        }
        return cfg;
    }

    private static void loadModule(Module m, JsonElement me) {
        if (me == null || !me.isJsonObject()) {
            return;
        }
        JsonObject mo = me.getAsJsonObject();
        JsonElement st = mo.get("settings");
        if (st != null && st.isJsonObject()) {
            for (Setting<?> s : m.settings()) {
                try {
                    s.fromJson(st.getAsJsonObject().get(s.key()));
                } catch (RuntimeException e) {
                    LOG.error("Fehler beim Lesen der Einstellung {}.{}", m.id(), s.key(), e);
                }
            }
        }
        JsonElement en = mo.get("enabled");
        if (en != null && en.isJsonPrimitive() && en.getAsJsonPrimitive().isBoolean()) {
            try {
                m.setEnabled(en.getAsBoolean());
            } catch (RuntimeException e) {
                LOG.error("Modul {} konnte nicht aktiviert werden", m.id(), e);
            }
        }
    }

    private static void backup(Path file) {
        try {
            Files.move(file, file.resolveSibling(file.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.error("Konnte Sicherung nicht anlegen", e);
        }
    }
}
