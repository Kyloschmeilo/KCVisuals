package com.kcvisuals.hud;

import com.kcvisuals.gui.Theme;
import com.kcvisuals.gui.widget.Draw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;

/** "KC Visuals | name | fps | ping | server" strip with an accent bar. */
public final class Watermark extends HudElement {
    private static final int H = 16;

    public Watermark() {
        super("watermark", "Wasserzeichen", "Name, FPS, Ping und Server");
    }

    private String rest() {
        MinecraftClient mc = mc();
        String name = mc.getSession() != null ? mc.getSession().getUsername() : "Spieler";
        return " | " + name + " | " + mc.getCurrentFps() + " FPS | " + ping() + " ms | " + server();
    }

    static int ping() {
        MinecraftClient mc = mc();
        if (mc.player != null && mc.getNetworkHandler() != null) {
            PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (e != null) {
                return e.getLatency();
            }
        }
        return 0;
    }

    static String server() {
        MinecraftClient mc = mc();
        ServerInfo info = mc.getCurrentServerEntry();
        if (info != null) {
            String n = info.name != null && !info.name.isBlank() ? info.name : info.address;
            return n == null ? "Server" : n;
        }
        return mc.isInSingleplayer() || mc.world == null ? "Einzelspieler" : "Server";
    }


    @Override
    public int width(boolean preview) {
        return 10 + Draw.font().getWidth("KC Visuals" + rest()) + 6;
    }

    @Override
    public int height(boolean preview) {
        return H;
    }

    @Override
    public void render(DrawContext ctx, int x, int y, boolean preview) {
        int w = width(preview);
        HudStyle.panel(ctx, x, y, w, H);
        HudStyle.accentBar(ctx, x, y, H);
        int ty = y + (H - 8) / 2 + 1;
        int tx = x + 10;
        Draw.text(ctx, "KC Visuals", tx, ty, Theme.ACCENT);
        Draw.text(ctx, rest(), tx + Draw.font().getWidth("KC Visuals"), ty, Theme.TEXT);
    }
}
