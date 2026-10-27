package com.xenowarriors.client.soul;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.render.DetailModel;
import com.xenowarriors.soul.SoulEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.Map;

/** A split soul: its owner's character look, washed pale and see-through, with a cold blue glow. */
public class SoulRenderer extends HumanoidMobRenderer<SoulEntity, PlayerModel<SoulEntity>> {
    private static final Map<CharacterType, DetailModel> MODELS = new EnumMap<>(CharacterType.class);
    private static final float PALE = 0.45F;

    public SoulRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false) {
            @Override
            public void renderToBuffer(PoseStack ps, com.mojang.blaze3d.vertex.VertexConsumer vc, int l, int o, float r, float g, float b, float a) {
                super.renderToBuffer(ps, vc, l, o, r * 0.55F, g * 0.8F, b, a * 0.55F);   // pale and see-through
            }
        }, 0.3F);
        addLayer(new Details(this));
    }

    private static DetailModel model(CharacterType t) {
        return MODELS.computeIfAbsent(t, k -> DetailModel.loadPath("/assets/" + XenoWarriors.MOD_ID + "/models/detail/" + k.getId() + ".json"));
    }

    @Override public ResourceLocation getTextureLocation(SoulEntity e) { return e.character().getSkin(); }

    @Override
    protected RenderType getRenderType(SoulEntity e, boolean visible, boolean translucent, boolean glowing) {
        return RenderType.entityTranslucent(getTextureLocation(e));
    }

    @Override protected float getWhiteOverlayProgress(SoulEntity e, float partial) { return PALE; }

    @Override
    protected int getBlockLightLevel(SoulEntity e, net.minecraft.core.BlockPos pos) { return 15; }

    @Override
    public void render(SoulEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(e, yaw, partial, pose, buffers, light);
        if (e.tickCount % 3 == 0 && e.level().random.nextFloat() < 0.6F)
            e.level().addParticle(net.minecraft.core.particles.ParticleTypes.SOUL, e.getRandomX(0.5), e.getRandomY(), e.getRandomZ(0.5), 0, 0.02, 0);
    }

    private static class Details extends RenderLayer<SoulEntity, PlayerModel<SoulEntity>> {
        Details(RenderLayerParent<SoulEntity, PlayerModel<SoulEntity>> parent) { super(parent); }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, SoulEntity e, float limbSwing, float limbSwingAmount,
                           float partialTick, float age, float yaw, float pitch) {
            CharacterType t = e.character();
            DetailModel m = model(t);
            if (m == null) return;
            PlayerModel<SoulEntity> p = getParentModel();
            m.head.copyFrom(p.head); m.body.copyFrom(p.body);
            m.rightArm.copyFrom(p.rightArm); m.leftArm.copyFrom(p.leftArm);
            m.rightLeg.copyFrom(p.rightLeg); m.leftLeg.copyFrom(p.leftLeg);
            com.xenowarriors.client.LimbBend.copy(p.rightArm, m.rightArm);
            com.xenowarriors.client.LimbBend.copy(p.leftArm, m.leftArm);
            com.xenowarriors.client.LimbBend.copy(p.rightLeg, m.rightLeg);
            com.xenowarriors.client.LimbBend.copy(p.leftLeg, m.leftLeg);
            int overlay = LivingEntityRenderer.getOverlayCoords(e, PALE);
            m.root.render(pose, buffers.getBuffer(RenderType.entityTranslucent(t.getDetailTexture())), light, overlay, 0.55F, 0.8F, 1F, 0.55F);
            if (m.faceRoot != null) {
                m.faceHead.copyFrom(p.head);
                m.faceRoot.render(pose, buffers.getBuffer(RenderType.entityTranslucent(com.xenowarriors.client.render.CharacterDetailLayer.baseFace(t))), light, overlay, 0.55F, 0.8F, 1F, 0.55F);
            }
        }
    }
}
