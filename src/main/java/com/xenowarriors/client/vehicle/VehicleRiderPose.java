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

    /** The rider's spot type, or null when not on a vehicle (or seated inside). */
    public static VehicleKind.SeatType spot(LivingEntity e) {
        if (!(e.getVehicle() instanceof VehicleEntity v)) return null;
        VehicleKind.SeatType t = v.seatOf(e).type();
        return t == VehicleKind.SeatType.SIT ? null : t;
    }

    /** End of HumanoidModel.setupAnim (players, bots and NPCs). */
    public static void apply(HumanoidModel<?> m, LivingEntity e, float age) {
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
