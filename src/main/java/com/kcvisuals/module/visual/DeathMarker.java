package com.kcvisuals.module.visual;

import com.kcvisuals.gui.Theme;
import com.kcvisuals.module.Module;
import com.kcvisuals.module.setting.BoolSetting;
import com.kcvisuals.module.setting.ColorSetting;
import com.kcvisuals.render.Shapes;
import com.kcvisuals.render.WorldRender;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;

/**
 * Beam and distance label at the player's own last death point, only in the dimension where it happened. The
 * position is information the player already had (vanilla keeps it as lastDeathLocation for the recovery compass),
 * so it is shown regardless of reduced debug info.
 */
public class DeathMarker extends Module implements WorldRender.Renderer, WorldRender.Ticker, WorldRender.AlwaysTicks {
    public final ColorSetting color = add(new ColorSetting("color", "Farbe", Theme.ACCENT));
    public final BoolSetting beam = add(new BoolSetting("beam", "Lichtstrahl", true));
    public final BoolSetting label = add(new BoolSetting("label", "Entfernung anzeigen", true));

    private RegistryKey<World> dim;
    private double mx, my, mz;
    private boolean recordedThisDeath;

    public DeathMarker() {
        super("death_marker", "Todesmarker", "Markiert deinen letzten Todespunkt in der aktuellen Dimension.");
    }

    /** Sets the marker explicitly (death observed or restored). */
    public void setMarker(RegistryKey<World> dimension, double x, double y, double z) {
        dim = dimension;
        mx = x;
        my = y;
        mz = z;
    }

    public void clear() {
        dim = null;
    }

    @Override
    public void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) {
            // Disconnected: forget the marker (it is restored from the server's lastDeathLocation on join).
            dim = null;
            recordedThisDeath = false;
            return;
        }
        if (p.isDead()) {
            if (!recordedThisDeath) {
                setMarker(mc.world.getRegistryKey(), p.getX(), p.getY(), p.getZ());
                recordedThisDeath = true;
            }
            return;
        }
        recordedThisDeath = false;
        // Adopt vanilla's lastDeathLocation (sent on respawn/join) when we have no matching exact position.
        Optional<GlobalPos> last = p.getLastDeathPos();
        if (last.isPresent()) {
            GlobalPos g = last.get();
            BlockPos b = g.pos();
            boolean same = dim != null && dim.equals(g.dimension())
                    && Math.abs(Math.floor(mx) - b.getX()) <= 1 && Math.abs(Math.floor(my) - b.getY()) <= 1
                    && Math.abs(Math.floor(mz) - b.getZ()) <= 1;
            if (!same) {
                setMarker(g.dimension(), b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
            }
        }
    }

    @Override
    public void renderWorld(WorldRender.Frame f) {
        if (dim == null || !dim.equals(f.mc.world.getRegistryKey())) {
            return;
        }
        int c = color.get();
        float x = f.rx(mx), y = f.ry(my), z = f.rz(mz);
        if (beam.get()) {
            VertexConsumer q = f.consumers.getBuffer(Shapes.quads());
            Shapes.beam(q, x, y, y + 60f, z, 0.12f, Shapes.alpha(c, 0.75f), Shapes.alpha(c, 0f));
            Shapes.beam(q, x, y, y + 30f, z, 0.3f, Shapes.alpha(c, 0.25f), Shapes.alpha(c, 0f));
            Shapes.ring(q, x, y + 0.02f, z, 0.35f, 0.7f, c, JumpCircles.PINK, 0.6f, 0f);
        }
        if (label.get()) {
            double dist = Math.sqrt(f.mc.player.squaredDistanceTo(mx, my, mz));
            String text = "Tod · " + Math.round(dist) + " m";
            double cd = Math.sqrt(x * x + (y + 2.2) * (y + 2.2) + z * z);
            float scale = (float) (0.03 * Math.max(1.0, cd / 8.0));
            MatrixStack m = f.matrices;
            m.push();
            m.translate(x, y + 2.2f, z);
            m.multiply(f.mc.gameRenderer.getCamera().getRotation());
            m.scale(scale, -scale, scale);
            TextRenderer tr = f.mc.textRenderer;
            float w = tr.getWidth(text);
            tr.draw(text, -w / 2f, 0f, 0xFFFFFFFF, false, m.peek().getPositionMatrix(), f.consumers,
                    TextRenderer.TextLayerType.NORMAL, (c & 0xFFFFFF) | 0x99000000, LightmapTextureManager.MAX_LIGHT_COORDINATE);
            m.pop();
        }
    }
}
