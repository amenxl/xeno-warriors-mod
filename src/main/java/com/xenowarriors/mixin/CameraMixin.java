package com.xenowarriors.mixin;

import com.xenowarriors.client.UltimateCutscene;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets the ultimate cutscenes take over the camera, and backs the third-person camera off behind vehicles. Also
 * reachable as {@link com.xenowarriors.client.XwCamera} for mods that build the camera themselves (Epic Fight's
 * third-person camera skips vanilla's setup, so this TAIL never runs then: its own hook calls us instead).
 */
@Mixin(Camera.class)
public abstract class CameraMixin implements com.xenowarriors.client.XwCamera {
    @Shadow private boolean detached;

    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Shadow protected abstract void setPosition(double x, double y, double z);

    @Shadow protected abstract double getMaxZoom(double zoom);

    @Shadow protected abstract void move(double distanceOffset, double verticalOffset, double horizontalOffset);

    @Inject(method = "setup", at = @At("TAIL"))
    private void xenowarriors$ourCamera(BlockGetter level, Entity entity, boolean detached, boolean mirrored,
                                        float partialTick, CallbackInfo ci) {
        xenowarriors$applyOurs(entity, detached, partialTick);
    }

    @Override
    public void xenowarriors$applyOurs(Entity entity, boolean detached, float partialTick) {
        // vehicles: the third-person camera backs off (so the car isn't filling the screen) and sits a little higher
        if (entity != null && entity.getVehicle() instanceof com.xenowarriors.vehicle.VehicleEntity v) {
            if (detached) {
                double extra = v.kind.base() == com.xenowarriors.vehicle.VehicleKind.MOTORBIKE ? 2.0 : 3.5;
                move(-getMaxZoom(extra), 0.6, 0.0);
            } else if (entity instanceof net.minecraft.world.entity.LivingEntity le && com.xenowarriors.client.vehicle.VehicleRiderPose.onBike(le)
                    && v.getPassengers().indexOf(le) == 0) {
                // on the motorbike the rider leans over the tank: the eyes go with the head, forward and down
                float yaw = v.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
                var at = ((net.minecraft.client.Camera) (Object) this).getPosition();
                setPosition(at.x - net.minecraft.util.Mth.sin(yaw) * 0.42, at.y - 0.2, at.z + net.minecraft.util.Mth.cos(yaw) * 0.42);
            }
            // (first person: the seats sit the driver's eyes at the windscreen, looking out over the dash - no lift needed)
        }
        // the cutscenes
        double[] cam = UltimateCutscene.camera(partialTick);
        if (cam == null) cam = com.xenowarriors.client.TeamUltCutscene.camera(partialTick);
        if (cam == null) return;
        this.detached = true;
        setRotation((float) cam[3], (float) cam[4]);
        setPosition(cam[0], cam[1], cam[2]);
    }
}
