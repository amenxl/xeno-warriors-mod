package com.xenowarriors.client.compat.epicfight;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.client.render.CharacterDetailLayer;
import com.xenowarriors.client.render.DetailModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.client.model.transformer.HumanoidModelBaker;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.client.renderer.patched.layer.PatchedLayer;
import yesman.epicfight.client.world.capabilites.entitypatch.player.AbstractClientPlayerPatch;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws a character's 3D details (hair, clothes, wings) and face on Epic Fight's skinned player. Each detail model is
 * baked once into an Epic Fight mesh with Epic Fight's own humanoid baker, so sleeves and coat bend at the elbows and
 * knees with its animations.
 */
public class DetailPatchedLayer extends PatchedLayer<AbstractClientPlayer, AbstractClientPlayerPatch<AbstractClientPlayer>,
        PlayerModel<AbstractClientPlayer>, RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>> {
    private static final Map<DetailModel, SkinnedMesh[]> BAKED = new IdentityHashMap<>();   // {details, face}
    private static final java.util.Set<DetailModel> FAILED = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

    @Override
    protected void renderLayer(AbstractClientPlayerPatch<AbstractClientPlayer> patch, AbstractClientPlayer player,
                               RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> layer, PoseStack pose,
                               MultiBufferSource buffers, int light, OpenMatrix4f[] poses, float bob, float yRot, float xRot, float partial) {
        if (player.isInvisible()) return;
        DetailModel live = CharacterDetailLayer.detailModel(player);
        if (live == null || FAILED.contains(live)) return;
        // baked from its own fresh copy of the model, so Epic Fight's baker never touches (and can't disturb) the
        // model the normal renderer animates - wings, coat, hair
        SkinnedMesh[] mesh = BAKED.computeIfAbsent(live, m -> bake(freshCopy(player, m)));
        DetailModel model = live;
        if (mesh == null) {
            FAILED.add(live);
            return;
        }
        int overlay = OverlayTexture.NO_OVERLAY;
        if (mesh[0] != null) {
            mesh[0].draw(pose, buffers, RenderType.entityCutoutNoCull(CharacterDetailLayer.detailTexture(player)), light,
                    1F, 1F, 1F, 1F, overlay, patch.getArmature(), poses);
        }
        if (mesh[1] != null) {
            mesh[1].draw(pose, buffers, RenderType.entityCutoutNoCull(CharacterDetailLayer.faceTexture(player)), light,
                    1F, 1F, 1F, 1F, overlay, patch.getArmature(), poses);
        }
    }

    private static DetailModel freshCopy(AbstractClientPlayer player, DetailModel live) {
        var type = com.xenowarriors.client.ClientCharacterData.get(player);
        boolean king = type == com.xenowarriors.character.CharacterType.MIKIKO && com.xenowarriors.client.mikiko.FoxKingClient.active(player);
        DetailModel copy = king ? DetailModel.loadPath("/assets/" + XenoWarriors.MOD_ID + "/models/detail/mikiko_foxking.json")
                : DetailModel.load(type);
        return copy != null ? copy : live;
    }

    private static ModelPart empty() {
        return new ModelPart(List.of(), Map.of());
    }

    private static HumanoidModel<AbstractClientPlayer> humanoid(ModelPart head, ModelPart body, ModelPart rightArm,
                                                               ModelPart leftArm, ModelPart rightLeg, ModelPart leftLeg) {
        Map<String, ModelPart> parts = new HashMap<>();
        parts.put("head", head);
        parts.put("hat", empty());
        parts.put("body", body);
        parts.put("right_arm", rightArm);
        parts.put("left_arm", leftArm);
        parts.put("right_leg", rightLeg);
        parts.put("left_leg", leftLeg);
        return new HumanoidModel<>(new ModelPart(List.of(), parts));
    }

    private static SkinnedMesh[] bake(DetailModel model) {
        try {
            // rest pose: no copied player pose, no blown hair or swinging coat
            model.root.getAllParts().forEach(p -> {
                p.resetPose();
                p.visible = true;
                p.yScale = 1F;
            });
            SkinnedMesh details = HumanoidModelBaker.VANILLA_TRANSFORMER.transformArmorModel(
                    humanoid(model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg));
            if (details != null) details.initialize();
            SkinnedMesh face = null;
            if (model.faceHead != null) {
                model.faceHead.resetPose();
                face = HumanoidModelBaker.VANILLA_TRANSFORMER.transformArmorModel(
                        humanoid(model.faceHead, empty(), empty(), empty(), empty(), empty()));
                if (face != null) face.initialize();
            }
            return new SkinnedMesh[]{details, face};
        } catch (Throwable t) {
            XenoWarriors.LOGGER.error("Couldn't prepare Xeno Warriors details for Epic Fight", t);
            FAILED.add(model);    // (the fresh copy; the live model just won't get Epic Fight details)
            return null;
        }
    }
}
