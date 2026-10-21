package com.xenowarriors.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.entity.EarthBulletEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** A small tumbling rock with a faint streak behind it. */
public class EarthBulletRenderer extends EntityRenderer<EarthBulletEntity> {
    public EarthBulletRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.1F;
    }

    @Override
    public void render(EarthBulletEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = e.tickCount + partialTick;
        double wx = Mth.lerp(partialTick, e.xOld, e.getX()), wy = Mth.lerp(partialTick, e.yOld, e.getY()), wz = Mth.lerp(partialTick, e.zOld, e.getZ());
        float grow = Mth.clamp(age / 2F, 0.3F, 1F);
        RockRender.draw(pose, buffers, e.level(), wx, wy, wz, 0, 0, 0, 0.34F * grow, e.getId(), age * 38F);
        // motion streak
        Vec3 v = e.getDeltaMovement().normalize().scale(-1.6);
        FxDraw.beam(pose, FxDraw.glow(buffers), (float) v.x, (float) v.y, (float) v.z, 0.09F, 0.85F, 0.7F, 0.5F, 0.45F);
        super.render(e, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(EarthBulletEntity e) {
        return FxDraw.WHITE;
    }
}
