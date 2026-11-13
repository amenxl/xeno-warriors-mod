package com.xenowarriors.client.vr;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.gov.UltimaPrototypeEntity;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.render.DetailModel;
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

/** The Ultima Prototype: Ultima's shape in steel plates and gunmetal, red eyes (an artificial copy). */
public class UltimaPrototypeRenderer extends HumanoidMobRenderer<UltimaPrototypeEntity, PlayerModel<UltimaPrototypeEntity>> {
    private static final ResourceLocation SKIN = XenoWarriors.id("textures/entity/character/ultima_prototype.png"),
            DETAILS = XenoWarriors.id("textures/entity/character/ultima_prototype_details.png"),
            FACE = XenoWarriors.id("textures/entity/character/ultima_prototype_face.png");
    private static final Map<CharacterType, DetailModel> MODELS = new EnumMap<>(CharacterType.class);

    public UltimaPrototypeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        addLayer(new Details(this));
        addLayer(new net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer<>(this,
                new net.minecraft.client.model.HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new net.minecraft.client.model.HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), ctx.getModelManager()));
    }

    private static DetailModel model(CharacterType t) {
        DetailModel m = MODELS.get(t);
        if (m == null && !MODELS.containsKey(t)) {      // (a missing model is remembered too - not looked up again every frame)
            m = DetailModel.loadPath("/assets/" + XenoWarriors.MOD_ID + "/models/detail/" + t.getId() + ".json");
            MODELS.put(t, m);
        }
        return m;
    }

    @Override
    public ResourceLocation getTextureLocation(UltimaPrototypeEntity e) { return SKIN; }

    private static class Details extends RenderLayer<UltimaPrototypeEntity, PlayerModel<UltimaPrototypeEntity>> {
        Details(RenderLayerParent<UltimaPrototypeEntity, PlayerModel<UltimaPrototypeEntity>> parent) { super(parent); }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, UltimaPrototypeEntity e, float limbSwing, float limbSwingAmount,
                           float partialTick, float age, float yaw, float pitch) {
            if (e.isInvisible()) return;
            CharacterType t = CharacterType.ULTIMA;
            DetailModel m = model(t);
            if (m == null) return;
            PlayerModel<UltimaPrototypeEntity> p = getParentModel();
            m.head.copyFrom(p.head); m.body.copyFrom(p.body);
            m.rightArm.copyFrom(p.rightArm); m.leftArm.copyFrom(p.leftArm);
            m.rightLeg.copyFrom(p.rightLeg); m.leftLeg.copyFrom(p.leftLeg);
            com.xenowarriors.client.LimbBend.copy(p.rightArm, m.rightArm);
            com.xenowarriors.client.LimbBend.copy(p.leftArm, m.leftArm);
            com.xenowarriors.client.LimbBend.copy(p.rightLeg, m.rightLeg);
            com.xenowarriors.client.LimbBend.copy(p.leftLeg, m.leftLeg);
            int overlay = LivingEntityRenderer.getOverlayCoords(e, 0F);
            m.root.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(DETAILS)), light, overlay);
            if (com.xenowarriors.client.cel.Cel.outline()) {
                com.xenowarriors.client.cel.CelOutline.body(p, pose, buffers, light, overlay);
                com.xenowarriors.client.cel.CelOutline.details(m, pose, buffers, light, overlay);
            }
            if (m.faceRoot != null) {
                m.faceHead.copyFrom(p.head);
                m.faceRoot.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(FACE)), light, overlay);
            }
        }
    }
}
