package com.xenowarriors.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.xenowarriors.pickup.HealthOrbEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** A small glowing health orb: a bright pink-white core in a soft red glow, bobbing and pulsing; it fades in its last seconds. */
public class HealthOrbRenderer extends EntityRenderer<HealthOrbEntity> {
    public HealthOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.1F;
    }

    @Override
    public void render(HealthOrbEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = e.tickCount + partialTick;
        float pulse = 1F + Mth.sin(age * 0.25F) * 0.1F;
        float fade = Mth.clamp((HealthOrbEntity.LIFE - age) / 60F, 0.2F, 1F) * (age > HealthOrbEntity.LIFE - 100 && ((int) age / 4) % 2 == 0 ? 0.5F : 1F);
        pose.pushPose();
        pose.translate(0, 0.25 + Mth.sin(age * 0.12F) * 0.08, 0);
        pose.mulPose(Axis.YP.rotationDegrees(age * 4F));
        float r = 0.14F * pulse;
        FxDraw.sphere(pose, FxDraw.glow(buffers), r * 0.55F, 1F, 0.9F, 0.92F, 0.95F * fade, age, 1.5F);
        FxDraw.sphere(pose, FxDraw.glow(buffers), r, 1F, 0.25F, 0.35F, 0.75F * fade, age, -1.2F);
        FxDraw.sphere(pose, FxDraw.glow(buffers), r * 1.7F, 1F, 0.15F, 0.2F, 0.28F * fade, age * 1.3F, 2F);
        pose.popPose();
        super.render(e, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(HealthOrbEntity e) {
        return FxDraw.WHITE;
    }
}
