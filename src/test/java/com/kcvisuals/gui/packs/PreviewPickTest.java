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
