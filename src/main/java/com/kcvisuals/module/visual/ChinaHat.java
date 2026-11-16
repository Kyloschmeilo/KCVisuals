package com.kcvisuals.module.visual;

import com.kcvisuals.gui.Theme;
import com.kcvisuals.module.Module;
import com.kcvisuals.module.setting.ColorSetting;
import com.kcvisuals.module.setting.NumberSetting;
import com.kcvisuals.render.Shapes;
import com.kcvisuals.render.WorldRender;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.Vec3d;

/** Translucent gradient cone above the player's own head; only visible in third person. */
public class ChinaHat extends Module implements WorldRender.Renderer {
    public final ColorSetting color1 = add(new ColorSetting("color1", "Farbe 1", (Theme.ACCENT & 0xFFFFFF) | 0x8C000000));
    public final ColorSetting color2 = add(new ColorSetting("color2", "Farbe 2", (JumpCircles.PINK & 0xFFFFFF) | 0x8C000000));
    public final NumberSetting radius = add(new NumberSetting("radius", "Radius", 0.65, 0.3, 1.2, 0.05));
    public final NumberSetting height = add(new NumberSetting("height", "Höhe", 0.3, 0.1, 0.8, 0.05));

    public ChinaHat() {
        super("china_hat", "China-Hut", "Durchscheinender Kegel über deinem Kopf (nur in der dritten Person).");
    }

    @Override
    public void renderWorld(WorldRender.Frame f) {
        ClientPlayerEntity p = f.mc.player;
        if (f.mc.options.getPerspective().isFirstPerson() || p.isInvisible()) {
            return;
        }
        Vec3d pos = p.getLerpedPos(f.tickDelta);
        float x = f.rx(pos.x), y = f.ry(pos.y + p.getHeight() + 0.06), z = f.rz(pos.z);
        float r = radius.getFloat(), h = height.getFloat();
        int a = color1.get(), b = color2.get();
        VertexConsumer q = f.consumers.getBuffer(Shapes.quads());
        Shapes.cone(q, x, y, z, r, h, a, b, 0.9f);
        // soft rim
        Shapes.ring(q, x, y, z, r, r + 0.05f, a, b, 1f, 0f);
        VertexConsumer l = f.consumers.getBuffer(Shapes.lines());
        Shapes.circleLine(l, x, y, z, r, a | 0xFF000000, b | 0xFF000000, 2f);
    }
}
