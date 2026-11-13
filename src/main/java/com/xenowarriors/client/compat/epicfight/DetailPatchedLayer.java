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
    private static final Map<DetailModel, SkinnedMesh[]> BAKED_HOOD = new IdentityHashMap<>();   // the same, rain hood up
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
        boolean hood = live.hoodUp != null && com.xenowarriors.client.mikiko.HoodClient.up(player);
        boolean legacy = legacyWings;
        SkinnedMesh[] mesh = (hood ? BAKED_HOOD : legacy ? BAKED_LEGACY : BAKED).computeIfAbsent(live, m -> bake(freshCopy(player, m), hood, legacy));
        DetailModel model = live;
        if (mesh == null) {
            FAILED.add(live);
            return;
        }
        int overlay = OverlayTexture.NO_OVERLAY;
        if (mesh[0] != null) {
            mesh[0].draw(pose, buffers, RenderType.entityCutoutNoCull(com.xenowarriors.client.cel.Cel.tex(CharacterDetailLayer.detailTexture(player))), light,
                    1F, 1F, 1F, 1F, overlay, patch.getArmature(), poses);
        }
        if (mesh[1] != null) {
            // the same face as without Epic Fight (and as the HUD portrait): the face, its battle damage, the talking mouth
            CharacterDetailLayer.FaceFrame ff = CharacterDetailLayer.faceFrame(player, FACE);
            if (CharacterDetailLayer.recordFaces) CharacterDetailLayer.RENDERED.put(player.getId(), ff.face + "|" + ff.damage + "|" + ff.mouth);
            mesh[1].draw(pose, buffers, RenderType.entityCutoutNoCull(ff.face), light,
                    1F, 1F, 1F, 1F, overlay, patch.getArmature(), poses);
            if (ff.damage != null) mesh[1].draw(pose, buffers, RenderType.entityTranslucent(ff.damage), light,
                    1F, 1F, 1F, 1F, overlay, patch.getArmature(), poses);
            if (ff.mouth != null) mesh[1].draw(pose, buffers, RenderType.entityCutoutNoCull(ff.mouth), light,
                    1F, 1F, 1F, 1F, overlay, patch.getArmature(), poses);
        }
        if (com.xenowarriors.client.cel.Cel.on() && !firstPerson(player)) {         // cel-shading: the thick ink outline, as without Epic Fight
            float ink = com.xenowarriors.client.cel.CelOutline.INK;
            var rt = com.xenowarriors.client.cel.CelRenderTypes.OUTLINE;
            SkinnedMesh body = bodyOutline(slim(player));
            if (body != null) { body.draw(pose, buffers, rt, light, ink, ink, ink, 1F, overlay, patch.getArmature(), poses); outlineDraws++; }
            Map<DetailModel, SkinnedMesh> outs = hood ? OUT_HOOD : OUT;
            if (!outs.containsKey(live)) {
                DetailModel o = freshCopy(player, live).outlineCopy();
                SkinnedMesh[] b = o == null ? null : bake(o, hood, false);
                outs.put(live, b == null ? null : b[0]);
            }
            SkinnedMesh det = outs.get(live);
            if (det != null) det.draw(pose, buffers, rt, light, ink, ink, ink, 1F, overlay, patch.getArmature(), poses);
        }
        if (live.hasWings() && !legacy) wings(patch, player, live, pose, buffers, light, overlay, poses, partial);
    }

    private static final Map<DetailModel, SkinnedMesh> OUT = new IdentityHashMap<>(), OUT_HOOD = new IdentityHashMap<>();
    private static final SkinnedMesh[] BODY_OUT = new SkinnedMesh[2];
    private static final boolean[] BODY_TRIED = new boolean[2];
    /** (tests) body outlines drawn on Epic Fight's skeleton. */
    public static int outlineDraws;

    private static boolean firstPerson(net.minecraft.world.entity.LivingEntity e) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        return e == mc.player && mc.options.getCameraType().isFirstPerson() && com.xenowarriors.client.FirstPersonHead.hidden();
    }

    /** A girl's slimmer body (her renderer's model has the chest piece). */
    static boolean slim(net.minecraft.world.entity.LivingEntity e) {
        var r = net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(e);
        return r instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?> lr && lr.getModel() instanceof HumanoidModel<?> h
                && (h.body.hasChild(com.xenowarriors.client.FemaleBody.CHEST) || com.xenowarriors.client.cel.CelOutline.slimArms(h));   // (or a slim skin's arms)
    }

    /** The body's ink outline (an inflated player body), baked once for Epic Fight's skeleton. */
    static SkinnedMesh bodyOutline(boolean slim) {
        int i = slim ? 1 : 0;
        if (!BODY_TRIED[i]) {
            BODY_TRIED[i] = true;
            try {
                var o = com.xenowarriors.client.cel.CelOutline.restBody(slim);
                SkinnedMesh m = HumanoidModelBaker.VANILLA_TRANSFORMER.transformArmorModel(
                        humanoid(o.head, o.body, o.rightArm, o.leftArm, o.rightLeg, o.leftLeg));
                if (m != null) m.initialize();
                BODY_OUT[i] = m;
            } catch (Throwable t) {
                XenoWarriors.LOGGER.error("Couldn't prepare the cel outline for Epic Fight", t);
            }
        }
        return BODY_OUT[i];
    }

    private static final CharacterDetailLayer.FaceFrame FACE = new CharacterDetailLayer.FaceFrame();

    /** (UI test, before/after) the old way: wings baked into the mesh in one pose. */
    public static boolean legacyWings;
    private static final Map<DetailModel, SkinnedMesh[]> BAKED_LEGACY = new IdentityHashMap<>();

    /** (tests) wing draws done on Epic Fight's skeleton. */
    public static int wingDraws;

    /**
     * Rhalix's wings aren't baked into the mesh (baked, they were frozen in one spread pose, always out, and pulled
     * about by the arm bones): they're drawn live, posed exactly as without Epic Fight (out/away, beating, folded),
     * fixed to Epic Fight's animated chest bone - so they stay on his back through every attack, roll and step.
     */
    private static void wings(AbstractClientPlayerPatch<AbstractClientPlayer> patch, AbstractClientPlayer player, DetailModel live, PoseStack pose,
                              MultiBufferSource buffers, int light, int overlay, OpenMatrix4f[] poses, float partial) {
        float age = player.tickCount + partial;
        boolean preview = com.xenowarriors.client.ClientCharacterData.isPreviewing();
        boolean flying = com.xenowarriors.client.FlightTracker.isFlying(player);
        boolean falling = !player.onGround() && !player.isInWater() && player.fallDistance > 1.5F;
        boolean kishi = com.xenowarriors.client.ClientSkillState.isCastingKishi(player.getUUID());
        float spread = Math.max(com.xenowarriors.client.SkillAnimations.wingSpread(player, age), com.xenowarriors.client.KarmaFocusClient.wingSpread(player));
        com.xenowarriors.client.RhalixWingsClient.onEfSkeleton = true;
        try { com.xenowarriors.client.RhalixWingsClient.pose(live, player, preview, flying, falling, kishi, spread, age); }
        finally { com.xenowarriors.client.RhalixWingsClient.onEfSkeleton = false; }
        if (!live.rightWing.visible && !live.leftWing.visible) return;
        int chest = patch.getArmature() instanceof yesman.epicfight.model.armature.HumanoidArmature h ? h.chest.getId() : 8;
        if (chest < 0 || chest >= poses.length) return;
        // vanilla model space (y down, x mirrored, 1/16 block units inside ModelPart) onto the chest bone: the same
        // mapping Epic Fight uses for the elytra, at the player model's scale
        OpenMatrix4f m = new OpenMatrix4f().scale(new yesman.epicfight.api.utils.math.Vec3f(-WING_SCALE, -WING_SCALE, WING_SCALE))
                .translate(new yesman.epicfight.api.utils.math.Vec3f(0F, -0.5F, -0.1F)).mulFront(poses[chest]);
        pose.pushPose();
        yesman.epicfight.api.utils.math.MathUtils.mulStack(pose, m);
        var vc = buffers.getBuffer(RenderType.entityCutoutNoCull(com.xenowarriors.client.cel.Cel.tex(CharacterDetailLayer.detailTexture(player))));
        live.rightWing.render(pose, vc, light, overlay);
        live.leftWing.render(pose, vc, light, overlay);
        pose.popPose();
        wingDraws++;
    }

    private static final float WING_SCALE = 0.9375F;

    private static DetailModel freshCopy(AbstractClientPlayer player, DetailModel live) {
        // limbs left whole: Epic Fight bends knees and elbows itself, and cuts our pre-split halves and joint
        // fillers into stretched, striped pieces (crouching with an axe...)
        DetailModel copy = DetailModel.loadWhole(CharacterDetailLayer.detailModelPath(player));
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

    /** Bakes any detail model (the townsfolk's too) for Epic Fight: {details, face}; null if it failed. */
    static SkinnedMesh[] bakeModel(DetailModel model) {
        return bake(model, false, false);
    }

    /** Baked with its left arm left off (Mikiko wears nothing on his left arm, so his shoulder plate shows). */
    static SkinnedMesh[] bakeModelHiding(DetailModel model, boolean noLeftArm) {
        if (!noLeftArm) return bake(model, false, false);
        try {
            model.root.getAllParts().forEach(p -> { p.resetPose(); p.visible = true; p.yScale = 1F; });
            SkinnedMesh m = HumanoidModelBaker.VANILLA_TRANSFORMER.transformArmorModel(
                    humanoid(model.head, model.body, model.rightArm, empty(), model.rightLeg, model.leftLeg));
            if (m != null) m.initialize();
            return new SkinnedMesh[]{m, null};
        } catch (Throwable t) {
            XenoWarriors.LOGGER.error("Couldn't prepare Xeno Warriors armor for Epic Fight", t);
            return null;
        }
    }

    static SkinnedMesh[] bake(DetailModel model, boolean hood, boolean legacy) {
        try {
            // rest pose: no copied player pose, no blown hair or swinging coat
            model.root.getAllParts().forEach(p -> {
                p.resetPose();
                p.visible = true;
                p.yScale = 1F;
            });
            // the rain hood: a second copy is baked with it up (hood_down hidden, hair that would poke through tucked in)
            if (model.rightWing != null) model.rightWing.visible = legacy;     // (wings: drawn live on the chest bone, see wings())
            if (model.leftWing != null) model.leftWing.visible = legacy;
            if (model.hoodUp != null) model.hoodUp.visible = hood;
            if (model.hoodDown != null) model.hoodDown.visible = !hood;
            if (hood) for (ModelPart strand : model.hoodPokers) strand.visible = false;
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
