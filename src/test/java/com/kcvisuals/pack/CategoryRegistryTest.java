package com.kcvisuals.pack;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class CategoryRegistryTest {
    static final List<String> REQUIRED = List.of(
            "swords", "axes", "mace", "bow", "crossbow", "trident", "arrows", "armor_netherite", "armor_diamond",
            "armor_layers", "elytra", "shield", "end_crystal", "obsidian", "respawn_anchor", "glowstone", "totem",
            "ender_pearl", "golden_apple", "wind_charge", "potions", "splash_particles", "cobweb", "water_lava",
            "fire", "explosion_particles", "crit_particles", "hotbar", "hud_icons", "crosshair", "inventory_gui", "sky");

    static CategoryRegistry bundled() throws IOException {
        try (InputStream in = CategoryRegistryTest.class.getResourceAsStream("/assets/kcvisuals/categories.json")) {
            return CategoryRegistry.load(in);
        }
    }

    @Test
    void bundledFileHasAllRequiredCategories() throws IOException {
        CategoryRegistry reg = bundled();
        for (String id : REQUIRED) {
            Category c = reg.get(id);
            assertNotNull(c, id);
            assertFalse(c.paths().isEmpty(), id);
            assertFalse(c.group().isBlank(), id);
            assertFalse(c.displayName().isBlank(), id);
            assertTrue(c.previewPath().startsWith("textures/"), id);
        }
        assertEquals(REQUIRED.size(), reg.all().size());
    }

    @Test
    void globAndExactMatching() {
        String json = "{\"categories\":[{\"id\":\"x\",\"group\":\"g\",\"displayName\":\"X\",\"previewPath\":\"textures/a.png\","
                + "\"paths\":[\"textures/entity/end_crystal/*\",\"models/item/bow.json\"]}]}";
        CategoryRegistry reg = CategoryRegistry.load(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        Category c = reg.get("x");
        assertTrue(c.matches("textures/entity/end_crystal/end_crystal.png"));
        assertTrue(c.matches("textures/entity/end_crystal/sub/x.png"));
        assertFalse(c.matches("textures/entity/end_crystal.png"));
        assertTrue(c.matches("models/item/bow.json"));
        assertFalse(c.matches("models/item/bowl.json"));
    }

    @Test
    void bowDoesNotMatchBowl() throws IOException {
        Category bow = bundled().get("bow");
        assertTrue(bow.matches("textures/item/bow.png"));
        assertTrue(bow.matches("textures/item/bow_pulling_1.png.mcmeta"));
        assertFalse(bow.matches("textures/item/bowl.png"));
        assertFalse(bow.matches("models/item/bowl.json"));
    }

    @Test
    void legacyNormalisation() {
        assertEquals("textures/item/diamond_sword.png", LegacyPaths.normalise("textures/items/diamond_sword.png"));
        assertEquals("textures/block/cobweb.png", LegacyPaths.normalise("textures/blocks/web.png"));
        assertEquals("textures/item/golden_sword.png", LegacyPaths.normalise("textures/items/gold_sword.png"));
        assertEquals("textures/item/golden_sword.png", LegacyPaths.normalise("textures/item/gold_sword.png"));
        assertEquals("textures/item/wooden_axe.png", LegacyPaths.normalise("textures/items/wood_axe.png"));
        assertEquals("textures/item/ender_pearl.png", LegacyPaths.normalise("textures/items/ender_pearl.png"));
        assertEquals("textures/item/totem_of_undying.png", LegacyPaths.normalise("textures/items/totem.png"));
        assertEquals("textures/entity/equipment/humanoid/diamond.png",
                LegacyPaths.normalise("textures/models/armor/diamond_layer_1.png"));
        assertEquals("textures/entity/equipment/humanoid_leggings/gold.png",
                LegacyPaths.normalise("textures/models/armor/gold_layer_2.png"));
        assertEquals("textures/entity/equipment/wings/elytra.png", LegacyPaths.normalise("textures/entity/elytra.png"));
        assertEquals("textures/entity/end_crystal/end_crystal.png",
                LegacyPaths.normalise("textures/entity/endercrystal/endercrystal.png"));
        // already modern paths are untouched
        assertEquals("textures/gui/sprites/hud/hotbar.png", LegacyPaths.normalise("textures/gui/sprites/hud/hotbar.png"));
        assertEquals("models/item/gold_ingot.json", LegacyPaths.normalise("models/item/gold_ingot.json"));
    }

    @Test
    void fireLayerLegacyMapsToFire() throws IOException {
        assertEquals("textures/block/fire_0.png", LegacyPaths.normalise("textures/blocks/fire_layer_0.png"));
        assertEquals("textures/block/fire_1.png.mcmeta", LegacyPaths.normalise("textures/blocks/fire_layer_1.png.mcmeta"));
        assertTrue(bundled().get("fire").matches(LegacyPaths.normalise("textures/blocks/fire_layer_0.png")));
    }

    @Test
    void legacyPotionBottleMapsToPotions() throws IOException {
        assertEquals("textures/item/potion.png", LegacyPaths.normalise("textures/items/potion_bottle_drinkable.png"));
        assertEquals("textures/item/splash_potion.png", LegacyPaths.normalise("textures/items/potion_bottle_splash.png"));
        assertEquals("textures/item/lingering_potion.png", LegacyPaths.normalise("textures/items/potion_bottle_lingering.png"));
        assertEquals("textures/item/glass_bottle.png", LegacyPaths.normalise("textures/items/potion_bottle_empty.png"));
        assertEquals("textures/item/potion_overlay.png", LegacyPaths.normalise("textures/items/potion_overlay.png"));
        assertTrue(bundled().get("potions").matches(LegacyPaths.normalise("textures/items/potion_bottle_splash.png")));
    }
}
