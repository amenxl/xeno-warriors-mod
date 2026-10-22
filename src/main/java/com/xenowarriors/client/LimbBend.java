package com.xenowarriors.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Elbows and knees for the player model. The arms, legs, sleeves and pants are split in half (the lower half is a
 * child part), and every frame the lower halves bend from the pose the animations made:
 * <ul>
 * <li>elbows: nearly straight hanging down or reaching all the way out, bent in between - so punches chamber and snap
 *     out straight, running arms pump bent, folded arms fold;</li>
 * <li>knees: kicks chamber bent and extend straight, the heel lifts behind on a back-swing, shins hang down when
 *     riding, a little bend when sneaking.</li>
 * </ul>
 * Only Mikiko and Rhalix bend (everyone else keeps straight limbs).
 */
public final class LimbBend {
    public static final String LOWER = "xw_lower";
    /** Set while the first-person hand is drawn (keep it straight there). */
    public static boolean firstPerson;

    private LimbBend() {}

    /** Where a limb bone splits (local y), or 0 for parts that don't. */
    public static float splitY(String bone) {
        return switch (bone) {
            case "right_arm", "left_arm", "right_sleeve", "left_sleeve" -> 4F;
            case "right_leg", "left_leg", "right_pants", "left_pants" -> 6F;
            default -> 0F;
        };
    }

    // ------------------------------------------------------------------ the player model's split limbs

    /** Rebuilds the player model's arms, legs, sleeves and pants as two halves (same textures as vanilla). */
    public static void splitPlayerMesh(MeshDefinition mesh, CubeDeformation def, boolean slim) {
        PartDefinition root = mesh.getRoot();
        CubeDeformation outer = def.extend(0.25F);
        if (slim) {
            limb(root, "left_arm", 32, 48, -1F, -2F, -2F, 3F, 4F, 4F, def, PartPose.offset(5F, 2.5F, 0F));
            limb(root, "right_arm", 40, 16, -2F, -2F, -2F, 3F, 4F, 4F, def, PartPose.offset(-5F, 2.5F, 0F));
            limb(root, "left_sleeve", 48, 48, -1F, -2F, -2F, 3F, 4F, 4F, outer, PartPose.offset(5F, 2.5F, 0F));
            limb(root, "right_sleeve", 40, 32, -2F, -2F, -2F, 3F, 4F, 4F, outer, PartPose.offset(-5F, 2.5F, 0F));
        } else {
            limb(root, "left_arm", 32, 48, -1F, -2F, -2F, 4F, 4F, 4F, def, PartPose.offset(5F, 2F, 0F));
            limb(root, "right_arm", 40, 16, -3F, -2F, -2F, 4F, 4F, 4F, def, PartPose.offset(-5F, 2F, 0F));
            limb(root, "left_sleeve", 48, 48, -1F, -2F, -2F, 4F, 4F, 4F, outer, PartPose.offset(5F, 2F, 0F));
            limb(root, "right_sleeve", 40, 32, -3F, -2F, -2F, 4F, 4F, 4F, outer, PartPose.offset(-5F, 2F, 0F));
        }
        limb(root, "left_leg", 16, 48, -2F, 0F, -2F, 4F, 6F, 4F, def, PartPose.offset(1.9F, 12F, 0F));
        limb(root, "right_leg", 0, 16, -2F, 0F, -2F, 4F, 6F, 4F, def, PartPose.offset(-1.9F, 12F, 0F));
        limb(root, "left_pants", 0, 48, -2F, 0F, -2F, 4F, 6F, 4F, outer, PartPose.offset(1.9F, 12F, 0F));
        limb(root, "right_pants", 0, 32, -2F, 0F, -2F, 4F, 6F, 4F, outer, PartPose.offset(-1.9F, 12F, 0F));
    }

