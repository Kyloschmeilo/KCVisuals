package com.kcvisuals.module.visual;

import com.kcvisuals.gui.Theme;
import com.kcvisuals.module.Module;
import com.kcvisuals.module.setting.BoolSetting;
import com.kcvisuals.module.setting.ColorSetting;
import com.kcvisuals.module.setting.NumberSetting;
import com.kcvisuals.render.Shapes;
import com.kcvisuals.render.TrajectorySim;
import com.kcvisuals.render.WorldRender;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.EggItem;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.ExperienceBottleItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SnowballItem;
import net.minecraft.item.ThrowablePotionItem;
import net.minecraft.item.TridentItem;
import net.minecraft.item.WindChargeItem;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Predicted flight path of the projectile in the player's own hand, computed only from the held item and the
 * player's own look direction and motion (the same inputs vanilla uses when throwing). Never for other entities.
 */
public class Trajectories extends Module implements WorldRender.Renderer {
    private static final int MAX_STEPS = 300;

    public final ColorSetting color = add(new ColorSetting("color", "Farbe", Theme.ACCENT));
    public final NumberSetting width = add(new NumberSetting("width", "Linienbreite", 3.0, 1.0, 6.0, 0.5));
    public final BoolSetting glow = add(new BoolSetting("glow", "Leuchten", true));
    public final BoolSetting endMarker = add(new BoolSetting("end_marker", "Endmarkierung", true));

    private final double[] sim = new double[(MAX_STEPS + 1) * 3];
    private final float[] rel = new float[(MAX_STEPS + 1) * 3];

    public Trajectories() {
        super("trajectories", "Flugbahnen", "Zeigt die Flugbahn des Wurfgegenstands in deiner Hand mit Aufprallpunkt.");
    }

    /** Projectile kind and launch speed for the stack, or null when it is not a (ready) projectile. */
    private static TrajectorySim.Kind kindFor(ClientPlayerEntity p, ItemStack stack, Hand hand) {
        Item item = stack.getItem();
        if (item instanceof EnderPearlItem || item instanceof SnowballItem || item instanceof EggItem) {
            return TrajectorySim.PEARL;
        }
        if (item instanceof ThrowablePotionItem) {
            return TrajectorySim.SPLASH_POTION;
        }
        if (item instanceof ExperienceBottleItem) {
            return TrajectorySim.XP_BOTTLE;
        }
        if (item instanceof WindChargeItem) {
            return TrajectorySim.WIND_CHARGE;
        }
        boolean using = p.isUsingItem() && p.getActiveHand() == hand;
        if (item instanceof BowItem) {
            if (!using) {
                return null;
            }
            float pull = BowItem.getPullProgress(p.getItemUseTime());
            if (pull < 0.1f) {
                return null;
            }
            TrajectorySim.Kind a = TrajectorySim.ARROW;
            return new TrajectorySim.Kind(TrajectorySim.arrowSpeed(pull), a.gravity(), a.drag(), 0f);
        }
        if (item instanceof CrossbowItem) {
            ChargedProjectilesComponent c = stack.get(DataComponentTypes.CHARGED_PROJECTILES);
            if (c == null || c.isEmpty() || c.contains(Items.FIREWORK_ROCKET)) {
                return null;
            }
            return TrajectorySim.CROSSBOW_ARROW;
        }
        if (item instanceof TridentItem) {
            if (!using || EnchantmentHelper.getTridentSpinAttackStrength(stack, p) > 0f) {
                return null;
            }
            return TrajectorySim.TRIDENT;
        }
        return null;
    }

