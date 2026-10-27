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
        shadowRadius = 1.2F;
    }

    @Override
    protected void applyRotations(VehicleEntity e, PoseStack pose, float age, float yaw, float pt) {
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180F - yaw));
    }
}
