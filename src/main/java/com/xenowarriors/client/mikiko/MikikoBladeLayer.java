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
        boolean back = phase == MikikoAnimations.BI_BACK || (phase == MikikoAnimations.BI_SHEATHE && t >= 8)
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
            pose.translate(0F, 0.42F, 0.26F);                    // across his back
            pose.mulPose(Axis.ZP.rotationDegrees(-45F));         // lying sideways (horizontal) across his back
        }
        pose.scale(1.1F, 1.1F, 1.1F);
        mc.getItemRenderer().renderStatic(MikikoAnimations.bladeStack(), ItemDisplayContext.NONE, light,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, pose, buffers, player.level(), player.getId());
        pose.popPose();
    }
}
