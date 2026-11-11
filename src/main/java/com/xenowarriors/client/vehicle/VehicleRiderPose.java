package com.xenowarriors.client.vehicle;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.vehicle.VehicleEntity;
import com.xenowarriors.vehicle.VehicleKind;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Riders who didn't get a seat: standing on the roof (arms out a little for balance) or hanging from the helicopter's
 * skids (arms straight up, legs dangling - or, landed, standing beside it with a hand on the rail). The vehicle tells
 * the renderer not to sit them down while they're being drawn (VehicleEntity.renderingRider).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class VehicleRiderPose {
    private VehicleRiderPose() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void pre(RenderLivingEvent.Pre<?, ?> e) {
        VehicleEntity.renderingRider = e.getEntity().getVehicle() instanceof VehicleEntity ? e.getEntity() : null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void post(RenderLivingEvent.Post<?, ?> e) { VehicleEntity.renderingRider = null; }

    // ------------------------------------------------------------------ seated riders tilt with the vehicle

    private static final java.util.Set<LivingEntity> TILTED = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    /**
     * Last (after anything that might cancel the draw): a seated rider is turned with the vehicle's tilt - pitching
     * and banking with the helicopter, leaning into the turns with the motorbike - and bounces a little on the bike.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tilt(RenderLivingEvent.Pre<?, ?> e) {
        LivingEntity r = e.getEntity();
        if (!(r.getVehicle() instanceof VehicleEntity v) || v.seatOf(r).type() != VehicleKind.SeatType.SIT) return;
        float pt = e.getPartialTick();
        float pitch = Mth.lerp(pt, v.pitchTiltO, v.pitchTilt), roll = Mth.lerp(pt, v.rollTiltO, v.rollTilt);
        boolean bike = v.kind.base() == VehicleKind.MOTORBIKE;
        float bounce = bike ? Mth.sin((v.tickCount + pt) * 1.7F) * 0.012F * (float) Math.min(1.0, Math.abs(v.speed()) / 0.4) : 0F;
        if (Math.abs(pitch) < 0.05F && Math.abs(roll) < 0.05F && bounce == 0F) return;
        var pose = e.getPoseStack();
        pose.pushPose();
        TILTED.add(r);
        double vx = Mth.lerp(pt, v.xo, v.getX()) - Mth.lerp(pt, r.xo, r.getX());
        double vy = Mth.lerp(pt, v.yo, v.getY()) - Mth.lerp(pt, r.yo, r.getY());
        double vz = Mth.lerp(pt, v.zo, v.getZ()) - Mth.lerp(pt, r.zo, r.getZ());
        float facing = Mth.rotLerp(pt, v.yRotO, v.getYRot());
        float mid = v.getBbHeight() * 0.45F;
        // the same turn about the same point as VehicleRenderer.applyRotations gives the vehicle
        pose.translate(vx, vy + mid + bounce, vz);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180F - facing));
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-pitch));
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-roll));
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(facing - 180F));
        pose.translate(-vx, -vy - mid, -vz);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void untilt(RenderLivingEvent.Post<?, ?> e) {
        if (TILTED.remove(e.getEntity())) e.getPoseStack().popPose();
    }

    // ------------------------------------------------------------------ riding the motorbike

    /** On a motorbike's seat (rider or pillion). */
    public static boolean onBike(LivingEntity e) {
        return BIKE_POSE && e.getVehicle() instanceof VehicleEntity v && v.kind.base() == VehicleKind.MOTORBIKE && v.seatOf(e).type() == VehicleKind.SeatType.SIT;
    }

    /** (tests) times a motorbike pose was put on. */
    public static int bikePoses;
    /** The motorbike riding pose is on. */
    public static boolean BIKE_POSE = false;

    /** Points a limb (resting straight down along +y from its pivot) at a spot in the model's space (+y down, -z forward). */
    private static void aim(net.minecraft.client.model.geom.ModelPart part, float tx, float ty, float tz) {
        float dx = tx - part.x, dy = ty - part.y, dz = tz - part.z;
        float l = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 0.01F) return;
        float a = -(float) Math.acos(Mth.clamp(dy / l, -1F, 1F));
        float sa = Mth.sin(a);
        part.xRot = a;
        part.yRot = Math.abs(sa) < 1e-3F ? 0F : (float) Mth.atan2(dx / (l * sa), dz / (l * sa));
        part.zRot = 0F;
    }

    /** A spot on the bike (model units, +x its right, -z forward, y up) in the rider's model space (hips at bike y hy, z hz). */
    private static float[] bikeSpot(VehicleEntity v, double bx, double by, double bz, double hy, double hz) {
        float s = v.kind.scale;
        return new float[]{(float) (-bx * s), (float) (12 - (by - hy) * s), (float) ((bz - hz) * s)};
    }

    /**
     * Rider: leaning over the tank, hands on the grips (which turn with the bars), feet on the pegs, head up looking
     * ahead. Pillion: sitting up behind, hands on the rider's sides (on the grab rails when riding alone), feet on
     * their pegs. A gun in hand keeps its aim (both arms).
     */
    private static void bikePose(HumanoidModel<?> m, LivingEntity e, VehicleEntity v, float age) {
        bikePoses++;
        boolean rider = v.getPassengers().indexOf(e) == 0;
        m.riding = false;
        float th = rider ? 0.62F : 0.22F;
        float c = Mth.cos(th), sn = Mth.sin(th);
        float headX = m.head.xRot, headY = m.head.yRot;
        m.body.xRot = th; m.body.yRot = 0; m.body.zRot = 0;
        m.body.y = 12 - 12 * c; m.body.z = -12 * sn;
        m.head.y = m.body.y; m.head.z = m.body.z;
        m.head.xRot = Mth.clamp(headX - th * 0.5F, -1.2F, 0.6F); m.head.yRot = headY;   // (eyes up the road, not at the tank)
        float shY = m.body.y + 2 * c, shZ = m.body.z + 2 * sn;
        boolean gun = com.xenowarriors.item.Guns.isGun(e.getMainHandItem()) || com.xenowarriors.item.Guns.isGun(e.getOffhandItem());
        float hy = rider ? 14.6F : 15.2F, hz = rider ? 0F : 10.6F;
        if (!gun) {
            m.rightArm.y = shY; m.rightArm.z = shZ; m.leftArm.y = shY; m.leftArm.z = shZ;
            if (rider) {
                // the grips, turned with the bars (about the steering head at bike z -15.5)
                float st = -Mth.lerp(net.minecraft.client.Minecraft.getInstance().getFrameTime(), v.steerO, v.steer) * Mth.DEG_TO_RAD * STEER_SIGN;
                for (int side = -1; side <= 1; side += 2) {
                    double gx = side * 7.25, gz = -12.5 + 15.5;              // (relative to the steering head)
                    double rx = gx * Math.cos(st) - gz * Math.sin(st), rz = gx * Math.sin(st) + gz * Math.cos(st);
                    float[] g = bikeSpot(v, rx, 17.7, rz - 15.5, hy, hz);
                    aim(side > 0 ? m.rightArm : m.leftArm, g[0], g[1] - 1.5F, g[2]);   // (hand ~1.5 short of the arm's end)
                }
            } else if (!v.getPassengers().isEmpty() && v.getPassengers().get(0) != e) {
                aim(m.rightArm, -4.6F, 9F, -7F);                              // hands on the rider's sides
                aim(m.leftArm, 4.6F, 9F, -7F);
            } else {
                float[] gr = bikeSpot(v, 3.5, 14, 13, hy, hz), gl = bikeSpot(v, -3.5, 14, 13, hy, hz);   // grab rails
                aim(m.rightArm, gr[0], gr[1] - 1.5F, gr[2]); aim(m.leftArm, gl[0], gl[1] - 1.5F, gl[2]);
            }
        } else {
            m.rightArm.y += shY - 2; m.rightArm.z += shZ; m.leftArm.y += shY - 2; m.leftArm.z += shZ;   // (the aim, carried with the shoulders)
        }
        // feet on the pegs, knees out round the tank
        float[] pr = rider ? bikeSpot(v, 4.25, 6.85, -4, hy, hz) : bikeSpot(v, 4, 7.85, 10, hy, hz);
        float[] pl = rider ? bikeSpot(v, -4.25, 6.85, -4, hy, hz) : bikeSpot(v, -4, 7.85, 10, hy, hz);
        m.rightLeg.y = 12; m.rightLeg.z = 0; m.leftLeg.y = 12; m.leftLeg.z = 0;
        aim(m.rightLeg, pr[0], pr[1], pr[2]);
        aim(m.leftLeg, pl[0], pl[1], pl[2]);
        m.hat.copyFrom(m.head);
    }

    /** Which way the bars' bone turns for a + steer (the model's turn sign through GeckoLib's mirroring). */
    static final float STEER_SIGN = 1F;

    /** The rider's spot type, or null when not on a vehicle (or seated inside). */
    public static VehicleKind.SeatType spot(LivingEntity e) {
        if (!(e.getVehicle() instanceof VehicleEntity v)) return null;
        VehicleKind.SeatType t = v.seatOf(e).type();
        return t == VehicleKind.SeatType.SIT ? null : t;
    }

    /** End of HumanoidModel.setupAnim (players, bots and NPCs). */
    public static void apply(HumanoidModel<?> m, LivingEntity e, float age) {
        if (BIKE_POSE && onBike(e)) { bikePose(m, e, (VehicleEntity) e.getVehicle(), age); return; }
        VehicleKind.SeatType t = spot(e);
        if (t == null) return;
        VehicleEntity v = (VehicleEntity) e.getVehicle();
        m.riding = false;
        float sway = Mth.sin(age * 0.11F + e.getId());
        if (t == VehicleKind.SeatType.HANG && v.dangling()) {
            // both hands up on the skid, the body swinging a little under it
            m.rightArm.xRot = (float) -Math.PI + 0.08F; m.rightArm.yRot = 0; m.rightArm.zRot = 0.12F;
            m.leftArm.xRot = (float) -Math.PI + 0.08F; m.leftArm.yRot = 0; m.leftArm.zRot = -0.12F;
            m.rightLeg.xRot = 0.12F + sway * 0.18F; m.rightLeg.yRot = 0; m.rightLeg.zRot = 0.05F;
            m.leftLeg.xRot = 0.12F - sway * 0.18F; m.leftLeg.yRot = 0; m.leftLeg.zRot = -0.05F;
            m.body.xRot = 0; m.head.xRot = Mth.clamp(m.head.xRot, -0.6F, 0.9F);
        } else if (t == VehicleKind.SeatType.HANG) {
            // landed: standing beside it, the inside hand up on the rail
            m.rightArm.xRot = -0.5F; m.rightArm.zRot = 0.1F;
            m.leftArm.xRot = -0.5F; m.leftArm.zRot = -0.1F;
            legsStill(m);
        } else {
            // standing on top: knees a touch bent, arms out for balance
            m.rightArm.xRot = -0.15F + sway * 0.05F; m.rightArm.zRot = 0.55F;
            m.leftArm.xRot = -0.15F - sway * 0.05F; m.leftArm.zRot = -0.55F;
            legsStill(m);
            m.rightLeg.zRot = 0.08F; m.leftLeg.zRot = -0.08F;
        }
        m.hat.copyFrom(m.head);
    }

    private static void legsStill(HumanoidModel<?> m) {
        m.rightLeg.xRot = 0; m.rightLeg.yRot = 0; m.rightLeg.zRot = 0;
        m.leftLeg.xRot = 0; m.leftLeg.yRot = 0; m.leftLeg.zRot = 0;
    }
}
