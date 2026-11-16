package com.kcvisuals.hud;

import com.kcvisuals.gui.Theme;
import com.kcvisuals.gui.widget.Draw;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Totems of undying (inventory + off hand) and the worn armour with remaining durability. */
public final class ArmorTotemCounter extends HudElement {
    private static final int ROW_H = 18;
    private static final int W = 56;
    private static final EquipmentSlot[] ARMOR = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private record Row(ItemStack stack, String text, int color) {}

    public ArmorTotemCounter() {
        super("armor", "Rüstung & Totems", "Totem-Anzahl und Haltbarkeit der Rüstung");
    }

    private final List<Row> rows = new ArrayList<>();

    @Override
    public void prepare(boolean preview) {
        rows.clear();
        PlayerEntity p = mc().player;
        if (preview || p == null) {
            rows.add(new Row(new ItemStack(Items.TOTEM_OF_UNDYING), "x3", Theme.TEXT));
            ItemStack[] sample = {
                new ItemStack(Items.NETHERITE_HELMET), new ItemStack(Items.NETHERITE_CHESTPLATE),
                new ItemStack(Items.NETHERITE_LEGGINGS), new ItemStack(Items.NETHERITE_BOOTS)
            };
            int[] pct = {100, 87, 54, 12};
            for (int i = 0; i < 4; i++) {
                rows.add(new Row(sample[i], pct[i] + "%", durabilityColor(pct[i])));
            }
            return;
        }
        int totems = 0;
        for (ItemStack s : p.getInventory().getMainStacks()) {
            if (s.isOf(Items.TOTEM_OF_UNDYING)) {
                totems += s.getCount();
            }
        }
        ItemStack off = p.getEquippedStack(EquipmentSlot.OFFHAND);
        if (off.isOf(Items.TOTEM_OF_UNDYING)) {
            totems += off.getCount();
        }
        rows.add(new Row(new ItemStack(Items.TOTEM_OF_UNDYING), "x" + totems, totems == 0 ? Theme.TEXT_FAINT : Theme.TEXT));
        for (EquipmentSlot slot : ARMOR) {
            ItemStack s = p.getEquippedStack(slot);
            if (s.isEmpty()) {
                continue;
            }
            int pct = s.isDamageable() ? Math.round(100f * (s.getMaxDamage() - s.getDamage()) / s.getMaxDamage()) : 100;
            rows.add(new Row(s, pct + "%", durabilityColor(pct)));
        }
    }

    @Override
    public void reset() {
        rows.clear();
    }

    @Override
    public boolean visible(boolean preview) {
        return !rows.isEmpty();
    }

    private static int durabilityColor(int pct) {
        return pct > 50 ? Theme.SUCCESS : pct > 20 ? Theme.WARNING : Theme.DANGER;
    }

    @Override
    public int width(boolean preview) {
        return W;
    }

    @Override
    public int height(boolean preview) {
        return rows.size() * ROW_H + 4;
    }

    @Override
    public void render(DrawContext ctx, int x, int y, boolean preview) {
        HudStyle.panel(ctx, x, y, W, rows.size() * ROW_H + 4);
        int ry = y + 2;
        for (Row r : rows) {
            ctx.drawItem(r.stack(), x + 4, ry + 1);
            Draw.text(ctx, r.text(), x + 24, ry + (ROW_H - 8) / 2 + 1, r.color());
            ry += ROW_H;
        }
    }
}
