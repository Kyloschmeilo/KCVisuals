package com.kcvisuals.preview;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class ModelPreviewTest {
    static final int UP = 0xFF00FF00, DOWN = 0xFFFF00FF, NORTH = 0xFFFF0000, SOUTH = 0xFF0000FF, WEST = 0xFFFFFF00,
            EAST = 0xFF00FFFF;

    static ArgbImage solid(int argb) {
        ArgbImage img = new ArgbImage(4, 4);
        java.util.Arrays.fill(img.pixels(), argb);
        return img;
    }

    /** A full 16^3 cube, each face with its own single-colour texture, in the vanilla block/block gui pose. */
    static ResolvedModel colourCube() {
        String json = """
                {"textures": {"u": "t:up", "d": "t:down", "n": "t:north", "s": "t:south", "w": "t:west", "e": "t:east"},
                 "elements": [{"from": [0,0,0], "to": [16,16,16], "faces": {
                   "up": {"texture": "#u"}, "down": {"texture": "#d"}, "north": {"texture": "#n"},
                   "south": {"texture": "#s"}, "west": {"texture": "#w"}, "east": {"texture": "#e"}}}],
                 "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]}}}
                """;
        return ModelResolver.resolve("test:cube", Map.of("test:cube", json)::get);
    }

    static Function<String, ArgbImage> colourTextures() {
        Map<String, ArgbImage> t = new HashMap<>();
        t.put("t:up", solid(UP));
        t.put("t:down", solid(DOWN));
        t.put("t:north", solid(NORTH));
        t.put("t:south", solid(SOUTH));
        t.put("t:west", solid(WEST));
        t.put("t:east", solid(EAST));
        return t::get;
    }

    static int shaded(int argb, float f) {
        int r = Math.round(((argb >> 16) & 0xFF) * f), g = Math.round(((argb >> 8) & 0xFF) * f),
                b = Math.round((argb & 0xFF) * f);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    static int firstOpaqueRow(ArgbImage img, int x) {
        for (int y = 0; y < img.height(); y++) {
            if ((img.get(x, y) >>> 24) != 0) {
                return y;
            }
        }
        return -1;
    }

    static int opaqueColumns(ArgbImage img) {
        int n = 0;
        for (int x = 0; x < img.width(); x++) {
            if (firstOpaqueRow(img, x) >= 0) {
                n++;
            }
        }
        return n;
    }

    @Test
    void rendersSingleCubeWithDepth() {
        ResolvedModel cube = colourCube();
        assertEquals(ResolvedModel.Kind.MODEL, cube.kind());
        ArgbImage img = Scene.item(cube, colourTextures()).render(64, 0);
        int top = firstOpaqueRow(img, 32);
        assertTrue(top >= 0, "cube visible in the centre column");
        assertEquals(UP, img.get(32, top + 2), "up face at the top centre");
        int down = shaded(DOWN, Direction.DOWN.shade());
        for (int p : img.pixels()) {
            assertNotEquals(down, p, "down face hidden");
            assertNotEquals(DOWN, p);
        }
        // pose [30, 225, 0] shows the east (left) and north (right) faces below the top, with directional shading
        int left = img.get(32 - 10, firstOpaqueRow(img, 32 - 10) + 28);
        int right = img.get(32 + 10, firstOpaqueRow(img, 32 + 10) + 28);
        assertEquals(shaded(EAST, 0.6f), left);
        assertEquals(shaded(NORTH, 0.8f), right);
    }

    @Test
    void depthTestKeepsTheNearerFace() {
        // two horizontal planes seen from above: the higher one must win wherever both cover a pixel
        String json = """
                {"textures": {"a": "t:up", "b": "t:north"},
                 "elements": [
                   {"from": [0,12,0], "to": [16,12,16], "faces": {"up": {"texture": "#a"}}},
                   {"from": [0,4,0], "to": [16,4,16], "faces": {"up": {"texture": "#b"}}}],
                 "display": {"gui": {"rotation": [90, 0, 0], "scale": [1, 1, 1]}}}
                """;
        ResolvedModel m = ModelResolver.resolve("t:m", Map.of("t:m", json)::get);
        ArgbImage img = Scene.item(m, colourTextures()).render(32, 0);
        assertEquals(UP, img.get(16, 16));
    }

    @Test
    void alphaCutoutLeavesTransparentPixels() {
        ArgbImage tex = new ArgbImage(2, 2);
        tex.set(0, 0, 0xFFFFFFFF);
        tex.set(1, 0, 0x10FFFFFF); // alpha 16 < 0.1 * 255: discarded
        tex.set(0, 1, 0x00000000);
        tex.set(1, 1, 0xFFFFFFFF);
        String json = """
                {"textures": {"x": "t:x"},
                 "elements": [{"from": [0,0,8], "to": [16,16,8], "shade": false, "faces": {"south": {"texture": "#x", "uv": [0,0,16,16]}}}]}
                """;
        ResolvedModel m = ModelResolver.resolve("t:m", Map.of("t:m", json)::get);
        ArgbImage img = Scene.item(m, Map.of("t:x", tex)::get).render(32, 0);
        // identity gui transform (vanilla default for a model without display): the south face fills the slot
        assertEquals(0xFFFFFFFF, img.get(4, 4));
        assertEquals(0, img.get(28, 4) >>> 24, "low-alpha texel discarded");
        assertEquals(0, img.get(4, 28) >>> 24, "transparent texel discarded");
        assertEquals(0xFFFFFFFF, img.get(28, 28));
    }

    @Test
    void elementRotationApplied() {
        String flat = """
                {"textures": {"x": "t:up"},
                 "elements": [{"from": [4,0,8], "to": [12,16,8], %s "faces": {"south": {"texture": "#x"}, "north": {"texture": "#x"}}}]}
                """;
        ResolvedModel plain = ModelResolver.resolve("t:m", Map.of("t:m", flat.formatted(""))::get);
        ResolvedModel turned = ModelResolver.resolve("t:m", Map.of("t:m",
                flat.formatted("\"rotation\": {\"angle\": 45, \"axis\": \"y\", \"origin\": [8,8,8]},"))::get);
        int w0 = opaqueColumns(Scene.item(plain, colourTextures()).render(64, 0));
        int w1 = opaqueColumns(Scene.item(turned, colourTextures()).render(64, 0));
        assertEquals(32, w0, 2);
        assertTrue(w1 < w0 - 6, "45 degree y rotation narrows the silhouette: " + w1 + " vs " + w0);
        ResolvedModel rescaled = ModelResolver.resolve("t:m", Map.of("t:m", flat.formatted(
                "\"rotation\": {\"angle\": 45, \"axis\": \"y\", \"origin\": [8,8,8], \"rescale\": true},"))::get);
        int w2 = opaqueColumns(Scene.item(rescaled, colourTextures()).render(64, 0));
        assertEquals(w0, w2, 2, "rescale restores the width");
    }

    @Test
    void parentChainMergesTexturesAndResolvesRefs() {
        Map<String, String> models = new HashMap<>();
        models.put("minecraft:block/cube", """
                {"elements": [{"from": [0,0,0], "to": [16,16,16], "faces": {
                  "up": {"texture": "#up"}, "down": {"texture": "#down"}, "north": {"texture": "#north"}}}],
                 "display": {"gui": {"rotation": [30, 225, 0], "scale": [0.625, 0.625, 0.625]}}}
                """);
        models.put("minecraft:block/cube_all", """
                {"parent": "block/cube", "textures": {"up": "#all", "down": "#all", "north": "#all"}}
                """);
        models.put("pack:item/thing", """
                {"parent": "minecraft:block/cube_all", "textures": {"all": "pack:block/thing"}}
                """);
        ResolvedModel m = ModelResolver.resolve("pack:item/thing", models::get);
        assertEquals(ResolvedModel.Kind.MODEL, m.kind());
        assertEquals(1, m.elements().size());
        assertEquals("pack:block/thing", m.texture("#up"));
        assertEquals("pack:block/thing", m.texture("north"));
        assertNotNull(m.gui());
        assertEquals(225f, m.gui().rotation()[1]);
        assertEquals(List.of("pack:item/thing", "minecraft:block/cube_all", "minecraft:block/cube"), m.chain());
        assertEquals("minecraft:item/foo", ModelResolver.normalise("Item/Foo"));
    }

    @Test
    void generatedParentIsTreatedAsFlat() {
        Map<String, String> models = Map.of(
                "minecraft:item/diamond_sword", "{\"parent\": \"minecraft:item/handheld\", \"textures\": {\"layer0\": \"item/diamond_sword\"}}",
                "minecraft:item/handheld", "{\"parent\": \"item/generated\", \"display\": {}}",
                "minecraft:item/old", "{\"parent\": \"builtin/generated\", \"textures\": {\"layer0\": \"items/old\", \"layer1\": \"#layer0\"}}");
        ResolvedModel sword = ModelResolver.resolve("minecraft:item/diamond_sword", models::get);
        assertEquals(ResolvedModel.Kind.FLAT, sword.kind());
        assertEquals(List.of("minecraft:item/diamond_sword"), sword.layers());
        ResolvedModel old = ModelResolver.resolve("item/old", models::get);
        assertEquals(ResolvedModel.Kind.FLAT, old.kind());
        assertEquals(List.of("minecraft:items/old", "minecraft:items/old"), old.layers());

        ResolvedModel special = ModelResolver.resolve("item/shield", Map.of("minecraft:item/shield",
                "{\"parent\": \"builtin/entity\"}")::get);
        assertEquals(ResolvedModel.Kind.SPECIAL, special.kind());
        assertEquals(ResolvedModel.Kind.MISSING, ModelResolver.resolve("item/none", Map.<String, String>of()::get).kind());
    }

    @Test
    void cycleInParentChainTerminates() {
        Map<String, String> models = Map.of(
                "minecraft:a", "{\"parent\": \"b\", \"textures\": {\"x\": \"#y\", \"y\": \"#x\"}}",
                "minecraft:b", "{\"parent\": \"a\"}");
        ResolvedModel m = ModelResolver.resolve("a", models::get);
        assertEquals(ResolvedModel.Kind.MISSING, m.kind());
        assertNull(m.texture("#x"), "texture reference cycle resolves to nothing");
        // long chains stop at the depth cap
        Map<String, String> deep = new HashMap<>();
        for (int i = 0; i < 40; i++) {
            deep.put("minecraft:m" + i, "{\"parent\": \"m" + (i + 1) + "\"}");
        }
        assertTrue(ModelResolver.resolve("m0", deep::get).chain().size() <= ModelResolver.MAX_DEPTH);
    }

    @Test
    void malformedJsonIsIgnored() {
        assertEquals(ResolvedModel.Kind.MISSING, ModelResolver.resolve("a", Map.of("minecraft:a", "{nope")::get).kind());
        String weird = """
                {"elements": [{"from": [0,0], "to": [16,16,16]}, {"from": [0,0,0], "to": [16,16,"x"]}, 5,
                  {"from": [0,0,0], "to": [16,16,16], "faces": {"up": {"texture": "#a"}, "sideways": {"texture": "#a"}}}]}
                """;
        ResolvedModel m = ModelResolver.resolve("a", Map.of("minecraft:a", weird)::get);
        assertEquals(1, m.elements().size());
        assertEquals(1, m.elements().get(0).faces().size());
    }

    @Test
    void itemDefinitionPicksGuiCaseFallbackOrFirst() {
        assertEquals("minecraft:item/end_crystal", ItemDefinitions.pick("""
                {"model":{"type":"select","property":"display_context","cases":[{"model":{"type":"model","model":"item/end_crystal"},
                "when":["gui","ground","fixed"]}],"fallback":{"type":"model","model":"item/end_crystal_in_hand"}}}
                """).model());
        assertEquals("minecraft:item/diamond_sword", ItemDefinitions.pick("""
                {"model":{"type":"select","property":"component","component":"custom_name","cases":[{"when":"A",
                "model":{"type":"model","model":"item/custom/a"}}],"fallback":{"type":"model","model":"item/diamond_sword"}}}
                """).model());
        assertEquals("cm:item/breach", ItemDefinitions.pick("""
                {"model":{"type":"minecraft:select","property":"component","cases":[{"when":"x","model":{"type":"model","model":"cm:item/breach"}}]}}
                """).model());
        assertEquals("minecraft:item/bow", ItemDefinitions.pick("""
                {"model":{"type":"condition","property":"using_item","on_false":{"type":"model","model":"item/bow"},
                "on_true":{"type":"range_dispatch","entries":[]}}}
                """).model());
        assertTrue(ItemDefinitions.pick("{\"model\":{\"type\":\"special\",\"base\":\"item/shield\"}}").special());
        assertNull(ItemDefinitions.pick("[]").model());
        assertNull(ItemDefinitions.pick("{broken").model());
    }

    @Test
    void conditionPrefersPackBranch() {
        String kb = """
                {"model":{"type":"condition","property":"component","predicate":"enchantments",
                "on_true":{"type":"model","model":"kb1:item/kb1"},
                "on_false":{"type":"model","model":"item/netherite_sword"}}}
                """;
        assertEquals("minecraft:item/netherite_sword", ItemDefinitions.pick(kb).model(), "no preference: idle branch");
        assertEquals("kb1:item/kb1", ItemDefinitions.pick(kb, id -> id.startsWith("kb1:")).model());
        assertEquals("minecraft:item/netherite_sword", ItemDefinitions.pick(kb, id -> false).model(),
                "nothing from the pack: default choice");
        String select = """
                {"model":{"type":"select","property":"component","cases":[
                  {"when":"a","model":{"type":"model","model":"item/vanilla_a"}},
                  {"when":"b","model":{"type":"composite","models":[{"type":"model","model":"p:item/b"}]}}],
                 "fallback":{"type":"model","model":"item/diamond_sword"}}}
                """;
        assertEquals("p:item/b", ItemDefinitions.pick(select, id -> id.startsWith("p:")).model());
        // a display_context select never shows a non-gui branch in the inventory, even if that one is the pack's
        String ctx = """
                {"model":{"type":"select","property":"display_context","cases":[
                  {"when":"gui","model":{"type":"model","model":"item/end_crystal"}}],
                 "fallback":{"type":"model","model":"p:item/in_hand"}}}
                """;
        assertEquals("minecraft:item/end_crystal", ItemDefinitions.pick(ctx, id -> id.startsWith("p:")).model());
    }

    static ResolvedModel hugeCubes(int n) {
        StringBuilder sb = new StringBuilder("{\"textures\":{\"a\":\"t:up\"},\"elements\":[");
        for (int i = 0; i < n; i++) {
            sb.append(i == 0 ? "" : ",").append(
                    "{\"from\":[-16,-16,-16],\"to\":[32,32,32],\"faces\":{\"up\":{\"texture\":\"#a\"},\"down\":{\"texture\":\"#a\"},"
                            + "\"north\":{\"texture\":\"#a\"},\"south\":{\"texture\":\"#a\"},\"east\":{\"texture\":\"#a\"},\"west\":{\"texture\":\"#a\"}}}");
        }
        return ModelResolver.resolve("t:m", Map.of("t:m", sb.append("]}").toString())::get);
    }

    @Test
    void hostileModelsAreCapped() {
        ResolvedModel many = hugeCubes(1000);
        assertEquals(1000, many.elements().size());
        assertThrows(Scene.BudgetExceeded.class, () -> Scene.item(many, colourTextures()), "6000 quads > cap");
        Scene big = Scene.item(hugeCubes(300), colourTextures());
        assertThrows(Scene.BudgetExceeded.class, () -> big.render(64, 0), "1800 huge quads exceed the fill budget");
        // ordinary models stay well inside both caps
        assertDoesNotThrow(() -> Scene.item(colourCube(), colourTextures()).strip(64));
    }

    @Test
    void endCrystalGeometryHasBaseAndCubes() {
        List<EndCrystalGeometry.Cuboid> parts = EndCrystalGeometry.cuboids();
        assertEquals(4, parts.size());
        EndCrystalGeometry.Cuboid base = parts.get(0);
        assertEquals("base", base.name());
        assertArrayEquals(new float[] {-6, 0, -6, 12, 4, 12}, base.box());
        assertEquals(0, base.u());
        assertEquals(16, base.v());
        for (int i = 1; i < 4; i++) {
            assertArrayEquals(new float[] {-4, -4, -4, 8, 8, 8}, parts.get(i).box(), parts.get(i).name());
        }
        assertEquals("outer_glass", parts.get(1).name());
        assertEquals("inner_glass", parts.get(2).name());
        assertEquals("cube", parts.get(3).name());
        assertEquals(32, parts.get(3).u());
        assertEquals(0.875f, parts.get(2).scale());
        assertEquals(0.765625f, parts.get(3).scale());
        // 6 faces per cuboid, base drawn when placed
        assertEquals(24, EndCrystalGeometry.quads(solid(0xFFFFFFFF), 0).size());
        ArgbImage img = Scene.endCrystal(solid(0xFFFFFFFF)).render(64, 0);
        assertTrue(opaqueColumns(img) > 20);
    }

    @Test
    void hoverStripHas24Frames() {
        Scene s = Scene.item(colourCube(), colourTextures());
        ArgbImage strip = s.strip(64);
        assertEquals(64, strip.width());
        assertEquals(64 * Scene.FRAMES, strip.height());
        assertEquals(24, Scene.FRAMES);
        ArgbImage first = s.render(64, 0);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                assertEquals(first.get(x, y), strip.get(x, y), "frame 0 is the static pose");
            }
        }
        boolean differs = false;
        for (int y = 0; y < 64 && !differs; y++) {
            for (int x = 0; x < 64; x++) {
                if (strip.get(x, y) != strip.get(x, 3 * 64 + y)) {
                    differs = true;
                    break;
                }
            }
        }
        assertTrue(differs, "the model turns between frames");
        Scene crystal = Scene.endCrystal(solid(0xFFFFFFFF));
        assertEquals(64 * 24, crystal.strip(64).height());
    }

    @Test
    void flatLayersAreComposited() {
        ArgbImage a = solid(0xFF112233);
        ArgbImage b = new ArgbImage(8, 8);
        b.set(0, 0, 0xFFFFFFFF);
        ArgbImage out = Scene.flat(List.of(a, b));
        assertEquals(8, out.width());
        assertEquals(0xFFFFFFFF, out.get(0, 0));
        assertEquals(0xFF112233, out.get(5, 5));
    }
}
