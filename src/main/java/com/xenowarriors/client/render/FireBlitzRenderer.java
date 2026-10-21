package com.xenowarriors.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.xenowarriors.entity.FireBlitzEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** A burning orb: white-hot core, orange body, flickering red shell. */
public class FireBlitzRenderer extends EntityRenderer<FireBlitzEntity> {
    public FireBlitzRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0F;
    }

    @Override
    public void render(FireBlitzEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = e.tickCount + partialTick;
        float flicker = 1F + Mth.sin(age * 1.3F) * 0.08F + Mth.sin(age * 2.7F) * 0.05F;
        float r = (e.isStuck() ? 0.28F : 0.34F) * flicker;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(age * 9F));
        FxDraw.sphere(pose, FxDraw.glow(buffers), r * 0.45F, 1F, 0.98F, 0.85F, 0.95F, age, 1.5F);
        FxDraw.sphere(pose, FxDraw.glow(buffers), r * 0.75F, 1F, 0.62F, 0.12F, 0.7F, age, -1.2F);
        FxDraw.sphere(pose, FxDraw.glow(buffers), r, 0.95F, 0.22F, 0.08F, 0.38F, age * 1.4F, 2F);
        pose.popPose();
        super.render(e, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(FireBlitzEntity e) {
        return FxDraw.WHITE;
    }
}
