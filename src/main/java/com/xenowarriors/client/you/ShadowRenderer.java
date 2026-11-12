package com.xenowarriors.client.you;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.you.DevilShadowEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

/** A demon's shadow: its owner in their own skin, drawn near black with a red tinge, a little see-through. */
public class ShadowRenderer extends HumanoidMobRenderer<DevilShadowEntity, PlayerModel<DevilShadowEntity>> {
    public ShadowRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false) {
            @Override
            public void renderToBuffer(PoseStack ps, com.mojang.blaze3d.vertex.VertexConsumer vc, int l, int o, float r, float g, float b, float a) {
                super.renderToBuffer(ps, vc, l, o, r * 0.16F, g * 0.06F, b * 0.09F, a * 0.92F);
            }
        }, 0.5F);
    }

    /** The owner's own skin. */
    @Override
    public ResourceLocation getTextureLocation(DevilShadowEntity e) {
        var id = e.ownerId();
        if (id == null) return DefaultPlayerSkin.getDefaultSkin();
        var conn = Minecraft.getInstance().getConnection();
        var info = conn == null ? null : conn.getPlayerInfo(id);
        return info != null ? info.getSkinLocation() : DefaultPlayerSkin.getDefaultSkin(id);
    }

    @Override
    protected RenderType getRenderType(DevilShadowEntity e, boolean visible, boolean translucent, boolean glowing) {
        return RenderType.entityTranslucent(getTextureLocation(e));
    }
}
