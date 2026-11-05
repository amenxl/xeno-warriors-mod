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

    /** Riding it in first person: the body goes see-through so you can see the road. */
    private static boolean inside(VehicleEntity e) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        return mc.player != null && mc.player.getVehicle() == e && mc.getCameraEntity() == mc.player && mc.options.getCameraType().isFirstPerson();
    }

    @Override
    public net.minecraft.client.renderer.RenderType getRenderType(VehicleEntity e, net.minecraft.resources.ResourceLocation texture,
                                                                   net.minecraft.client.renderer.MultiBufferSource buffers, float pt) {
        return inside(e) ? net.minecraft.client.renderer.RenderType.entityTranslucent(texture) : super.getRenderType(e, texture, buffers, pt);
    }

    @Override
    public software.bernie.geckolib.core.object.Color getRenderColor(VehicleEntity e, float pt, int light) {
        return inside(e) ? software.bernie.geckolib.core.object.Color.ofRGBA(1F, 1F, 1F, 0.18F) : super.getRenderColor(e, pt, light);
    }

    @Override
    protected void applyRotations(VehicleEntity e, PoseStack pose, float age, float yaw, float pt) {
        // faces where the vehicle points (its own yaw, smoothed between ticks - not a mob's lagging body turn)
        float facing = net.minecraft.util.Mth.rotLerp(pt, e.yRotO, e.getYRot());
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-facing));    // (the models face -z)
        // tilts with its motion: nose down speeding along in the air, banking into turns, nose up a step
        float pitch = net.minecraft.util.Mth.lerp(pt, e.pitchTiltO, e.pitchTilt), roll = net.minecraft.util.Mth.lerp(pt, e.rollTiltO, e.rollTilt);
        if (Math.abs(pitch) > 0.05F || Math.abs(roll) > 0.05F) {
            float mid = e.getBbHeight() * 0.45F;
            pose.translate(0, mid, 0);
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(pitch));
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(roll));
            pose.translate(0, -mid, 0);
        }
        pose.scale(e.kind.scale, e.kind.scale, e.kind.scale);
    }
}
