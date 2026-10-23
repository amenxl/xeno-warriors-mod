package com.xenowarriors.client.npc;

import com.xenowarriors.npc.KiBlastEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/** Ki blasts are pure light - the glowing particles are the ball. */
public class KiBlastRenderer extends EntityRenderer<KiBlastEntity> {
    public KiBlastRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public ResourceLocation getTextureLocation(KiBlastEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
