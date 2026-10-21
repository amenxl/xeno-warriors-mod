package com.xenowarriors.client.corrupted;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;

/** Washes a possessed body in a pulsing translucent purple (drawn over the body with its own texture). */
public class CorruptionTintLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private final boolean always;

    public CorruptionTintLayer(RenderLayerParent<T, M> parent, boolean always) {
        super(parent);
        this.always = always;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible() || (!always && !CorruptionClient.is(entity.getId()))) return;
        float pulse = 0.38F + 0.12F * (float) Math.sin(ageInTicks * 0.12F);
        getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(getTextureLocation(entity))),
                light, OverlayTexture.NO_OVERLAY, 0.55F, 0.12F, 0.9F, pulse);
    }
}
