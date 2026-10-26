package com.xenowarriors.client;

import com.xenowarriors.character.CharacterType;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Ultima's body language by output: at 25% his hands stay in his pockets and he fights with lazy kicks, like the
 * fight bores him; at 70% his blows are heavy two-handed slams and haymakers.
 */
public final class UltimaAnim {
    private UltimaAnim() {}

    public static void apply(HumanoidModel<?> m, Player p, float age) {
        if (ClientCharacterData.get((net.minecraft.client.player.AbstractClientPlayer) p) != CharacterType.ULTIMA) return;
        if (UltimaClient.overload(p.getId())) return;
        int tier = UltimaClient.tier(p.getId());
        float atk = m.attackTime;
        if (tier == 0 && atk <= 0F && idle(m, p, age)) return;
        if (tier == 0) {
            // hands in his pockets, shoulders a bit slack, head tilted - not even trying
            m.rightArm.xRot = 0.18F + (p.isCrouching() ? 0.3F : 0F);
            m.leftArm.xRot = 0.18F + (p.isCrouching() ? 0.3F : 0F);
            m.rightArm.yRot = 0.25F; m.leftArm.yRot = -0.25F;
            m.rightArm.zRot = 0.12F; m.leftArm.zRot = -0.12F;
            m.head.zRot = Mth.sin(age * 0.03F) * 0.06F + 0.08F;
            if (atk > 0F) {                                // a lazy kick instead of a punch
                float s = Mth.sin(atk * Mth.PI);
                m.rightLeg.xRot = -1.45F * s;
                m.rightLeg.zRot = 0.1F * s;
                m.leftLeg.xRot = 0.25F * s;
                m.body.yRot = -0.15F * s;
                m.rightArm.xRot += 0.1F * s; m.leftArm.xRot += 0.1F * s;
            }
        } else if (tier == 2 && atk > 0F) {
            // heavy blows: both fists come up and smash down, the body throwing its weight in
            boolean slam = (p.swingTime + p.getId()) % 2 == 0 || p.fallDistance > 0.5F;
            float up = atk < 0.45F ? atk / 0.45F : 1F - (atk - 0.45F) / 0.55F;
            float down = atk > 0.45F ? Mth.sin((atk - 0.45F) / 0.55F * Mth.PI) : 0F;
            if (slam) {
                float x = -2.9F * up + 0.8F * down;
                m.rightArm.xRot = x; m.leftArm.xRot = x;
                m.rightArm.zRot = 0.2F * up; m.leftArm.zRot = -0.2F * up;
                m.rightArm.yRot = -0.3F * up; m.leftArm.yRot = 0.3F * up;
                m.body.xRot = atk > 0.45F ? 0.5F * down : -0.2F * up;
                m.rightLeg.xRot = -0.4F * down; m.leftLeg.xRot = 0.35F * down;
            } else {                                       // haymaker: wound way back, then swung through
                float s = Mth.sin(atk * Mth.PI);
                m.rightArm.xRot = -1.4F - 0.3F * s;
                m.rightArm.yRot = atk < 0.4F ? 1.0F * (atk / 0.4F) : 1.0F - 2.2F * ((atk - 0.4F) / 0.6F);
                m.body.yRot = atk < 0.4F ? 0.5F * (atk / 0.4F) : 0.5F - 1.1F * ((atk - 0.4F) / 0.6F);
                m.leftArm.xRot = -0.6F; m.leftArm.zRot = -0.3F;
                m.rightLeg.xRot = -0.3F * s; m.leftLeg.xRot = 0.3F * s;
            }
        }
    }

    private static final java.util.Map<Integer, Integer> STILL_SINCE = new java.util.HashMap<>();

    private static float smooth(float x) { x = Mth.clamp(x, 0F, 1F); return x * x * (3F - 2F * x); }

    private static void lerp(net.minecraft.client.model.geom.ModelPart part, float w, float x, float y, float z) {
        part.xRot = Mth.lerp(w, part.xRot, x); part.yRot = Mth.lerp(w, part.yRot, y); part.zRot = Mth.lerp(w, part.zRot, z);
    }

