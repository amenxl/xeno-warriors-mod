package com.xenowarriors.client.corrupted;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.corrupted.CorruptedVillagerEntity;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/** A villager body with a corrupted soul inside: purple-washed skin and glowing eyes. */
public class CorruptedVillagerRenderer extends MobRenderer<CorruptedVillagerEntity, VillagerModel<CorruptedVillagerEntity>> {
    private static final ResourceLocation SKIN = new ResourceLocation("minecraft", "textures/entity/villager/villager.png");
    private static final RenderType EYES = RenderType.eyes(XenoWarriors.id("textures/entity/corrupted/corrupted_villager_eyes.png"));

    public CorruptedVillagerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new VillagerModel<>(ctx.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
        addLayer(new CorruptionTintLayer<>(this, true));
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return EYES;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(CorruptedVillagerEntity entity) {
        return SKIN;
    }
}
