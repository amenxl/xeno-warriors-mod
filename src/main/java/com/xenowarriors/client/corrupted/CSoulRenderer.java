package com.xenowarriors.client.corrupted;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.corrupted.CSoulBossEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** Draws the C-Soul three times the size of a normal soul. */
public class CSoulRenderer extends MobRenderer<CSoulBossEntity, CSoulModel> {
    private static final ResourceLocation TEX = XenoWarriors.id("textures/entity/corrupted/c_soul.png");
    private static final RenderType EYES = RenderType.eyes(XenoWarriors.id("textures/entity/corrupted/c_soul_eyes.png"));
    public static final float SCALE = 3.0F;

    public CSoulRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new CSoulModel(ctx.bakeLayer(CSoulModel.LAYER)), 1.4F);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return EYES;
            }
        });
    }

    @Override
    protected void scale(CSoulBossEntity entity, PoseStack pose, float partialTick) {
        pose.scale(SCALE, SCALE, SCALE);
    }

    @Override
    protected int getBlockLightLevel(CSoulBossEntity entity, BlockPos pos) {
        return 12;
    }

    @Override
    public ResourceLocation getTextureLocation(CSoulBossEntity entity) {
        return TEX;
    }
}
