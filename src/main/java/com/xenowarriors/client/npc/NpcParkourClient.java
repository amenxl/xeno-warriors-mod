package com.xenowarriors.client.npc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.xenowarriors.bot.BotParkour;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * An NPC's parkour move (NpcParkour) drawn on its body: the whole body leans, tips or tumbles with the move (works with
 * Epic Fight's bodies too), and on the plain model the arms and legs take the pose - hands planted on a vault, reaching
 * up on a climb, tucked in a roll, one leg out in a slide.
 */
public final class NpcParkourClient {
    private NpcParkourClient() {}

    private record Move(int code, long at) {}
    private static final Map<Integer, Move> MOVES = new HashMap<>();
    /** How long each move's pose lasts (ticks) if no "done" comes. */
    private static final int[] LEN = {0, 11, 11, 8, 10, 9, 9, 9, 12, 0, 10, 10, 20};

    public static void set(int entity, int packed) {
        int code = BotParkour.codeOf(packed);
        var mc = Minecraft.getInstance();
        if (code == BotParkour.NONE || mc.level == null) MOVES.remove(entity);
        else MOVES.put(entity, new Move(code, mc.level.getGameTime()));
        if (MOVES.size() > 256) MOVES.clear();
    }

    /** The move and how far through it (0-1), or null. */
    private static float[] now(LivingEntity e, float pt) {
        Move m = MOVES.get(e.getId());
        if (m == null || e.level() == null) return null;
        float t = (e.level().getGameTime() - m.at) + pt, len = Math.max(1, LEN[Math.min(m.code, LEN.length - 1)]);
        if (t > len + 4) { MOVES.remove(e.getId()); return null; }
        return new float[]{m.code, Mth.clamp(t / len, 0F, 1F)};
    }

    /** The body's turn for the move (from the renderer's setupRotations). */
    public static void rotate(LivingEntity e, PoseStack pose, float pt) {
        float[] n = now(e, pt);
        if (n == null) return;
        int code = (int) n[0];
        float p = n[1], arc = Mth.sin(p * Mth.PI);
        switch (code) {
            case BotParkour.VAULT_R, BotParkour.VAULT_L -> {                    // sideways over it, legs swung through
                pose.translate(0, 0.9, 0);
                pose.mulPose(Axis.ZP.rotationDegrees((code == BotParkour.VAULT_R ? -1 : 1) * 55F * arc));
                pose.mulPose(Axis.XP.rotationDegrees(15F * arc));
                pose.translate(0, -0.9, 0);
            }
            case BotParkour.WALLJUMP_R, BotParkour.WALLJUMP_L ->
                    pose.mulPose(Axis.ZP.rotationDegrees((code == BotParkour.WALLJUMP_R ? -1 : 1) * 22F * arc));
            case BotParkour.CLING, BotParkour.CLIMB -> pose.mulPose(Axis.XP.rotationDegrees(-12F));   // (flat to the wall)
            case BotParkour.ROLL -> {                                           // a full forward tumble
                pose.translate(0, 0.5, 0);
                pose.mulPose(Axis.XP.rotationDegrees(-360F * p));
                pose.translate(0, -0.5, 0);
            }
            case BotParkour.SLIDE -> {                                          // leaning right back, low
                pose.translate(0, 0.15 * arc, 0);
                pose.mulPose(Axis.XP.rotationDegrees(62F * Math.min(1F, arc * 1.6F)));
            }
            case BotParkour.DODGE_L, BotParkour.DODGE_R -> pose.mulPose(Axis.ZP.rotationDegrees((code == BotParkour.DODGE_R ? 1 : -1) * 25F * arc));
            case BotParkour.LEAP -> pose.mulPose(Axis.XP.rotationDegrees(-25F * arc));
            default -> {}
        }
    }

    /** Arms and legs for the move (from a humanoid NPC model's setupAnim, last). */
    public static void limbs(LivingEntity e, HumanoidModel<?> m) {
        float[] n = now(e, Minecraft.getInstance().getFrameTime());
        if (n == null) return;
        int code = (int) n[0];
        float arc = Mth.sin(n[1] * Mth.PI);
        switch (code) {
            case BotParkour.VAULT_R, BotParkour.VAULT_L -> {
                boolean r = code == BotParkour.VAULT_R;
                (r ? m.rightArm : m.leftArm).xRot = -0.4F; (r ? m.rightArm : m.leftArm).zRot = (r ? 1 : -1) * 0.3F;   // the planted hand
                (r ? m.leftArm : m.rightArm).xRot = -2.4F * arc;
                m.rightLeg.xRot = -1.2F * arc; m.leftLeg.xRot = -1.0F * arc;
            }
            case BotParkour.CLING, BotParkour.CLIMB, BotParkour.WALLJUMP_R, BotParkour.WALLJUMP_L -> {
                m.rightArm.xRot = -2.9F; m.leftArm.xRot = -2.9F;
                m.rightArm.zRot = 0.15F; m.leftArm.zRot = -0.15F;
                m.rightLeg.xRot = code == BotParkour.CLIMB ? -1.1F * arc : 0.2F; m.leftLeg.xRot = code == BotParkour.CLIMB ? 0.3F : -0.3F;
            }
            case BotParkour.ROLL -> {
                m.rightArm.xRot = -1.4F; m.leftArm.xRot = -1.4F;
                m.rightLeg.xRot = -1.6F; m.leftLeg.xRot = -1.6F;
                m.head.xRot = 0.8F;
            }
            case BotParkour.SLIDE -> {
                m.rightLeg.xRot = -1.4F; m.leftLeg.xRot = -0.6F;
                m.rightArm.xRot = -0.6F; m.leftArm.xRot = 0.4F; m.leftArm.zRot = -0.7F;
                m.head.xRot = -0.5F;
            }
            case BotParkour.DODGE_L, BotParkour.DODGE_R -> { m.rightArm.xRot = -1.2F; m.leftArm.xRot = -1.2F; }
            default -> {}
        }
    }
}
