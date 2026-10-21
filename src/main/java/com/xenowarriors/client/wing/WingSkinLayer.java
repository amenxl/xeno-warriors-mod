package com.xenowarriors.client.wing;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;

/**
 * Skins for Rhalix's winged companions, drawn over the vanilla look: a colored wash (Shadow, Rose) or a soft
 * glow (Frost, Ember, Golden), and Void does both.
 */
public class WingSkinLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private static final Int2IntOpenHashMap SKIN = new Int2IntOpenHashMap();
    //                                   wash r,g,b,a                  glow r,g,b
    private static final float[][] LOOK = {
            null,
            {0.12F, 0.1F, 0.18F, 0.6F, 0, 0, 0},           // Shadow
            {1F, 0.45F, 0.7F, 0.5F, 0, 0, 0},              // Rose
            {0.7F, 0.9F, 1F, 0.35F, 0.25F, 0.45F, 0.65F},   // Frost
            {1F, 0.5F, 0.2F, 0.3F, 0.6F, 0.25F, 0.05F},     // Ember
            {1F, 0.85F, 0.3F, 0.35F, 0.55F, 0.42F, 0.1F},   // Golden
            {0.35F, 0.1F, 0.6F, 0.55F, 0.35F, 0.1F, 0.55F}, // Void
    };

    public WingSkinLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    public static void set(int id, int skin) {
        if (skin == 0) SKIN.remove(id);
        else SKIN.put(id, skin);
    }

    public static int get(int id) {
        return SKIN.getOrDefault(id, 0);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T e, float limbSwing, float limbSwingAmount,
                       float partialTick, float age, float yaw, float pitch) {
        int skin = SKIN.getOrDefault(e.getId(), 0);
        if (skin <= 0 || skin >= LOOK.length || e.isInvisible()) return;
        float[] l = LOOK[skin];
        var tex = getTextureLocation(e);
        if (l[3] > 0) {
            getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light,
                    OverlayTexture.NO_OVERLAY, l[0], l[1], l[2], l[3]);
        }
        if (l[4] + l[5] + l[6] > 0) {
            float pulse = 0.75F + 0.25F * (float) Math.sin(age * 0.1F);
            getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(tex)), LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, l[4] * pulse, l[5] * pulse, l[6] * pulse, 1F);
        }
    }
}
