package com.xenowarriors.client.corrupted;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.corrupted.CorruptedSoulEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public class CorruptedSoulRenderer extends MobRenderer<CorruptedSoulEntity, CorruptedSoulModel<CorruptedSoulEntity>> {
    private static final ResourceLocation TEX = XenoWarriors.id("textures/entity/corrupted/corrupted_soul.png");
    private static final RenderType EYES = RenderType.eyes(XenoWarriors.id("textures/entity/corrupted/corrupted_soul_eyes.png"));

    public CorruptedSoulRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new CorruptedSoulModel<>(ctx.bakeLayer(CorruptedSoulModel.LAYER)), 0.2F);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return EYES;
            }
        });
        // a weapon it carries (a Simply Swords blade, now and then): floating at its side, swung at you
        var hands = ctx.getItemInHandRenderer();
        addLayer(new net.minecraft.client.renderer.entity.layers.RenderLayer<>(this) {
            @Override
            public void render(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buf, int light, CorruptedSoulEntity e,
                               float limb, float limbAmt, float pt, float age, float yawHead, float pitch) {
                var stack = e.getMainHandItem();
                if (stack.isEmpty()) return;
                pose.pushPose();
                pose.translate(0.42F, 0.86F + net.minecraft.util.Mth.sin(age * 0.12F) * 0.04F, -0.05F);
                float swing = e.getAttackAnim(pt);
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90F + net.minecraft.util.Mth.sin(swing * (float) Math.PI) * 110F));
                pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180F));
                pose.translate(1F / 16F, 0.125F, -0.4F);
                hands.renderItem(e, stack, net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, pose, buf, light);
                pose.popPose();
            }
        });
    }

    @Override
    protected int getBlockLightLevel(CorruptedSoulEntity entity, BlockPos pos) {
        return Math.max(4, super.getBlockLightLevel(entity, pos));   // dark: it doesn't glow, only its eyes do
    }

    @Override
    public ResourceLocation getTextureLocation(CorruptedSoulEntity entity) {
        return TEX;
    }
}
