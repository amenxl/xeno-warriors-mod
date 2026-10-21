package com.xenowarriors.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import com.xenowarriors.client.ClientSkillState;
import com.xenowarriors.client.FlightTracker;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

import java.util.EnumMap;
import java.util.Map;

/**
 * Draws each character's 3D details on top of the player model. The detail bones copy the
 * player model's pose every frame, so hair follows the head, sleeves follow the arms, etc.
 * Rhalix's wings come out when he's in the air (flying, gliding, falling) or casting Kishi,
 * like in the show, and are always shown on the selection screen.
 */
public class CharacterDetailLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private final Map<CharacterType, DetailModel> models = new EnumMap<>(CharacterType.class);

    public CharacterDetailLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
        for (CharacterType type : CharacterType.PLAYABLE) {
            DetailModel model = DetailModel.load(type);
            if (model != null) models.put(type, model);
        }
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;
        CharacterType type = ClientCharacterData.get(player);
        DetailModel model = models.get(type);
        if (model == null) return;

        PlayerModel<AbstractClientPlayer> parent = getParentModel();
        model.head.copyFrom(parent.head);
        model.body.copyFrom(parent.body);
        model.rightArm.copyFrom(parent.rightArm);
        model.leftArm.copyFrom(parent.leftArm);
        model.rightLeg.copyFrom(parent.rightLeg);
        model.leftLeg.copyFrom(parent.leftLeg);

        if (model.hasWings()) {
            boolean preview = ClientCharacterData.isPreviewing();
            boolean flying = FlightTracker.isFlying(player);
            boolean falling = !player.onGround() && !player.isInWater() && player.fallDistance > 1.5F;
            boolean kishi = ClientSkillState.isCastingKishi(player.getUUID());
            float spread = com.xenowarriors.client.SkillAnimations.wingSpread(player, ageInTicks);
            model.setupWings(preview || flying || falling || kishi || spread > 0F, flying || falling, ageInTicks, spread);
        }

        // hood up (SR hoodie): the hair tucks under it, only the front bangs show
        if (model.hair != null) {
            model.hair.visible = !(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getItem()
                    instanceof com.xenowarriors.item.SrHoodieItem);
        }

        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(type.getDetailTexture()));
        model.root.render(poseStack, buffer, packedLight, overlay);

        if (model.faceRoot != null) {
            model.faceHead.copyFrom(parent.head);
            var faceTexture = type.getFaceTexture();
            if (player.isSleeping() && type == CharacterType.MIKIKO) {
                // breathe in (mouth closed) / snore out (mouth open, snot bubble) in time with the z's
                boolean snore = (player.tickCount % 40) < 22;
                faceTexture = com.xenowarriors.XenoWarriors.id("textures/entity/character/mikiko_face_"
                        + (snore ? "snore" : "sleep") + ".png");
            }
            VertexConsumer faceBuffer = buffers.getBuffer(RenderType.entityCutoutNoCull(faceTexture));
            model.faceRoot.render(poseStack, faceBuffer, packedLight, overlay);
        }
    }
}
