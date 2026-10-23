package com.xenowarriors.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.xenowarriors.client.mikiko.HekubladeRenderer;
import com.xenowarriors.client.mikiko.MikikoAnimations;
import com.xenowarriors.entity.ThrownHekubladeEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

/** The thrown Season 3 Hekublade: spinning flat like a boomerang. */
public class ThrownHekubladeRenderer extends EntityRenderer<ThrownHekubladeEntity> {
    public ThrownHekubladeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.2F;
    }

    @Override
    public boolean shouldRender(ThrownHekubladeEntity e, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(ThrownHekubladeEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(0, 0.3, 0);
        pose.mulPose(Axis.YP.rotationDegrees((e.tickCount + partialTick) * 38F));   // whirling round
        pose.mulPose(Axis.XP.rotationDegrees(90F));                                   // lying flat
        pose.scale(1.4F, 1.4F, 1.4F);
        HekubladeRenderer.current = e.level().getEntity(e.ownerId());                 // (so it shows the Season 3 blade)
        try {
            Minecraft.getInstance().getItemRenderer().renderStatic(MikikoAnimations.bladeStack(), ItemDisplayContext.NONE, light,
                    OverlayTexture.NO_OVERLAY, pose, buffers, e.level(), e.getId());
        } finally {
            HekubladeRenderer.current = null;
        }
        pose.popPose();
        super.render(e, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ThrownHekubladeEntity e) {
        return new ResourceLocation("textures/item/iron_sword.png");
    }
}
