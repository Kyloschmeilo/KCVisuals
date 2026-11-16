package com.kcvisuals.gui.packs;

import com.kcvisuals.KCVisuals;
import com.kcvisuals.pack.ModelClosure;
import com.kcvisuals.pack.PackSource;
import com.kcvisuals.preview.ArgbImage;
import com.kcvisuals.preview.ItemDefinitions;
import com.kcvisuals.preview.ModelResolver;
import com.kcvisuals.preview.ResolvedModel;
import com.kcvisuals.preview.Scene;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

/**
 * Model previews for one pack (or Vanilla), read on the preview worker: every model, item definition and texture is
 * looked up in the pack first and then in the game's default (vanilla) resource pack, never in the user's enabled
 * packs, so a tile shows the pack itself on top of vanilla. Reads are capped like the 2D previews.
 */
final class ModelPreviews {
    /** Rendered preview side in pixels (a 32 px tile preview at gui scale 2). */
    static final int SIZE = 64;
    /** Textures are shrunk to this before rendering; a 64 px render never needs more. */
    private static final int MAX_TEXTURE = 256;
    private static final String CRYSTAL_TEXTURE = "assets/minecraft/textures/entity/end_crystal/end_crystal.png";

    /** A rendered preview; {@code scene} is set for 3D renders (used again for the hover strip). */
    record Result(ArgbImage image, Scene scene) {}

    private final PackSource src;
    private final Map<String, String> index;
    private final Map<String, ArgbImage> textures = new HashMap<>();
    /** True once anything was read from the pack rather than from vanilla. */
    private boolean fromPack;

    /** @param src the pack, or null for Vanilla; @param index its output-path index ({@link ModelClosure#outputIndex}) */
    ModelPreviews(PackSource src, Map<String, String> index) {
        this.src = src;
        this.index = index;
    }

    /**
     * The inventory model of {@code item}: a 3D render, a flat icon from its layers, or null when the 2D texture
     * preview should be used instead (no model, a special/builtin model, or nothing in it comes from the pack).
     * With {@code vanillaFallback} a pack that does not change the item still gets the vanilla item (used where the
     * 2D fallback would show an unrelated texture of the category, e.g. the crystal entity atlas).
     */
    Result item(String item, boolean vanillaFallback) {
        String defPath = ModelClosure.resolve("minecraft:" + item, "items/", ".json");
        if (defPath == null) {
            return null;
        }
        String def = json(defPath);
        String modelId;
        if (def != null) {
            ItemDefinitions.Pick pick = ItemDefinitions.pick(def);
            if (pick.special() || pick.model() == null) {
                return null;
            }
            modelId = pick.model();
        } else {
            modelId = "minecraft:item/" + item;
        }
        ResolvedModel model = ModelResolver.resolve(modelId, id -> {
            String path = ModelClosure.resolve(id, "models/", ".json");
            return path == null ? null : json(path);
        });
        switch (model.kind()) {
            case MODEL -> {
                Scene scene = Scene.item(model, this::texture);
                if (src != null && !fromPack && !vanillaFallback) {
                    return null;
                }
                return new Result(scene.render(SIZE, 0), scene);
            }
            case FLAT -> {
                if (src == null && !vanillaFallback) {
                    return null; // the 2D preview already shows the vanilla icon
                }
                List<ArgbImage> layers = new ArrayList<>();
                for (String layer : model.layers()) {
                    layers.add(texture(layer));
                }
                ArgbImage icon = fromPack || vanillaFallback ? Scene.flat(layers) : null;
                return icon == null ? null : new Result(icon.region(0, 0, icon.width(), icon.height(), PreviewCache.MAX_SIZE), null);
            }
            default -> {
                return null;
            }
        }
    }

    /** The placed end crystal with the pack's entity texture (vanilla's if the pack has none). */
    Result placedCrystal() {
        Scene scene = Scene.endCrystal(image(CRYSTAL_TEXTURE));
        return new Result(scene.render(SIZE, 0), scene);
    }

    private ArgbImage texture(String id) {
        if (textures.containsKey(id)) {
            return textures.get(id);
        }
        String path = ModelClosure.resolve(id, "textures/", ".png");
        ArgbImage img = path == null ? null : image(path);
        textures.put(id, img);
        return img;
    }

    /** Text of the output path from the pack, else vanilla; null if missing, oversized or unreadable. */
    private String json(String outPath) {
        byte[] b = bytes(outPath, PreviewPick.MAX_JSON_BYTES);
        return b == null ? null : new String(b, StandardCharsets.UTF_8);
    }

    /** First animation frame of the PNG at the output path (pack, else vanilla), shrunk to {@link #MAX_TEXTURE}. */
    private ArgbImage image(String outPath) {
        byte[] b = bytes(outPath, PreviewPick.MAX_IMAGE_BYTES);
        long pixels = PreviewPick.pngPixels(b);
        if (b == null || pixels <= 0 || pixels > PreviewPick.MAX_PIXELS) {
            return null;
        }
        try (NativeImage img = NativeImage.read(b)) {
            int[] f = PreviewPick.firstFrame(img.getWidth(), img.getHeight());
            int[] size = PreviewPick.fitWithin(f[2], f[3], MAX_TEXTURE);
            ArgbImage out = new ArgbImage(size[0], size[1]);
            for (int y = 0; y < size[1]; y++) {
                int sy = f[1] + (int) ((long) y * f[3] / size[1]);
                for (int x = 0; x < size[0]; x++) {
                    out.set(x, y, img.getColorArgb(f[0] + (int) ((long) x * f[2] / size[0]), sy));
                }
            }
            return out;
        } catch (IOException | RuntimeException e) {
            KCVisuals.LOGGER.debug("Unreadable texture {} in {}: {}", outPath, src == null ? "vanilla" : src.id(), e.toString());
            return null;
        }
    }

    private byte[] bytes(String outPath, int max) {
        if (src != null && index != null) {
            String raw = index.get(outPath);
            if (raw != null) {
                try (InputStream in = src.open(raw)) {
                    byte[] b = PreviewPick.readCapped(in, max);
                    if (b != null) {
                        fromPack = true;
                        return b;
                    }
                    KCVisuals.LOGGER.warn("{} in {} is larger than {} bytes, ignored for the preview", raw, src.id(), max);
                } catch (IOException | RuntimeException e) {
                    KCVisuals.LOGGER.debug("Unreadable {} in {}: {}", raw, src.id(), e.toString());
                }
            }
        }
        return vanilla(outPath, max);
    }

    /** {@code assets/<ns>/<path>} from the game's built-in resources (not the user's resource packs). */
    private static byte[] vanilla(String outPath, int max) {
        int slash = outPath.indexOf('/', "assets/".length());
        if (!outPath.startsWith("assets/") || slash < 0) {
            return null;
        }
        try {
            Identifier id = Identifier.of(outPath.substring("assets/".length(), slash), outPath.substring(slash + 1));
            InputSupplier<InputStream> in = MinecraftClient.getInstance().getDefaultResourcePack()
                    .open(ResourceType.CLIENT_RESOURCES, id);
            if (in == null) {
                return null;
            }
            try (InputStream s = in.get()) {
                return PreviewPick.readCapped(s, max);
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
