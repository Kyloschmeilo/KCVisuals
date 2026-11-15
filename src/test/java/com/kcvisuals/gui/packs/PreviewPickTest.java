package com.kcvisuals.gui.packs;

import static org.junit.jupiter.api.Assertions.*;

import com.kcvisuals.pack.Category;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
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

    static final String CUSTOM_MODEL = "{\"parent\":\"item/smaller_util\",\"textures\":{\"0\":\"item/end_crystal\"},"
            + "\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,16]}]}";
    static final String GENERATED = "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"item/end_crystal\"}}";
    static final String HANDHELD = "{\"parent\":\"item/handheld\",\"textures\":{\"layer0\":\"minecraft:item/diamond_sword\"}}";

    /** Reader over a fixed map of entry name to JSON text. */
    static Function<String, String> reader(Map<String, String> files) {
        return files::get;
    }

    @Test
    void customParentLargeTextureIsAtlas() {
        String tex = "assets/minecraft/textures/item/end_crystal.png";
        String model = "assets/minecraft/models/item/end_crystal.json";
        Function<String, String> r = reader(Map.of(model, CUSTOM_MODEL));
        assertTrue(PreviewPick.isModelAtlas(Set.of(tex, model), tex, 128, 128, r));
        // icon-sized texture: shown as-is even with a custom model
        assertFalse(PreviewPick.isModelAtlas(Set.of(tex, model), tex, 32, 32, r));
        // unreadable model counts as custom
        assertTrue(PreviewPick.isModelAtlas(Set.of(tex, model), tex, 128, 128, reader(Map.of())));
        // HD texture without any model of its own: a real (large) icon
        assertFalse(PreviewPick.isModelAtlas(Set.of(tex), tex, 128, 128, r));
        // a model for a different item does not count
        String other = "assets/minecraft/models/item/end_crystal_2.json";
        assertFalse(PreviewPick.isModelAtlas(Set.of(tex, other), tex, 128, 128, reader(Map.of(other, CUSTOM_MODEL))));
    }

    @Test
    void hdIconWithGeneratedParentIsNotAtlas() {
        String tex = "assets/minecraft/textures/item/end_crystal.png";
        String model = "assets/minecraft/models/item/end_crystal.json";
        assertFalse(PreviewPick.isModelAtlas(Set.of(tex, model), tex, 512, 512, reader(Map.of(model, GENERATED))));
        String sword = "assets/minecraft/textures/item/diamond_sword.png";
        String swordModel = "assets/minecraft/models/item/diamond_sword.json";
        assertFalse(PreviewPick.isModelAtlas(Set.of(sword, swordModel), sword, 64, 64, reader(Map.of(swordModel, HANDHELD))));
        // generated parent but no layer0: not a flat icon
        String noLayer = "{\"parent\":\"item/generated\",\"textures\":{\"particle\":\"item/x\"}}";
        assertTrue(PreviewPick.isModelAtlas(Set.of(tex, model), tex, 128, 128, reader(Map.of(model, noLayer))));
    }

    @Test
    void itemDefinitionResolvesToModelInPack() {
        String tex = "assets/minecraft/textures/item/end_crystal.png";
        String def = "assets/minecraft/items/End_Crystal.json";
        String flat = "assets/minecraft/models/item/crystal_flat.json";
        String fancy = "assets/minecraft/models/item/crystal_3d.json";
        String toFlat = "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/crystal_flat\"}}";
        String toFancy = "{\"model\":{\"type\":\"minecraft:select\",\"cases\":[{\"model\":{\"type\":\"model\","
                + "\"model\":\"item/crystal_flat\"}}],\"fallback\":{\"type\":\"minecraft:model\",\"model\":\"item/crystal_3d\"}}}";
        String toVanilla = "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/end_crystal\"}}";
        Map<String, String> files = Map.of(flat, GENERATED, fancy, CUSTOM_MODEL);
        Set<String> e = Set.of(tex, def, flat, fancy);
        assertFalse(PreviewPick.isModelAtlas(e, tex, 128, 128, reader(with(files, def, toFlat))));
        assertTrue(PreviewPick.isModelAtlas(e, tex, 128, 128, reader(with(files, def, toFancy))));
        // model not shipped by the pack: cannot be shown to be flat, keep the 3D badge
        assertTrue(PreviewPick.isModelAtlas(e, tex, 128, 128, reader(with(files, def, toVanilla))));
    }

    @Test
    void itemsDefinitionTakesPrecedenceOverModelsItem() {
        String tex = "assets/minecraft/textures/item/end_crystal.png";
        String def = "assets/minecraft/items/end_crystal.json";
        String model = "assets/minecraft/models/item/end_crystal.json";
        String fancy = "assets/minecraft/models/item/crystal_3d.json";
        String flat = "assets/minecraft/models/item/crystal_flat.json";
        Set<String> e = Set.of(tex, def, model, fancy, flat);
        // flat models/item/end_crystal.json, but the item definition points at a custom 3D model -> atlas
        Map<String, String> toFancy = Map.of(
                def, "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/crystal_3d\"}}",
                model, GENERATED, fancy, CUSTOM_MODEL, flat, GENERATED);
        assertTrue(PreviewPick.isModelAtlas(e, tex, 128, 128, reader(toFancy)));
        // the reverse: custom models/item/end_crystal.json, but the item definition uses a flat model -> HD icon
        Map<String, String> toFlat = Map.of(
                def, "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"item/crystal_flat\"}}",
                model, CUSTOM_MODEL, fancy, CUSTOM_MODEL, flat, GENERATED);
        assertFalse(PreviewPick.isModelAtlas(e, tex, 128, 128, reader(toFlat)));
        // without an item definition, models/item decides
        assertTrue(PreviewPick.isModelAtlas(Set.of(tex, model), tex, 128, 128, reader(toFlat)));
        assertFalse(PreviewPick.isModelAtlas(Set.of(tex, model), tex, 128, 128, reader(toFancy)));
    }

    @Test
    void readCappedReturnsNullForOversizedEntries() throws java.io.IOException {
        byte[] ten = new byte[10];
        assertArrayEquals(ten, PreviewPick.readCapped(new java.io.ByteArrayInputStream(ten), 10));
        assertArrayEquals(ten, PreviewPick.readCapped(new java.io.ByteArrayInputStream(ten), 11));
        assertNull(PreviewPick.readCapped(new java.io.ByteArrayInputStream(new byte[11]), 10));
        // an endless stream is never buffered beyond the cap
        java.io.InputStream endless = new java.io.InputStream() {
            @Override
            public int read() {
                return 0;
            }
        };
        assertNull(PreviewPick.readCapped(endless, 1024));
        assertEquals(0, PreviewPick.readCapped(java.io.InputStream.nullInputStream(), 10).length);
    }

    @Test
    void findEntryPrefersModernPathThenSortedOrder() {
        String legacy = "assets/minecraft/textures/items/diamond_sword.png";
        String modern = "assets/minecraft/textures/item/diamond_sword.png";
        String modernUpper = "assets/minecraft/textures/item/Diamond_Sword.png";
        Set<String> e = new LinkedHashSet<>(List.of(legacy, modernUpper, modern));
        assertEquals(modernUpper, PreviewPick.findEntry(e, "textures/item/diamond_sword.png"));
        assertEquals(legacy, PreviewPick.findEntry(Set.of(legacy), "textures/item/diamond_sword.png"));
        assertNull(PreviewPick.findEntry(e, "textures/item/iron_sword.png"));
    }

    @Test
    void atlasRuleOnlyAppliesToItemTextures() {
        String entity = "assets/minecraft/textures/entity/end_crystal/end_crystal.png";
        String model = "assets/minecraft/models/item/end_crystal.json";
        Function<String, String> r = reader(Map.of(model, CUSTOM_MODEL));
        Set<String> e = Set.of(entity, model);
        assertFalse(PreviewPick.isModelAtlas(e, entity, 64, 32, r));
        assertFalse(PreviewPick.isModelAtlas(e, null, 64, 64, r));
        // legacy textures/items/ path maps to the modern item name
        String legacy = "assets/minecraft/textures/items/diamond_sword.png";
        String swordModel = "assets/minecraft/models/item/diamond_sword.json";
        assertTrue(PreviewPick.isModelAtlas(Set.of(legacy, swordModel), legacy, 64, 64,
                reader(Map.of(swordModel, CUSTOM_MODEL))));
    }

    @Test
    void flatIconModelDetection() {
        assertTrue(PreviewPick.isFlatIconModel(GENERATED));
        assertTrue(PreviewPick.isFlatIconModel(HANDHELD));
        assertTrue(PreviewPick.isFlatIconModel("{\"parent\":\"builtin/generated\",\"textures\":{\"layer0\":\"x\"}}"));
        assertFalse(PreviewPick.isFlatIconModel(CUSTOM_MODEL));
        assertFalse(PreviewPick.isFlatIconModel("not json"));
        assertFalse(PreviewPick.isFlatIconModel(null));
    }

    private static Map<String, String> with(Map<String, String> base, String k, String v) {
        Map<String, String> m = new HashMap<>(base);
        m.put(k, v);
        return m;
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
