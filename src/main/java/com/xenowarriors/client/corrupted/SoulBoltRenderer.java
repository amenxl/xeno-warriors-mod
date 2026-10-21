package com.xenowarriors.client.corrupted;

import com.xenowarriors.corrupted.SoulBoltEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/** Soul bolts are pure flame - their particle trail is all you see. */
public class SoulBoltRenderer extends EntityRenderer<SoulBoltEntity> {
    public SoulBoltRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public ResourceLocation getTextureLocation(SoulBoltEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
