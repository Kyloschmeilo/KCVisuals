package com.kcvisuals.module.visual;

import com.kcvisuals.module.Module;
import com.kcvisuals.module.setting.NumberSetting;
import com.kcvisuals.render.ScreenMath;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.RotationAxis;

/**
 * First-person hand/item placement (see HeldItemRendererMixin): per-hand offset in view space, item scale and
 * rotation around the item itself, and a swing speed multiplier for the own player's swing animation. X offsets and
 * Y/Z rotations are mirrored for the left arm, so positive X always moves a hand outwards.
 */
public class CustomHand extends Module {
    private static CustomHand instance;

    public final NumberSetting mainX = add(new NumberSetting("main_x", "Haupthand X", 0.0, -1.0, 1.0, 0.02));
    public final NumberSetting mainY = add(new NumberSetting("main_y", "Haupthand Y", -0.1, -1.0, 1.0, 0.02));
    public final NumberSetting mainZ = add(new NumberSetting("main_z", "Haupthand Z", 0.0, -1.0, 1.0, 0.02));
    public final NumberSetting offX = add(new NumberSetting("off_x", "Nebenhand X", 0.0, -1.0, 1.0, 0.02));
    public final NumberSetting offY = add(new NumberSetting("off_y", "Nebenhand Y", -0.1, -1.0, 1.0, 0.02));
    public final NumberSetting offZ = add(new NumberSetting("off_z", "Nebenhand Z", 0.0, -1.0, 1.0, 0.02));
    public final NumberSetting scale = add(new NumberSetting("scale", "Größe", 0.75, 0.3, 1.5, 0.05));
    public final NumberSetting rotX = add(new NumberSetting("rot_x", "Drehung X", 0.0, -180.0, 180.0, 5.0));
    public final NumberSetting rotY = add(new NumberSetting("rot_y", "Drehung Y", 0.0, -180.0, 180.0, 5.0));
    public final NumberSetting rotZ = add(new NumberSetting("rot_z", "Drehung Z", 0.0, -180.0, 180.0, 5.0));
    public final NumberSetting swingSpeed = add(new NumberSetting("swing_speed", "Schlaggeschwindigkeit", 1.0, 0.5, 2.0, 0.05));

    public CustomHand() {
        super("custom_hand", "Eigene Hand", "Position, Größe und Drehung der Hand und gehaltener Items, Schlagtempo.");
        instance = this;
    }

    public static CustomHand instance() {
        return instance;
    }

    public static boolean active() {
        return instance != null && instance.enabled();
    }

    /** View-space offset, applied before vanilla's hand transforms. */
    public void applyOffset(MatrixStack matrices, boolean mainHand, Arm arm) {
        float side = arm == Arm.RIGHT ? 1f : -1f;
        if (mainHand) {
            matrices.translate(side * mainX.getFloat(), mainY.getFloat(), mainZ.getFloat());
        } else {
            matrices.translate(side * offX.getFloat(), offY.getFloat(), offZ.getFloat());
        }
    }

    /** Rotation and scale around the item, applied right before the item model is drawn. */
    public void applyItemTransform(MatrixStack matrices, Arm arm) {
        float side = arm == Arm.RIGHT ? 1f : -1f;
        if (rotX.get() != 0) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotX.getFloat()));
        }
        if (rotY.get() != 0) {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * rotY.getFloat()));
        }
        if (rotZ.get() != 0) {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * rotZ.getFloat()));
        }
        float s = scale.getFloat();
        matrices.scale(s, s, s);
    }

    /** Swing duration for the own player. */
    public int swingDuration(int vanillaTicks) {
        return ScreenMath.swingDuration(vanillaTicks, swingSpeed.get());
    }
}
