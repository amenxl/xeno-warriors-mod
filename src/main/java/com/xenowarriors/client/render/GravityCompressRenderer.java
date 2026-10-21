package com.xenowarriors.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.xenowarriors.entity.GravityCompressEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Gravity Compress: a violet sphere with pressure bands rippling inward, three spinning rings
 * that tighten with it, and a dark crater ring on the ground that cracks wider while it crushes.
 */
public class GravityCompressRenderer extends EntityRenderer<GravityCompressEntity> {
    public GravityCompressRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0F;
    }

    @Override
    public boolean shouldRender(GravityCompressEntity e, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(GravityCompressEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = e.tickCount + partialTick;
        float radius = e.radius(age);
        if (radius <= 0.02F) return;
        float closing = Mth.clamp((age - GravityCompressEntity.FORM) / (GravityCompressEntity.CLOSED - GravityCompressEntity.FORM), 0F, 1F);
        float alpha = Mth.clamp(age / 3F, 0F, 1F);
        // violet that heats to hot magenta as it tightens
        float r = 0.62F + 0.3F * closing, g = 0.32F - 0.12F * closing, b = 1.0F;
        VertexConsumer vc = FxDraw.glow(buffers);

        pose.pushPose();
        FxDraw.sphere(pose, vc, radius, r, g, b, 0.42F * alpha, age, -0.6F - closing * 0.6F);
        FxDraw.sphere(pose, vc, radius * 0.97F, 1F, 0.85F, 1F, 0.08F * alpha, age * 1.3F, 0.9F);

        // three tilted rings spinning around the sphere
        for (int i = 0; i < 3; i++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(age * (9 + i * 5) + i * 60));
            pose.mulPose(Axis.XP.rotationDegrees(55 + i * 35));
            FxDraw.ring(pose, vc, radius * 1.08F, 0.07F + 0.04F * closing, 1F, 0.8F, 1F, 0.75F * alpha);
            pose.popPose();
        }
        pose.popPose();

        // crater ring on the ground under the target
        LivingEntity target = e.target();
        float groundOffset = target != null ? -target.getBbHeight() / 2 + 0.03F : -radius + 0.03F;
        pose.pushPose();
        pose.translate(0, groundOffset, 0);
        float crater = 1.2F + 1.6F * closing;
        FxDraw.disc(pose, vc, 0F, crater, 0.15F, 0.05F, 0.3F, 0.55F * alpha, 0F);
        FxDraw.disc(pose, vc, crater * 0.92F, crater, r, g, b, 0.8F * alpha, 0.8F * alpha);
        pose.popPose();

        super.render(e, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(GravityCompressEntity e) {
        return FxDraw.WHITE;
    }
}
