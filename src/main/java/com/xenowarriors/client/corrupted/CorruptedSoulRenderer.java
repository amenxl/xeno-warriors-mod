package com.xenowarriors.client.corrupted;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.corrupted.CorruptedSoulEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public class CorruptedSoulRenderer extends MobRenderer<CorruptedSoulEntity, CorruptedSoulModel> {
    private static final ResourceLocation TEX = XenoWarriors.id("textures/entity/corrupted/corrupted_soul.png");
    private static final RenderType EYES = RenderType.eyes(XenoWarriors.id("textures/entity/corrupted/corrupted_soul_eyes.png"));

    public CorruptedSoulRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new CorruptedSoulModel(ctx.bakeLayer(CorruptedSoulModel.LAYER)), 0.2F);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return EYES;
            }
        });
    }

    @Override
    protected int getBlockLightLevel(CorruptedSoulEntity entity, BlockPos pos) {
        return 12;
    }

    @Override
    public ResourceLocation getTextureLocation(CorruptedSoulEntity entity) {
        return TEX;
    }
}
