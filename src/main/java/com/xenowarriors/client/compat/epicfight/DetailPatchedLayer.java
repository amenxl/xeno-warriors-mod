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
    /**
     * The ink outline shell on Epic Fight's body: off. Baked onto Epic Fight's skeleton it came out as black slabs
     * moving beside the arms (the user's videos - a dark "shadow" doing the moves). Cel-shaded textures still apply;
     * the outline is drawn as before whenever our own renderer draws them (out of battle mode).
     */
    public static boolean OUTLINE_ON_EF = false;
    private static final Map<DetailModel, SkinnedMesh[]> BAKED = new IdentityHashMap<>();   // {details, face}
    private static final Map<DetailModel, SkinnedMesh[]> BAKED_HOOD = new IdentityHashMap<>();   // the same, rain hood up
    private static final java.util.Set<DetailModel> FAILED = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

    @Override
    protected void renderLayer(AbstractClientPlayerPatch<AbstractClientPlayer> patch, AbstractClientPlayer player,
                               RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> layer, PoseStack pose,
                               MultiBufferSource buffers, int light, OpenMatrix4f[] poses, float bob, float yRot, float xRot, float partial) {
        if (player.isInvisible()) return;
        tail(patch, player, pose, buffers, light, poses, partial);
        DetailModel live = CharacterDetailLayer.detailModel(player);
        if (live == null || FAILED.contains(live)) return;
        // baked from its own fresh copy of the model, so Epic Fight's baker never touches (and can't disturb) the
        // model the normal renderer animates - wings, coat, hair
        boolean hood = live.hoodUp != null && com.xenowarriors.client.mikiko.HoodClient.up(player);
        boolean legacy = legacyWings;
        SkinnedMesh[] mesh = (hood ? BAKED_HOOD : legacy ? BAKED_LEGACY : BAKED).computeIfAbsent(live,
                m -> bake(freshCopy(player, m), hood, legacy, RIGID && !legacy));
        DetailModel model = live;
        if (mesh == null) {
            FAILED.add(live);
            return;
        }
        int overlay = OverlayTexture.NO_OVERLAY;
        RenderType detailType = RenderType.entityCutoutNoCull(com.xenowarriors.client.cel.Cel.tex(CharacterDetailLayer.detailTexture(player)));
        Rig rig = RIGID && !legacy ? RIGS.computeIfAbsent(live, m -> Rig.of(player)) : null;
        if (mesh[0] != null) mesh[0].draw(pose, buffers, detailType, light, 1F, 1F, 1F, 1F, overlay, patch.getArmature(), poses);
        if (rig != null) {
            if (rig.draw(patch, pose, buffers.getBuffer(detailType), light, overlay, poses, hood)) { rigDraws++; lastRig = rig.info; }
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
        if (com.xenowarriors.client.cel.Cel.outline() && OUTLINE_ON_EF && !firstPerson(player)) {         // cel-shading: the thick ink outline, as without Epic Fight
            float ink = com.xenowarriors.client.cel.CelOutline.INK;
            var rt = com.xenowarriors.client.cel.CelRenderTypes.OUTLINE;
            SkinnedMesh body = bodyOutline(slim(player));
            if (body != null) { body.draw(pose, buffers, rt, light, ink, ink, ink, 1F, overlay, patch.getArmature(), poses); outlineDraws++; }
            Map<DetailModel, SkinnedMesh> outs = hood ? OUT_HOOD : OUT;
            if (!outs.containsKey(live)) {
                DetailModel o = freshCopy(player, live).outlineCopy();
                SkinnedMesh[] b = o == null ? null : bake(o, hood, false, false);
                outs.put(live, b == null ? null : b[0]);
            }
            SkinnedMesh det = outs.get(live);
            if (det != null) det.draw(pose, buffers, rt, light, ink, ink, ink, 1F, overlay, patch.getArmature(), poses);
        }
        if (live.hasWings() && !legacy) wings(patch, player, live, pose, buffers, light, overlay, poses, partial);
    }

    /**
     * Clothes on the body, arms and legs drawn as rigid pieces on Epic Fight's bones - our own split model (each arm
     * and leg cut at the elbow / knee with its joint fillers, just as our renderer draws them), each piece moved by its
     * bone's skinning transform. Baking them into a skinned mesh with Epic Fight's armor baker (the old way) cut and
     * stretched coats and sleeves: Yuri's and Technica's lab coats came out as boxes, Kyrie's sleeves floated, Keita's
     * hoodie puffed up (the user's videos and picture). Off = the old way (tests compare).
     */
    public static boolean RIGID = true;
    /** (tests) rig draws. */
    public static int rigDraws;
    private static final Map<DetailModel, Rig> RIGS = new IdentityHashMap<>();

    static final class Rig {
        final DetailModel m;
        final ModelPart bodyUpper, bodyLower;
        final boolean head, noLeftArm;
        String info = "";
        private static java.lang.reflect.Field cubesField;

        Rig(DetailModel m, boolean head, boolean noLeftArm) {
            this.m = m;
            this.head = head;
            this.noLeftArm = noLeftArm;
            m.root.getAllParts().forEach(p -> { p.resetPose(); p.visible = true; p.yScale = 1F; });
            if (m.rightWing != null) m.rightWing.visible = false;           // (drawn live, see wings())
            if (m.leftWing != null) m.leftWing.visible = false;
            // the body in two: the upper part rides the chest bone, below the waist the torso (hip) bone
            List<ModelPart.Cube> up = new java.util.ArrayList<>(), low = new java.util.ArrayList<>();
            for (ModelPart.Cube c : cubes(m.body)) ((c.minY + c.maxY) / 2F < WAIST ? up : low).add(c);
            // the coat tails hang from the waist (they swing with the legs, see draw), everything else rides the chest
            Map<String, ModelPart> kids = new HashMap<>(), lowKids = new HashMap<>();
            for (String k : childNames(m.body)) (k.startsWith("coat_") ? lowKids : kids).put(k, m.body.getChild(k));
            bodyUpper = new ModelPart(up, kids);
            bodyLower = new ModelPart(low, lowKids);
            info = "body cubes " + cubes(m.body).size() + " -> upper " + up.size() + " lower " + low.size() + " kids " + kids.keySet() + " low kids " + lowKids.keySet()
                    + " lower y " + low.stream().map(c -> String.format(java.util.Locale.ROOT, "%.1f-%.1f", c.minY, c.maxY)).limit(6).toList()
                    + " arms " + cubes(m.rightArm).size() + (m.rightArm.hasChild(com.xenowarriors.client.LimbBend.LOWER) ? "+" + cubes(m.rightArm.getChild(com.xenowarriors.client.LimbBend.LOWER)).size() : "")
                    + " legs " + cubes(m.rightLeg).size() + (m.rightLeg.hasChild(com.xenowarriors.client.LimbBend.LOWER) ? "+" + cubes(m.rightLeg.getChild(com.xenowarriors.client.LimbBend.LOWER)).size() : "");
            lastRig = info;
            bodyUpper.loadPose(m.body.getInitialPose()); bodyUpper.setInitialPose(m.body.getInitialPose());
            bodyLower.loadPose(m.body.getInitialPose()); bodyLower.setInitialPose(m.body.getInitialPose());
        }

        static Rig of(AbstractClientPlayer player) {
            try {
                DetailModel split = DetailModel.loadPath(CharacterDetailLayer.detailModelPath(player));
                return split == null ? null : new Rig(split, false, false);
            } catch (Throwable t) {
                XenoWarriors.LOGGER.error("Couldn't prepare Xeno Warriors clothes for Epic Fight", t);
                return null;
            }
        }

        @SuppressWarnings("unchecked")
        private static List<ModelPart.Cube> cubes(ModelPart p) {
            try {
                if (cubesField == null) cubesField = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(ModelPart.class, "f_104212_");
                return (List<ModelPart.Cube>) cubesField.get(p);
            } catch (Throwable t) { return List.of(); }
        }

        private static java.util.Set<String> childNames(ModelPart p) {
            try {
                var f = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(ModelPart.class, "f_104213_");
                return ((Map<String, ModelPart>) f.get(p)).keySet();
            } catch (Throwable t) { return java.util.Set.of(); }
        }

        boolean draw(AbstractClientPlayerPatch<AbstractClientPlayer> patch, PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer vc, int light, int overlay,
                     OpenMatrix4f[] poses, boolean hood) {
            return draw(patch.getArmature(), pose, vc, light, overlay, poses, hood, 1F, 1F, 1F);
        }

        boolean draw(yesman.epicfight.api.model.Armature armature, PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer vc, int light, int overlay,
                     OpenMatrix4f[] poses, boolean hood, float r, float g, float b) {
            if (!(armature instanceof yesman.epicfight.model.armature.HumanoidArmature h)) return false;
            tint[0] = r; tint[1] = g; tint[2] = b;
            if (m.hoodDown != null) m.hoodDown.visible = !hood;
            if (m.coatBack != null) {
                // Rhalix's coat tails: swung by Epic Fight's thighs (forward / back, out to the side), as our renderer does
                float[] lr = legAngles(h.torso, h.thighR, poses), ll = legAngles(h.torso, h.thighL, poses);
                if (lr != null && ll != null) m.animateCoat(lr[0], ll[0], lr[1], -ll[1], 0F, 0F, 0F, false);
            }
            if (head) piece(h.head, m.head, null, pose, vc, light, overlay, poses);
            piece(h.chest, bodyUpper, null, pose, vc, light, overlay, poses);
            piece(h.torso, bodyLower, null, pose, vc, light, overlay, poses);
            limb(h.armR, h.handR, m.rightArm, pose, vc, light, overlay, poses);
            if (!noLeftArm) limb(h.armL, h.handL, m.leftArm, pose, vc, light, overlay, poses);
            limb(h.thighR, h.legR, m.rightLeg, pose, vc, light, overlay, poses);
            limb(h.thighL, h.legL, m.leftLeg, pose, vc, light, overlay, poses);
            return true;
        }

        private static final float[] tint = {1F, 1F, 1F};

        /** A thigh's swing relative to the hips, as vanilla leg angles: {xRot (negative = forward), zRot (positive = toward the body's right)}. */
        private static float[] legAngles(yesman.epicfight.api.animation.Joint torso, yesman.epicfight.api.animation.Joint thigh, OpenMatrix4f[] poses) {
            int t = torso.getId(), k = thigh.getId();
            if (t < 0 || k < 0 || t >= poses.length || k >= poses.length) return null;
            org.joml.Matrix4f a = OpenMatrix4f.exportToMojangMatrix(new OpenMatrix4f().load(poses[t]).mulBack(torso.getToOrigin()).mulBack(V2E));
            org.joml.Matrix4f b = OpenMatrix4f.exportToMojangMatrix(new OpenMatrix4f().load(poses[k]).mulBack(thigh.getToOrigin()).mulBack(V2E));
            org.joml.Vector3f d = a.invert().mul(b).transformDirection(new org.joml.Vector3f(0F, 1F, 0F));
            return new float[]{(float) Math.atan2(d.z, d.y), (float) Math.atan2(-d.x, d.y)};
        }

        private static void limb(yesman.epicfight.api.animation.Joint upper, yesman.epicfight.api.animation.Joint lower, ModelPart part,
                                 PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer vc, int light, int overlay, OpenMatrix4f[] poses) {
            ModelPart low = part.hasChild(com.xenowarriors.client.LimbBend.LOWER) ? part.getChild(com.xenowarriors.client.LimbBend.LOWER) : null;
            if (low != null) low.visible = false;
            piece(upper, part, null, pose, vc, light, overlay, poses);
            if (low != null) {
                low.visible = true;
                piece(lower, low, part, pose, vc, light, overlay, poses);
            }
        }

        /** One rigid piece: vanilla model space at rest -> Epic Fight's bind space -> its bone's pose. */
        private static void piece(yesman.epicfight.api.animation.Joint joint, ModelPart part, ModelPart parent, PoseStack pose,
                                  com.mojang.blaze3d.vertex.VertexConsumer vc, int light, int overlay, OpenMatrix4f[] poses) {
            int id = joint.getId();
            if (id < 0 || id >= poses.length) return;
            OpenMatrix4f m = new OpenMatrix4f().load(poses[id]).mulBack(joint.getToOrigin()).mulBack(V2E);
            pose.pushPose();
            yesman.epicfight.api.utils.math.MathUtils.mulStack(pose, m);
            if (parent != null) pose.translate(parent.x / 16F, parent.y / 16F, parent.z / 16F);     // (a lower half: its parent's pivot, at rest)
            part.render(pose, vc, light, overlay, tint[0], tint[1], tint[2], 1F);
            pose.popPose();
        }
    }

    /** Where the body is cut between the chest and hip bones (model y, px below the neck). */
    static float WAIST = 7F;
    /** Vanilla model space (blocks: y down from the neck, x the body's left) -> Epic Fight's bind space (y up from the feet). */
    static final OpenMatrix4f V2E = new OpenMatrix4f().translate(new yesman.epicfight.api.utils.math.Vec3f(0F, 1.5F, 0F))
            .scale(new yesman.epicfight.api.utils.math.Vec3f(-1F, -1F, 1F));

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
        // the chest bone's skinning transform on the vanilla body's space - the same as the clothes (see Rig)
        OpenMatrix4f m = RIGID && patch.getArmature() instanceof yesman.epicfight.model.armature.HumanoidArmature ha
                ? new OpenMatrix4f().load(poses[chest]).mulBack(ha.chest.getToOrigin()).mulBack(V2E)
                : new OpenMatrix4f().scale(new yesman.epicfight.api.utils.math.Vec3f(-WING_SCALE, -WING_SCALE, WING_SCALE))
                .translate(new yesman.epicfight.api.utils.math.Vec3f(0F, -0.5F, -0.1F)).mulFront(poses[chest]);
        pose.pushPose();
        yesman.epicfight.api.utils.math.MathUtils.mulStack(pose, m);
        var vc = buffers.getBuffer(RenderType.entityCutoutNoCull(com.xenowarriors.client.cel.Cel.tex(CharacterDetailLayer.detailTexture(player))));
        live.rightWing.render(pose, vc, light, overlay);
        live.leftWing.render(pose, vc, light, overlay);
        lastWings = wingInfo(live.rightWing) + " | " + wingInfo(live.leftWing);
        pose.popPose();
        wingDraws++;
    }

    private static final float WING_SCALE = 0.9375F;
    /** (tests) the wings' pose when last drawn on Epic Fight's skeleton. */
    public static String lastWings = "";
    /** (tests) what the last rig was built from. */
    public static String lastRig = "";

    private static String wingInfo(ModelPart w) {
        return String.format(java.util.Locale.ROOT, "vis=%s pos=(%.1f,%.1f,%.1f) rot=(%.2f,%.2f,%.2f) scale=(%.2f,%.2f,%.2f) kids=%d",
                w.visible, w.x, w.y, w.z, w.xRot, w.yRot, w.zRot, w.xScale, w.yScale, w.zScale, w.getAllParts().count());
    }

    /** (tests) fox tail draws on Epic Fight's skeleton. */
    public static int tailDraws;

    /**
     * Mikiko's fox tail (DragonMineZ Saiyan, the Origins outfits, Fox Kin): on Epic Fight's torso (hip) bone, in the
     * vanilla body's space - the chest mapping the wings use, moved down by the chest bone's height above the torso.
     */
    private static void tail(AbstractClientPlayerPatch<AbstractClientPlayer> patch, AbstractClientPlayer player, PoseStack pose,
                             MultiBufferSource buffers, int light, OpenMatrix4f[] poses, float partial) {
        if (!(patch.getArmature() instanceof yesman.epicfight.model.armature.HumanoidArmature h)) return;
        int torso = h.torso.getId();
        if (torso < 0 || torso >= poses.length) return;
        OpenMatrix4f m = new OpenMatrix4f().load(poses[torso]).mulBack(h.torso.getToOrigin()).mulBack(V2E);
        pose.pushPose();
        yesman.epicfight.api.utils.math.MathUtils.mulStack(pose, m);
        if (com.xenowarriors.client.compat.FoxTailLayer.renderOnBody(pose, buffers, light, player, partial)) tailDraws++;
        pose.popPose();
    }

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
        return bake(model, false, false, false);
    }

    /** Baked with its left arm left off (Mikiko wears nothing on his left arm, so his shoulder plate shows). */
    static SkinnedMesh[] bakeModelHiding(DetailModel model, boolean noLeftArm) {
        if (!noLeftArm) return bake(model, false, false, false);
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

    static SkinnedMesh[] bake(DetailModel model, boolean hood, boolean legacy, boolean headOnly) {
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
            SkinnedMesh details = HumanoidModelBaker.VANILLA_TRANSFORMER.transformArmorModel(headOnly
                    ? humanoid(model.head, empty(), empty(), empty(), empty(), empty())          // (the rest: the rig, see Rig)
                    : humanoid(model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg));
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
