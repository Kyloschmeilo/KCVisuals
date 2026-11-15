package com.kcvisuals.gui.packs;

import static org.junit.jupiter.api.Assertions.*;

import com.kcvisuals.pack.Category;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PreviewPickTest {
    static final Category CRYSTAL = new Category("end_crystal", "Crystal PvP", "Endkristall",
            "textures/item/end_crystal.png", List.of("textures/item/end_crystal*", "textures/entity/end_crystal/*"));
    static final Category SWORDS = new Category("swords", "Waffen", "Schwerter", "textures/item/diamond_sword.png",
            List.of("textures/item/diamond_sword*", "textures/item/iron_sword*"));

    @Test
    void exactPreviewPathWinsCaseInsensitively() {
        Set<String> e = new LinkedHashSet<>(List.of(
                "assets/minecraft/textures/entity/end_crystal/end_crystal.png",
                "assets/minecraft/textures/item/End_Crystal.png"));
        assertEquals("assets/minecraft/textures/item/End_Crystal.png", PreviewPick.find(e, CRYSTAL));
    }

    @Test
    void legacyNameMatchesModernPreviewPath() {
        Set<String> e = Set.of("assets/minecraft/textures/items/diamond_sword.png");
        assertEquals("assets/minecraft/textures/items/diamond_sword.png", PreviewPick.find(e, SWORDS));
    }

    @Test
    void fallsBackToAnotherPngOfTheCategoryPreferringItems() {
        Set<String> e = new LinkedHashSet<>(List.of(
                "assets/minecraft/textures/entity/end_crystal/end_crystal.png",
                "assets/minecraft/textures/item/end_crystal.png.mcmeta"));
        assertEquals("assets/minecraft/textures/entity/end_crystal/end_crystal.png", PreviewPick.find(e, CRYSTAL));
        Set<String> s = new LinkedHashSet<>(List.of(
                "assets/minecraft/textures/entity/end_crystal/x.png",
                "assets/minecraft/models/item/diamond_sword.json",
                "assets/minecraft/textures/item/iron_sword.png"));
        assertEquals("assets/minecraft/textures/item/iron_sword.png", PreviewPick.find(s, SWORDS));
    }

    @Test
    void nullWhenNoPng() {
        assertNull(PreviewPick.find(Set.of("assets/minecraft/models/item/diamond_sword.json", "pack.png"), SWORDS));
    }

    @Test
    void largeItemTextureWithCustomModelIsAnAtlas() {
        String tex = "assets/minecraft/textures/item/end_crystal.png";
        Set<String> withModel = Set.of(tex, "assets/minecraft/models/item/end_crystal.json");
        Set<String> withItemDef = Set.of(tex, "assets/minecraft/items/End_Crystal.json");
        Set<String> textureOnly = Set.of(tex);
        assertTrue(PreviewPick.isModelAtlas(withModel, tex, 128, 128));
        assertTrue(PreviewPick.isModelAtlas(withItemDef, tex, 64, 32));
        // icon-sized texture: shown as-is even with a custom model
        assertFalse(PreviewPick.isModelAtlas(withModel, tex, 32, 32));
        assertFalse(PreviewPick.isModelAtlas(withModel, tex, 16, 16));
        // HD texture without a custom model: a real (large) icon, shown as-is
        assertFalse(PreviewPick.isModelAtlas(textureOnly, tex, 128, 128));
        // a model for a different item does not count
        assertFalse(PreviewPick.isModelAtlas(Set.of(tex, "assets/minecraft/models/item/end_crystal_2.json"), tex, 128, 128));
    }

    @Test
    void atlasRuleOnlyAppliesToItemTextures() {
        String entity = "assets/minecraft/textures/entity/end_crystal/end_crystal.png";
        Set<String> e = Set.of(entity, "assets/minecraft/models/item/end_crystal.json");
        assertFalse(PreviewPick.isModelAtlas(e, entity, 64, 32));
        assertFalse(PreviewPick.isModelAtlas(e, null, 64, 64));
        // legacy textures/items/ path maps to the modern item name
        String legacy = "assets/minecraft/textures/items/diamond_sword.png";
        assertTrue(PreviewPick.isModelAtlas(Set.of(legacy, "assets/minecraft/models/item/diamond_sword.json"), legacy, 64, 64));
    }

    @Test
    void firstFrameOfVerticalStrips() {
        assertArrayEquals(new int[] {0, 0, 16, 16}, PreviewPick.firstFrame(16, 512));
        assertArrayEquals(new int[] {0, 0, 16, 20}, PreviewPick.firstFrame(16, 20));
        assertArrayEquals(new int[] {0, 0, 182, 22}, PreviewPick.firstFrame(182, 22));
    }

    @Test
    void fitWithinOnlyShrinks() {
        assertArrayEquals(new int[] {16, 16}, PreviewPick.fitWithin(16, 16, 128));
        assertArrayEquals(new int[] {128, 128}, PreviewPick.fitWithin(512, 512, 128));
        assertArrayEquals(new int[] {128, 15}, PreviewPick.fitWithin(1024, 120, 128));
    }
}