    /** A 12-pixel limb as two 6-pixel boxes; {@code upperH} is the length of the upper half above the pivot's top. */
    private static void limb(PartDefinition root, String name, int u, int v, float x, float y, float z, float w,
                             float upperUnused, float d, CubeDeformation def, PartPose pose) {
        PartDefinition upper = root.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, w, 6F, d, def), pose);
        // the lower half: side textures carry on 6 pixels further down the same strip
        upper.addOrReplaceChild(LOWER, CubeListBuilder.create().texOffs(u, v + 6).addBox(x, 0F, z, w, 6F, d, def),
                PartPose.offset(0F, y + 6F, 0F));
    }

    // ------------------------------------------------------------------ bending

    private static ModelPart lower(ModelPart limb) {
        return limb.hasChild(LOWER) ? limb.getChild(LOWER) : null;
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0F, 1F);
        return t * t * (3F - 2F * t);
    }

    /** Elbow bend (negative = forearm up/forward) from how far forward the upper arm is. */
    public static float elbow(float armX) {
        float f = -armX;
        float bell = smooth(0.15F, 0.7F, f) * (1F - smooth(1.25F, 1.65F, f));
        return -(0.14F + 0.95F * bell);
    }

    /** Knee bend (positive = shin back) from the thigh, the other thigh, and whether he's sitting. */
    public static float knee(float legX, float otherX, boolean riding) {
        float g = -legX;
        if (riding) return Mth.clamp(g, 0F, 1.6F);
        float bend = Math.max(0F, legX) * 1.15F;                                  // heel lifting behind
        boolean bothForward = g > 1.0F && -otherX > 1.0F;                       // sitting with legs stretched out
        if (!bothForward) bend += 1.05F * smooth(0.45F, 0.9F, g) * (1F - smooth(1.3F, 1.7F, g));   // kick chamber
        return bend;
    }

    public static void apply(PlayerModel<?> m, LivingEntity entity) {
        ModelPart ra = lower(m.rightArm), la = lower(m.leftArm), rl = lower(m.rightLeg), ll = lower(m.leftLeg);
        if (ra == null || la == null || rl == null || ll == null) return;
        boolean bends = !firstPerson && entity instanceof AbstractClientPlayer p
                && ClientCharacterData.get(p) != com.xenowarriors.character.CharacterType.NONE && !entity.isSwimming()
                && !entity.isFallFlying();
        float er = bends ? elbow(m.rightArm.xRot) : 0F, el = bends ? elbow(m.leftArm.xRot) : 0F;
        float kr = bends ? knee(m.rightLeg.xRot, m.leftLeg.xRot, m.riding) : 0F;
        float kl = bends ? knee(m.leftLeg.xRot, m.rightLeg.xRot, m.riding) : 0F;
        if (bends && m.crouching) {
            kr += 0.3F;
            kl += 0.3F;
        }
        pose(ra, er);
        pose(la, el);
        pose(rl, kr);
        pose(ll, kl);
        ModelPart rs = lower(m.rightSleeve), ls = lower(m.leftSleeve), rp = lower(m.rightPants), lp = lower(m.leftPants);
        if (rs != null) pose(rs, er);
        if (ls != null) pose(ls, el);
        if (rp != null) pose(rp, kr);
        if (lp != null) pose(lp, kl);
    }

    private static void pose(ModelPart lower, float x) {
        lower.xRot = x;
        lower.yRot = 0F;
        lower.zRot = 0F;
    }

    /** Copies the bends from the player model onto a detail model's limb (same part names). */
    public static void copy(ModelPart from, ModelPart to) {
        ModelPart a = lower(from), b = lower(to);
        if (a != null && b != null) b.copyFrom(a);
    }

    /** Moves a pose from the shoulder space of an arm into its hand, following the elbow. */
    public static void toForearm(ModelPart arm, com.mojang.blaze3d.vertex.PoseStack pose) {
        ModelPart low = lower(arm);
        if (low == null) return;
        low.translateAndRotate(pose);
        pose.translate(0F, -low.y / 16F, 0F);    // back to shoulder-relative offsets, now bent with the forearm
    }
}
