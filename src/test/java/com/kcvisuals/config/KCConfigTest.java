package com.kcvisuals.config;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.kcvisuals.module.Module;
import com.kcvisuals.module.ModuleManager;
import com.kcvisuals.module.setting.BoolSetting;
import com.kcvisuals.module.setting.ColorSetting;
import com.kcvisuals.module.setting.ModeSetting;
import com.kcvisuals.module.setting.NumberSetting;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KCConfigTest {
    static class Demo extends Module {
        final BoolSetting flag = add(new BoolSetting("flag", "Flag", false));
        final NumberSetting amount = add(new NumberSetting("amount", "Menge", 1, 0, 10, 0.5));
        final ColorSetting color = add(new ColorSetting("color", "Farbe", 0xFF9B5CFF));
        final ModeSetting mode = add(new ModeSetting("mode", "Modus", "a", "a", "b", "c"));
        int enables, disables;

        Demo() {
            super("demo", "Demo", "Test");
        }

        @Override
        public void onEnable() {
            enables++;
        }

        @Override
        public void onDisable() {
            disables++;
        }
    }

    private static ModuleManager manager() {
        ModuleManager m = new ModuleManager();
        m.register(new Demo());
        return m;
    }

    @Test
    void roundTripModulesAndSettings(@TempDir Path dir) {
        Path file = dir.resolve("config").resolve("kcvisuals.json");
        ModuleManager a = manager();
        Demo d = a.get(Demo.class);
        d.setEnabled(true);
        d.flag.set(true);
        d.amount.set(7.5);
        d.color.set(0x80112233);
        d.mode.set("c");
        JsonObject hud = new JsonObject();
        hud.addProperty("x", 3);
        new KCConfig(hud).save(file, a);

        ModuleManager b = manager();
        KCConfig loaded = KCConfig.load(file, b);
        Demo e = b.get(Demo.class);
        assertTrue(e.enabled());
        assertEquals(1, e.enables);
        assertTrue(e.flag.get());
        assertEquals(7.5, e.amount.get());
        assertEquals(0x80112233, e.color.get());
        assertEquals("c", e.mode.get());
        assertEquals(3, loaded.hud().get("x").getAsInt());
    }

    @Test
    void unknownModuleIgnored(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("c.json");
        Files.writeString(file, "{\"modules\":{\"ghost\":{\"enabled\":true},\"demo\":{\"enabled\":true,\"settings\":{\"nope\":1,\"flag\":true}}},\"extra\":5}");
        ModuleManager m = manager();
        KCConfig.load(file, m);
        Demo d = m.get(Demo.class);
        assertTrue(d.enabled());
        assertTrue(d.flag.get());
        assertEquals(1.0, d.amount.get());
    }

    @Test
    void outOfRangeNumberClamped(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("c.json");
        Files.writeString(file, "{\"modules\":{\"demo\":{\"settings\":{\"amount\":99,\"mode\":\"zzz\"}}}}");
        ModuleManager m = manager();
        KCConfig.load(file, m);
        assertEquals(10.0, m.get(Demo.class).amount.get());
        assertEquals("a", m.get(Demo.class).mode.get());
        Files.writeString(file, "{\"modules\":{\"demo\":{\"settings\":{\"amount\":-5}}}}");
        KCConfig.load(file, m);
        assertEquals(0.0, m.get(Demo.class).amount.get());
    }

    @Test
    void corruptFileFallsBackToDefaults(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("kcvisuals.json");
        Files.writeString(file, "{ this is not json");
        ModuleManager m = manager();
        KCConfig cfg = KCConfig.load(file, m);
        assertFalse(m.get(Demo.class).enabled());
        assertEquals(0, cfg.hud().size());
        assertTrue(Files.exists(dir.resolve("kcvisuals.json.bak")));
        assertEquals("{ this is not json", Files.readString(dir.resolve("kcvisuals.json.bak")));
    }

    @Test
    void missingFileGivesDefaults(@TempDir Path dir) {
        ModuleManager m = manager();
        KCConfig cfg = KCConfig.load(dir.resolve("none.json"), m);
        assertFalse(m.get(Demo.class).enabled());
        assertNotNull(cfg.hud());
    }

    static class Exploding extends Module {
        Exploding() {
            super("boom", "Boom", "wirft beim Laden");
        }

        @Override
        public void onEnable() {
            throw new IllegalStateException("kaputt");
        }
    }

    @Test
    void badModuleDoesNotDropHud(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("c.json");
        Files.writeString(file, "{\"modules\":{\"boom\":{\"enabled\":true},\"demo\":{\"enabled\":true}},"
                + "\"hud\":{\"layout\":{\"x\":1}}}");
        ModuleManager m = new ModuleManager();
        m.register(new Exploding());
        m.register(new Demo());
        KCConfig cfg = KCConfig.load(file, m);
        assertTrue(m.get(Demo.class).enabled(), "modules after the broken one still load");
        assertEquals(1, cfg.hud().getAsJsonObject("layout").get("x").getAsInt());
        Path out = dir.resolve("out.json");
        cfg.save(out, m);
        assertTrue(Files.readString(out).contains("\"layout\""), "hud survives the next save");
    }
}
