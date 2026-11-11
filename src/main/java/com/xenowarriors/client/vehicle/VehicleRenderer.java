package com.xenowarriors.client.vehicle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.vehicle.VehicleEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Draws vehicles with GeckoLib; headlights and tail lights glow. No death flop - vehicles don't fall over. */
public class VehicleRenderer extends GeoEntityRenderer<VehicleEntity> {
    public VehicleRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new VehicleModel());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        shadowRadius = 1.5F;
    }

    // (first person: drawn as it is - the v2 models have a real cabin, so the driver looks out through the windscreen
    // over the dash; the old see-through body is gone)

    @Override
    protected void applyRotations(VehicleEntity e, PoseStack pose, float age, float yaw, float pt) {
        // faces where the vehicle points (its own yaw, smoothed between ticks - not a mob's lagging body turn)
        float facing = net.minecraft.util.Mth.rotLerp(pt, e.yRotO, e.getYRot());
        // The models face -z; an entity at yaw 0 goes +z, so they're turned round (180 - yaw, as GeckoLib does for its own
        // entities). Before v0.221 this was -yaw alone: every vehicle was drawn back to front - nose toward the driver.
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180F - facing));
        // tilts with its motion: nose down speeding along in the air, banking into turns, nose up a step
        float pitch = net.minecraft.util.Mth.lerp(pt, e.pitchTiltO, e.pitchTilt), roll = net.minecraft.util.Mth.lerp(pt, e.rollTiltO, e.rollTilt);
        if (Math.abs(pitch) > 0.05F || Math.abs(roll) > 0.05F) {
            float mid = e.getBbHeight() * 0.45F;
            pose.translate(0, mid, 0);
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-pitch));    // (signs: in the turned-round frame, + pitch here is nose up)
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-roll));
            pose.translate(0, -mid, 0);
        }
        pose.scale(e.kind.scale, e.kind.scale, e.kind.scale);
    }
}
