package com.xenowarriors.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.xenowarriors.entity.BlazingWindEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The fire tornado: a funnel of spinning glowing bands - white-hot at the base, orange through the
 * middle, deep red at the top - wobbling as it twists, with three flame ribbons winding up it.
 */
public class BlazingWindRenderer extends EntityRenderer<BlazingWindEntity> {
    private static final int BANDS = 30;

    public BlazingWindRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0F;
    }

    @Override
    public boolean shouldRender(BlazingWindEntity e, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(BlazingWindEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = e.tickCount + partialTick;
        float h = e.height(age), r = e.radius(age);
        if (h < 0.2F) return;
        float alpha = age > BlazingWindEntity.BURST ? 1F - BlazingWindEntity.smooth((age - BlazingWindEntity.BURST) / 15F) : 1F;
        for (int i = 0; i < BANDS; i++) {
            float k = i / (float) (BANDS - 1);
            float y = k * h;
            float rr = r * (0.28F + 0.72F * (float) Math.pow(k, 1.25));
            float wob = Mth.sin(k * 6F - age * 0.25F) * 0.35F * (0.4F + k);
            // color: white-yellow at the bottom -> orange -> red at the top
            float cr = 1F, cg = Mth.lerp(k, 0.92F, 0.18F), cb = Mth.lerp(Math.min(1F, k * 2.5F), 0.6F, 0.05F);
            pose.pushPose();
            pose.translate(Mth.cos(age * 0.15F + k * 3F) * wob, y, Mth.sin(age * 0.15F + k * 3F) * wob);
            pose.mulPose(Axis.YP.rotationDegrees(age * (22F - k * 10F) + i * 31F));
            pose.mulPose(Axis.XP.rotationDegrees(Mth.sin(i * 1.7F) * 6F));
            float a = (0.42F - 0.18F * k) * alpha * (0.75F + 0.25F * Mth.sin(age * 0.6F + i));
            FxDraw.ring(pose, FxDraw.glow(buffers), rr, h / BANDS * 1.6F, cr, cg, cb, a);
            FxDraw.ring(pose, FxDraw.glow(buffers), rr * 0.7F, h / BANDS * 1.2F, 1F, Math.min(1F, cg + 0.25F), cb + 0.15F, a * 0.6F);
            pose.popPose();
        }
        // flame ribbons spiralling up
        for (int s = 0; s < 3; s++) {
            float prevX = 0, prevY = 0, prevZ = 0;
            for (int i = 0; i <= 18; i++) {
                float k = i / 18F;
                float ang = k * 9F + age * 0.35F + s * 2.094F;
                float rr = r * (0.3F + 0.75F * k);
                float x = Mth.cos(ang) * rr, y = k * h, z = Mth.sin(ang) * rr;
                if (i > 0) {
                    pose.pushPose();
                    pose.translate(prevX, prevY, prevZ);
                    FxDraw.beam(pose, FxDraw.glow(buffers), x - prevX, y - prevY, z - prevZ, 0.22F + 0.2F * k,
                            1F, 0.55F - 0.3F * k, 0.1F, 0.7F * alpha);
                    pose.popPose();
                }
                prevX = x; prevY = y; prevZ = z;
            }
        }
        // scorched glowing ring on the ground
        FxDraw.disc(pose, FxDraw.glow(buffers), r * 0.8F, r * 1.6F, 1F, 0.45F, 0.1F, 0.5F * alpha, 0F);
        super.render(e, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(BlazingWindEntity e) {
        return FxDraw.WHITE;
    }
}