    @Override
    public void renderWorld(WorldRender.Frame f) {
        ClientPlayerEntity p = f.mc.player;
        Hand hand = Hand.MAIN_HAND;
        TrajectorySim.Kind kind = kindFor(p, p.getMainHandStack(), Hand.MAIN_HAND);
        if (kind == null) {
            hand = Hand.OFF_HAND;
            kind = kindFor(p, p.getOffHandStack(), Hand.OFF_HAND);
        }
        if (kind == null) {
            return;
        }
        float td = f.tickDelta;
        float yaw = p.getYaw(td), pitch = p.getPitch(td);
        Vec3d pos = p.getLerpedPos(td);
        double sx = pos.x, sy = pos.y + p.getStandingEyeHeight() - 0.1, sz = pos.z;

        // ProjectileEntity#setVelocity(shooter, pitch, yaw, roll, speed, 0)
        float rad = MathHelper.RADIANS_PER_DEGREE;
        double dx = -MathHelper.sin(yaw * rad) * MathHelper.cos(pitch * rad);
        double dy = -MathHelper.sin((pitch + kind.pitchOffset()) * rad);
        double dz = MathHelper.cos(yaw * rad) * MathHelper.cos(pitch * rad);
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        Vec3d mv = p.getMovement();
        double vx = dx / len * kind.speed() + mv.x;
        double vy = dy / len * kind.speed() + (p.isOnGround() ? 0 : mv.y);
        double vz = dz / len * kind.speed() + mv.z;

        int n = TrajectorySim.simulateInto(sim, sx, sy, sz, vx, vy, vz, kind.gravity(), kind.drag(), MAX_STEPS);

        // Block collision: raycast each segment, truncate at the first hit.
        BlockHitResult hit = null;
        int bottom = f.mc.world.getBottomY() - 8;
        for (int i = 0; i + 1 < n; i++) {
            Vec3d a = new Vec3d(sim[i * 3], sim[i * 3 + 1], sim[i * 3 + 2]);
            Vec3d b = new Vec3d(sim[i * 3 + 3], sim[i * 3 + 4], sim[i * 3 + 5]);
            BlockHitResult r = f.mc.world.raycast(new RaycastContext(a, b, RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, p));
            if (r.getType() == HitResult.Type.BLOCK) {
                Vec3d h = r.getPos();
                sim[i * 3 + 3] = h.x;
                sim[i * 3 + 4] = h.y;
                sim[i * 3 + 5] = h.z;
                n = i + 2;
                hit = r;
                break;
            }
            if (b.y < bottom) {
                n = i + 2;
                break;
            }
        }
        if (n < 2) {
            return;
        }

        // Visual start at the hand in first person, blending into the true path over the first points.
        float ox = 0, oy = 0, oz = 0;
        if (f.mc.options.getPerspective().isFirstPerson()) {
            boolean rightArm = (p.getMainArm() == Arm.RIGHT) == (hand == Hand.MAIN_HAND);
            float side = rightArm ? 1f : -1f;
            ox = -MathHelper.cos(yaw * rad) * 0.22f * side;
            oz = -MathHelper.sin(yaw * rad) * 0.22f * side;
            oy = -0.12f;
        }
        int blend = Math.min(12, n - 1);
        for (int i = 0; i < n; i++) {
            float k = i < blend ? 1f - (float) i / blend : 0f;
            rel[i * 3] = f.rx(sim[i * 3]) + ox * k;
            rel[i * 3 + 1] = f.ry(sim[i * 3 + 1]) + oy * k;
            rel[i * 3 + 2] = f.rz(sim[i * 3 + 2]) + oz * k;
        }

        int c = color.get();
        float w = width.getFloat();
        VertexConsumer lines = f.consumers.getBuffer(Shapes.lines());
        if (glow.get()) {
            Shapes.lineStrip(lines, rel, n, Shapes.alpha(c, 0.10f), Shapes.alpha(c, 0.28f), w * 4f);
            Shapes.lineStrip(lines, rel, n, Shapes.alpha(c, 0.2f), Shapes.alpha(c, 0.5f), w * 2.2f);
        }
        Shapes.lineStrip(lines, rel, n, Shapes.alpha(c, 0.55f), c, w);

        if (endMarker.get() && hit != null) {
            Direction side = hit.getSide();
            float nx = side.getOffsetX(), ny = side.getOffsetY(), nz = side.getOffsetZ();
            float ux, uy, uz, wx, wy, wz;
            if (side.getAxis() == Direction.Axis.Y) {
                ux = 1; uy = 0; uz = 0; wx = 0; wy = 0; wz = 1;
            } else if (side.getAxis() == Direction.Axis.X) {
                ux = 0; uy = 1; uz = 0; wx = 0; wy = 0; wz = 1;
            } else {
                ux = 1; uy = 0; uz = 0; wx = 0; wy = 1; wz = 0;
            }
            int last = (n - 1) * 3;
            float cx = f.rx(sim[last]) + nx * 0.015f, cy = f.ry(sim[last + 1]) + ny * 0.015f, cz = f.rz(sim[last + 2]) + nz * 0.015f;
            VertexConsumer q = f.consumers.getBuffer(Shapes.quads());
            Shapes.planeRing(q, cx, cy, cz, ux, uy, uz, wx, wy, wz, 0f, 0.28f, Shapes.alpha(c, 0.35f), Shapes.alpha(c, 0.12f));
            Shapes.planeRing(q, cx, cy, cz, ux, uy, uz, wx, wy, wz, 0.28f, 0.36f, c, c);
            Shapes.planeRing(q, cx, cy, cz, ux, uy, uz, wx, wy, wz, 0.36f, 0.5f, Shapes.alpha(c, 0.5f), 0);
        }
    }
}
