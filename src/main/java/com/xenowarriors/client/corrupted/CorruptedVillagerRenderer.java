package com.xenowarriors.client.corrupted;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.corrupted.CorruptedVillagerEntity;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/** Possessed people: ordinary-looking townsfolk with drained skin, purple veins and glowing eyes. */
public class CorruptedVillagerRenderer extends HumanoidMobRenderer<CorruptedVillagerEntity, ZombieModel<CorruptedVillagerEntity>> {
    private static final int SKINS = 8;
    private static final ResourceLocation[] SKIN = new ResourceLocation[SKINS];
    private static final RenderType EYES = RenderType.eyes(XenoWarriors.id("textures/entity/corrupted/possessed_eyes.png"));

    static {
        for (int i = 0; i < SKINS; i++) SKIN[i] = XenoWarriors.id("textures/entity/corrupted/possessed_" + i + ".png");
    }

    public CorruptedVillagerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new ZombieModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return EYES;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(CorruptedVillagerEntity entity) {
        return SKIN[Math.floorMod(entity.getUUID().hashCode(), SKINS)];
    }
}
