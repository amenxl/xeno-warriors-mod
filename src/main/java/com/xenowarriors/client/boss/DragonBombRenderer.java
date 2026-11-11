package com.xenowarriors.client.boss;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.xenowarriors.boss.DragonBombEntity;
import com.xenowarriors.client.render.FxDraw;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The Dragon Bomb: a giant sphere of ender energy - a dark violet shell with magenta bands flowing in, a hot pink-white
 * core, two spinning rings - and, once it's thrown, a glowing ring on the ground where it will land.
 */
public class DragonBombRenderer extends EntityRenderer<DragonBombEntity> {
    public DragonBombRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 0F;
    }

    @Override
    public boolean shouldRender(DragonBombEntity e, Frustum f, double x, double y, double z) { return true; }

    @Override
    public void render(DragonBombEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buf, int light) {
        float age = e.tickCount + pt, r = e.radius();
        VertexConsumer vc = FxDraw.glow(buf);
        float pulse = 0.85F + 0.15F * Mth.sin(age * 0.4F);
        pose.pushPose();
        FxDraw.sphere(pose, vc, r, 0.55F, 0.18F, 0.95F, 0.55F, age, -0.8F);
        FxDraw.sphere(pose, vc, r * 0.82F, 0.95F, 0.35F, 1F, 0.35F * pulse, age * 1.2F, 1.1F);
        FxDraw.sphere(pose, vc, r * 0.45F, 1F, 0.85F, 1F, 0.6F * pulse, age, 0.5F, 10, 20);
        for (int i = 0; i < 2; i++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(age * (7 + i * 6) + i * 90));
            pose.mulPose(Axis.XP.rotationDegrees(70 + i * 40));
            FxDraw.ring(pose, vc, r * 1.15F, 0.12F + r * 0.02F, 0.85F, 0.6F, 1F, 0.7F);
            pose.popPose();
        }
        pose.popPose();
        BlockPos t = e.targetPos();
        if (!t.equals(BlockPos.ZERO)) {                                          // where it will land (follows the target while charging)
            pose.pushPose();
            pose.translate(t.getX() + 0.5 - e.getX(), t.getY() + 0.05 - e.getY(), t.getZ() + 0.5 - e.getZ());
            float a = e.thrown() ? 0.8F : 0.35F;
            FxDraw.disc(pose, vc, 0F, DragonBombEntity.BLAST_R, 0.4F, 0.1F, 0.6F, 0.25F * a, 0.05F);
            FxDraw.disc(pose, vc, DragonBombEntity.BLAST_R * 0.94F, DragonBombEntity.BLAST_R, 1F, 0.4F, 1F, a, a);
            pose.popPose();
        }
        super.render(e, yaw, pt, pose, buf, light);
    }

    @Override
    public ResourceLocation getTextureLocation(DragonBombEntity e) { return FxDraw.WHITE; }
}