    /**
     * Standing around at 25%, he settles into lazy poses: leaning back on a wall with his arms crossed (if there's a
     * wall behind him), otherwise switching between arms crossed and rubbing the back of his neck with a hand in
     * his pocket; crouching still, he squats down. Returns true if an idle pose was drawn.
     */
    private static boolean idle(HumanoidModel<?> m, Player p, float age) {
        boolean still = p.onGround() && p.getDeltaMovement().horizontalDistanceSqr() < 1.0E-4 && !p.isUsingItem();
        if (!still) { STILL_SINCE.remove(p.getId()); return false; }
        int since = STILL_SINCE.computeIfAbsent(p.getId(), k -> p.tickCount);
        float t = age - since;
        float w = smooth((t - 30F) / 20F);
        if (w <= 0F) return false;
        float breathe = Mth.sin(age * 0.08F) * 0.03F;
        if (p.isCrouching()) {                                       // squatting on his heels, an arm on his knee
            lerp(m.rightLeg, w, -1.45F, 0.3F, 0.12F); lerp(m.leftLeg, w, -1.45F, -0.3F, -0.12F);
            lerp(m.rightArm, w, -1.1F + breathe, -0.25F, 0.15F); lerp(m.leftArm, w, -0.7F, 0.1F, -0.1F);
            LimbBend.extraKnees(p, 2.3F * w, 2.3F * w);
            LimbBend.extraElbows(p, -0.6F * w, -0.3F * w);
            float drop = 5.5F * w;                                      // the whole body sinks down onto his heels
            m.head.y += drop; m.body.y += drop; m.rightArm.y += drop; m.leftArm.y += drop; m.rightLeg.y += drop; m.leftLeg.y += drop;
            m.rightLeg.z -= 2F * w; m.leftLeg.z -= 2F * w;
            m.head.xRot = Mth.lerp(w, m.head.xRot, m.head.xRot + 0.2F);
            return true;
        }
        var back = net.minecraft.core.BlockPos.containing(p.position().subtract(p.getLookAngle().multiply(0.8, 0, 0.8))).above();
        boolean wall = p.level().getBlockState(back).isCollisionShapeFullBlock(p.level(), back);
        int pose = wall ? 0 : (int) (t / 240) % 2 == 0 ? 1 : 2;
        if (pose == 2) {                                             // rubbing the back of his neck, other hand in his pocket
            lerp(m.rightArm, w, -2.55F + breathe, 0.15F, -0.75F);
            LimbBend.extraElbows(p, -1.7F * w, 0F);
            lerp(m.leftArm, w, 0.18F, -0.25F, -0.12F);
            m.head.xRot = Mth.lerp(w, m.head.xRot, 0.25F);
            m.head.zRot = Mth.lerp(w, m.head.zRot, -0.15F);
            lerp(m.leftLeg, w, -0.08F, 0F, -0.08F); lerp(m.rightLeg, w, 0.05F, 0F, 0.06F);
        } else {                                                     // arms crossed (leaning back on the wall, one foot crossed over)
            lerp(m.rightArm, w, -0.95F + breathe, -0.62F, 0.12F);
            lerp(m.leftArm, w, -1.05F + breathe, 0.62F, -0.12F);
            LimbBend.extraElbows(p, -0.5F * w, -0.5F * w);
            m.head.zRot = Mth.lerp(w, m.head.zRot, 0.12F);
            if (pose == 0) {
                m.body.xRot = Mth.lerp(w, m.body.xRot, -0.12F);
                lerp(m.rightLeg, w, 0.15F, 0F, -0.22F); lerp(m.leftLeg, w, -0.1F, 0F, -0.04F);
            } else {
                lerp(m.leftLeg, w, -0.08F, 0F, -0.08F); lerp(m.rightLeg, w, 0.05F, 0F, 0.06F);
            }
        }
        return true;
    }

    /**
     * Boss intro (for the Ultima boss, not wired up yet): sitting on a Government facility's roof edge, legs
     * swinging, forearms on his lap, bored and looking away. {@code notice} 0..1 turns his head to the player and
     * leans him forward (switch the face to grin past 0.7); then the boss pushes off and drops. Knee bend of
     * ~1.45 + 0.45*swing per leg is applied by the renderer's limb bending.
     */
    public static void perch(HumanoidModel<?> m, float age, float notice) {
        float swing = Mth.sin(age * 0.21F) * (1F - notice);
        m.rightLeg.xRot = -1.5F + 0.05F * swing; m.rightLeg.yRot = 0.12F; m.rightLeg.zRot = 0.05F;
        m.leftLeg.xRot = -1.5F - 0.05F * swing;  m.leftLeg.yRot = -0.12F; m.leftLeg.zRot = -0.05F;
        m.body.xRot = 0.25F * notice;
        m.rightArm.xRot = -0.3F - 0.15F * notice; m.rightArm.yRot = -0.1F; m.rightArm.zRot = 0.12F;
        m.leftArm.xRot = -0.2F - 0.15F * notice;  m.leftArm.yRot = 0.35F;  m.leftArm.zRot = -0.2F;
        m.head.xRot = 0.05F + 0.1F * notice; m.head.yRot = 0.55F - 0.75F * notice; m.head.zRot = 0.08F + 0.1F * notice;
    }

    /** Knee bends that go with {@link #perch}: right, left. */
    public static float[] perchKnees(float age, float notice) {
        float swing = Mth.sin(age * 0.21F) * (1F - notice);
        return new float[] {1.45F + 0.45F * swing, 1.45F - 0.45F * swing};
    }

    /** What his energy is called: Data (Gov data powers him), not Karma. */
    public static String resource() {
        return ClientCharacterData.getLocal() == CharacterType.ULTIMA ? "Data" : "Karma";
    }
}
