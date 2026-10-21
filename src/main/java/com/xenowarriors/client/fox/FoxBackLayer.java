package com.xenowarriors.client.fox;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.xenowarriors.mixin.FoxAccessor;
import net.minecraft.client.model.FoxModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Fox;

/** The fox Mikiko carries, curled up asleep across his back. */
public class FoxBackLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation[] TEXTURES = {
            new ResourceLocation("textures/entity/fox/fox_sleep.png"),
            new ResourceLocation("textures/entity/fox/snow_fox_sleep.png")};
    private final FoxModel<Fox> model;
    private Fox dummy;

    public FoxBackLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models) {
        super(parent);
        model = new FoxModel<>(models.bakeLayer(ModelLayers.FOX));
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbAmount, float partialTick, float age, float headYaw, float headPitch) {
        int variant = FoxClient.back(player.getUUID());
        if (variant < 0 || player.isInvisible()) return;
        if (dummy == null || dummy.level() != player.level()) {
            dummy = EntityType.FOX.create(player.level());
            if (dummy == null) return;
            ((FoxAccessor) dummy).xenowarriors$setSleeping(true);
        }
        dummy.tickCount = player.tickCount;
        model.young = false;
        model.attackTime = 0;
        model.riding = false;
        model.prepareMobModel(dummy, 0F, 0F, partialTick);
        model.setupAnim(dummy, 0F, 0F, age, 0F, 0F);

        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        // upper back, belly against him, lying slightly across his shoulders
        pose.translate(0.0F, 0.36F, 0.16F);
        pose.mulPose(Axis.ZP.rotationDegrees(70F));
        pose.mulPose(Axis.XP.rotationDegrees(-90F));
        // a soft breathing rise and fall
        float breathe = 1F + 0.02F * (float) Math.sin(age * 0.08F);
        pose.scale(0.62F, 0.62F * breathe, 0.62F);
        pose.translate(0.0F, -1.5F, -0.2F);
        ResourceLocation tex = variant >= 10
                ? com.xenowarriors.XenoWarriors.id("textures/entity/fox/" + com.xenowarriors.fox.FoxSkins.get(variant - 10).id() + "_sleep.png")
                : TEXTURES[Math.min(variant, 1)];
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(tex)),
                light, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
        pose.popPose();
    }
}
