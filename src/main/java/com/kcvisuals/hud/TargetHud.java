package com.kcvisuals.hud;

import com.kcvisuals.gui.Theme;
import com.kcvisuals.gui.widget.Draw;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.Util;

/**
 * The entity the player attacked last (within {@link #SHOW_MS}): head, name, health bar, armour and (for players)
 * ping. Only uses information the client already has.
 */
public final class TargetHud extends HudElement {
    public static final long SHOW_MS = 8000;
    private static final int W = 172;
    private static final int H = 40;
    private static final int HEAD = 28;

    private static final TargetTracker<LivingEntity> TRACKER = new TargetTracker<>(SHOW_MS);
    private LivingEntity shown;
    private boolean preview;
    private float shownHealth = -1;
    private Entity shownFor;
    private Float lastSeenHp;
    private Entity lastSeenFor;

    public TargetHud() {
        super("target", "Ziel-HUD", "Zuletzt angegriffenes Ziel (8 s)");
    }

    private static boolean tracking;

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        tracking = enabled;
        if (!enabled) {
            TRACKER.clear();
            reset();
        }
    }

    /** Remembers an attacked entity; ignored while the element is disabled so no entity reference is kept. */
    public static void setTarget(Entity e) {
        if (tracking && e instanceof LivingEntity le) {
            TRACKER.set(le, Util.getMeasuringTimeMs());
        }
    }

    public static void clearTarget() {
        TRACKER.clear();
    }

    @Override
    public void prepare(boolean preview) {
        this.preview = preview;
        shown = preview ? null : current();
        if (shown == null) {
            shownFor = null;
        }
    }

    @Override
    public void reset() {
        shown = null;
        shownFor = null;
        shownHealth = -1;
        lastSeenHp = null;
        lastSeenFor = null;
    }

    private static LivingEntity current() {
        return TRACKER.current(Util.getMeasuringTimeMs(), t -> !t.isRemoved() && t.isAlive());
    }

    @Override
    public boolean visible(boolean preview) {
        return this.preview || shown != null;
    }

    @Override
    public int width(boolean preview) {
        return W;
    }

    @Override
    public int height(boolean preview) {
        return H;
    }

    @Override
    public void render(DrawContext ctx, int x, int y, boolean preview) {
        MinecraftClient mc = mc();
        LivingEntity t = this.preview ? null : shown;
        String name;
        float hp, max;
        int armor;
        Integer ping = null;
        SkinTextures skin = null;
        if (t != null) {
            name = t.getName().getString();
            hp = t.getHealth();
            max = Math.max(1f, t.getMaxHealth());
            armor = t.getArmor();
            if (t instanceof PlayerEntity p && mc.getNetworkHandler() != null) {
                PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(p.getUuid());
                if (e != null) {
                    ping = e.getLatency();
                    skin = e.getSkinTextures();
                }
            }
        } else {
            name = mc.player != null ? mc.player.getName().getString() : "Spieler";
            hp = 15f;
            max = 20f;
            armor = 12;
            ping = 23;
            skin = mc.player != null ? mc.player.getSkin() : null;
        }
        if (t != null && !this.preview) {
            // Same rule as DamageNumbers: no new health information while the target is out of sight.
            if (mc.player != null && !mc.player.canSee(t)) {
                hp = lastSeenHp != null && lastSeenFor == t ? lastSeenHp : hp;
            } else {
                lastSeenHp = hp;
                lastSeenFor = t;
            }
        }
        if (t != shownFor || shownHealth < 0) {
            shownFor = t;
            shownHealth = hp;
        }
        shownHealth = Draw.approach(shownHealth, hp);

        HudStyle.panel(ctx, x, y, W, H);
        int hx = x + 6, hy = y + (H - HEAD) / 2;
        if (skin != null) {
            PlayerSkinDrawer.draw(ctx, skin, hx, hy, HEAD);
        } else {
            Draw.roundRect(ctx, hx, hy, HEAD, HEAD, 4, Theme.SURFACE_HOVER);
            String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
            Draw.textCentered(ctx, initial, hx + HEAD / 2, hy + (HEAD - 8) / 2 + 1, Theme.TEXT);
        }
        int tx = hx + HEAD + 6;
        int tw = x + W - 6 - tx;
        String pingText = ping != null ? ping + " ms" : "";
        int pw = pingText.isEmpty() ? 0 : Draw.font().getWidth(pingText) + 4;
        Draw.text(ctx, Draw.ellipsize(name, tw - pw), tx, y + 6, Theme.TEXT);
        if (!pingText.isEmpty()) {
            Draw.text(ctx, pingText, tx + tw - pw + 4, y + 6, Theme.TEXT_DIM);
        }
        // health bar
        float frac = Math.max(0f, Math.min(1f, shownHealth / max));
        int by = y + 18;
        Draw.roundRect(ctx, tx, by, tw, 5, 2, Theme.BORDER);
        if (frac > 0) {
            Draw.roundRect(ctx, tx, by, Math.max(3, Math.round(tw * frac)), 5, 2,
                    Theme.mix(Theme.DANGER, Theme.SUCCESS, Math.min(1f, frac * 1.3f)));
        }
        String hpText = String.format(Locale.GERMAN, "%.1f / %.1f", hp, max);
        Draw.text(ctx, hpText, tx, y + 27, Theme.TEXT_DIM);
        String armorText = "Rüstung " + armor;
        Draw.text(ctx, armorText, tx + tw - Draw.font().getWidth(armorText), y + 27, Theme.TEXT_DIM);
    }
}
