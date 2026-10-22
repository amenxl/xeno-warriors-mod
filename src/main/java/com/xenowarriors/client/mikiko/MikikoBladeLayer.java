package com.xenowarriors.client.mikiko;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;

/** Draws the Hekublade in Mikiko's empty hand while it's summoned mid-combo. */
public class MikikoBladeLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private final ItemInHandRenderer itemRenderer;

    public MikikoBladeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, ItemInHandRenderer itemRenderer) {
        super(parent);
        this.itemRenderer = itemRenderer;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        HekubladeRenderer.current = player;
        try {
            renderBlades(pose, buffers, light, player, partialTick);
        } finally {
            HekubladeRenderer.current = null;
        }
    }

    private void renderBlades(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float partialTick) {
        if (MikikoAnimations.offhandBlade(player)) {
            // the Hekublade in his off hand, crossed with his sword
            pose.pushPose();
            getParentModel().translateToHand(HumanoidArm.LEFT, pose);
            pose.mulPose(Axis.XP.rotationDegrees(-90F));
            pose.mulPose(Axis.YP.rotationDegrees(180F));
            pose.translate(-1F / 16F, 0.125F, -0.625F);
            itemRenderer.renderItem(player, MikikoAnimations.bladeStack(), ItemDisplayContext.THIRD_PERSON_LEFT_HAND, true, pose, buffers, light);
            pose.popPose();
        }
        if (MikikoAnimations.swordShouldered(player)) {
            // hurt and tired: the sword lies horizontally across his shoulders, behind his head (clear of it)
            pose.pushPose();
            getParentModel().body.translateAndRotate(pose);
            pose.translate(-0.08F, -0.34F, 0.42F);
            pose.mulPose(Axis.ZP.rotationDegrees(-45F));
            pose.scale(1.05F, 1.05F, 1.05F);
            net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(player.getMainHandItem(), ItemDisplayContext.NONE,
                    light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, pose, buffers, player.level(), player.getId());
            pose.popPose();
        }
        float[] bi = MikikoAnimations.bladeIdle(player, partialTick);
        if (bi != null) renderIdleBlade(pose, buffers, light, player, (int) bi[0], bi[1]);
        if (!MikikoAnimations.showSummonedBlade(player)) return;
        pose.pushPose();
        getParentModel().translateToHand(HumanoidArm.RIGHT, pose);
        pose.mulPose(Axis.XP.rotationDegrees(-90F));
        pose.mulPose(Axis.YP.rotationDegrees(180F));
        pose.translate(1F / 16F, 0.125F, -0.625F);
        itemRenderer.renderItem(player, MikikoAnimations.bladeStack(), ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, pose, buffers, light);
        pose.popPose();
    }

    /** The Hekublade spinning up through the air above his hand, or slung diagonally across his back. */
    private void renderIdleBlade(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, int phase, float t) {
        boolean toss = phase == MikikoAnimations.BI_TOSS && t >= 6 && t < 30;
        boolean back = phase == MikikoAnimations.BI_BACK || phase == MikikoAnimations.BI_RUN || (phase == MikikoAnimations.BI_SHEATHE && t >= 8)
                || (phase == MikikoAnimations.BI_DRAW && t < 8);
        if (!toss && !back) return;
        var mc = net.minecraft.client.Minecraft.getInstance();
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        if (toss) {
            float u = (t - 6F) / 24F;
            float rise = 4F * u * (1F - u) * 1.7F;            // up and back down into his hand
            pose.translate(-0.36F, 0.62F - rise, -0.35F);       // model space: y points down, -x is his right
            pose.mulPose(Axis.XP.rotationDegrees(u * 720F));     // two full flips
            pose.mulPose(Axis.ZP.rotationDegrees(45F));
        } else {
            pose.translate(0.05F, 0.58F, 0.3F);                  // across his lower back, hilt in his right hand
            pose.mulPose(Axis.ZP.rotationDegrees(-45F));         // lying sideways (horizontal) across his back
        }
        pose.scale(1.1F, 1.1F, 1.1F);
        mc.getItemRenderer().renderStatic(MikikoAnimations.bladeStack(), ItemDisplayContext.NONE, light,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, pose, buffers, player.level(), player.getId());
        pose.popPose();
    }
}
