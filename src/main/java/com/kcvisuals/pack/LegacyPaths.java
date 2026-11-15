package com.kcvisuals.pack;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps pre-1.13 / pre-1.21.4 resource pack paths onto their modern equivalents. Paths are relative to
 * {@code assets/minecraft/}. Used both when matching categories and when the generated pack is written, so a
 * legacy pack contributes its files under the modern path.
 */
public final class LegacyPaths {
    private LegacyPaths() {}

    private static final Pattern LEGACY_ARMOR =
            Pattern.compile("^textures/models/armor/([a-z]+)_layer_([12])(_overlay)?(\\.png.*)$");
    private static final Pattern GOLD_OR_WOOD_TOOL =
            Pattern.compile("^(gold|wood)_(sword|axe|pickaxe|shovel|hoe|helmet|chestplate|leggings|boots)$");

    private static final Map<String, String> ARMOR_MATERIALS = Map.of(
            "leather", "leather", "chainmail", "chainmail", "iron", "iron", "gold", "gold",
            "diamond", "diamond", "netherite", "netherite", "turtle", "turtle_scute", "copper", "copper");

    /** Whole-name renames for item textures and item models (name without extension). */
    private static final Map<String, String> ITEM_RENAMES = Map.of(
            "totem", "totem_of_undying",
            "apple_golden", "golden_apple",
            "bow_standby", "bow",
            "fireball", "fire_charge",
            "bottle_drinkable", "potion",
            "bottle_splash", "splash_potion",
            "bottle_lingering", "lingering_potion",
            "bottle_empty", "glass_bottle");

    /** Prefix renames for single files whose folder or name changed (the trailing dot keeps the extension). */
    private static final Map<String, String> PREFIX_RENAMES = Map.of(
            "textures/block/web.", "textures/block/cobweb.",
            "textures/entity/elytra.", "textures/entity/equipment/wings/elytra.",
            "textures/entity/arrow.", "textures/entity/projectiles/arrow.",
            "textures/entity/spectral_arrow.", "textures/entity/projectiles/spectral_arrow.",
            "textures/entity/endercrystal/endercrystal.", "textures/entity/end_crystal/end_crystal.",
            "textures/entity/endercrystal/endercrystal_beam.", "textures/entity/end_crystal/end_crystal_beam.");

    public static String normalise(String path) {
        String p = path;
        if (p.startsWith("textures/items/")) {
            p = "textures/item/" + p.substring("textures/items/".length());
        } else if (p.startsWith("textures/blocks/")) {
            p = "textures/block/" + p.substring("textures/blocks/".length());
        }

        for (Map.Entry<String, String> e : PREFIX_RENAMES.entrySet()) {
            if (p.startsWith(e.getKey())) {
                return e.getValue() + p.substring(e.getKey().length());
            }
        }

        Matcher armor = LEGACY_ARMOR.matcher(p);
        if (armor.matches()) {
            String material = ARMOR_MATERIALS.get(armor.group(1));
            if (material != null) {
                String folder = armor.group(2).equals("1") ? "humanoid" : "humanoid_leggings";
                String overlay = armor.group(3) == null ? "" : "_overlay";
                return "textures/entity/equipment/" + folder + "/" + material + overlay + armor.group(4);
            }
        }

        for (String dir : new String[] {"textures/item/", "models/item/"}) {
            if (p.startsWith(dir)) {
                return dir + renameItem(p.substring(dir.length()));
            }
        }
        return p;
    }

    private static String renameItem(String rest) {
        int end = rest.length();
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '.' || c == '/') {
                end = i;
                break;
            }
        }
        String stem = rest.substring(0, end);
        String tail = rest.substring(end);
        Matcher m = GOLD_OR_WOOD_TOOL.matcher(stem);
        if (m.matches()) {
            return (m.group(1).equals("gold") ? "golden_" : "wooden_") + m.group(2) + tail;
        }
        return ITEM_RENAMES.getOrDefault(stem, stem) + tail;
    }
}
