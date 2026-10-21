package com.xenowarriors.client;

import com.xenowarriors.network.SkillAnimPacket;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

/**
 * Body poses for skills, eased in and out on top of the normal player animation.
 *
 * Kishi in the air (like the manga panel): wings spread wide, he pulls his right hand in to his chest
 * and pushes it out at the target, the left knee comes up and the right leg hangs straight.
 * Kishi on the ground: right arm raised overhead and slammed down toward the target, wide braced stance.
 * Earth Wave: lifts his right knee high, arms rising out to the sides, then stomps down hard
 * (the wave leaves at the moment his foot hits the ground).
 */
public final class SkillAnimations {
    public static final int KISHI_DURATION = 32;
    public static final int STOMP_DURATION = 22;
    /** Tick the stomping foot hits the ground - the Earth Wave entity waits this long before moving. */
    public static final int STOMP_IMPACT = com.xenowarriors.entity.EarthWaveEntity.IMPACT;

    private SkillAnimations() {}

    public static int duration(int anim) {
        return anim == SkillAnimPacket.STOMP ? STOMP_DURATION : KISHI_DURATION;
    }

    /** Called after the vanilla pose is set up, before sleeves/pants copy the arms and legs. */
    public static void apply(HumanoidModel<?> model, LivingEntity entity, float ageInTicks) {
        ClientSkillState.SkillAnim anim = ClientSkillState.currentAnim(entity);
        if (anim == null || entity.isPassenger() || entity.isVisuallySwimming()) return;
        float t = ageInTicks - anim.startTick();
        if (anim.anim() == SkillAnimPacket.STOMP) stomp(model, t);
        else kishi(model, t, anim.airborne());
    }

    private static void kishi(HumanoidModel<?> model, float t, boolean airborne) {
        float w = weight(t, KISHI_DURATION, 4F, 23F);
        if (w <= 0F) return;
        // the casting hand points where he's looking
        float aimX = model.head.xRot - (float) Math.PI / 2F;
        float aimY = model.head.yRot;

        if (airborne) {
            float push = smooth((t - 3F) / 5F);
            pose(model.rightArm, w, lerp(-0.95F, aimX + 0.1F, push), lerp(0.6F, aimY - 0.05F, push), lerp(0.1F, 0F, push));
            pose(model.leftArm, w, 0.45F, 0F, -0.4F);
            float knee = smooth(t / 7F);
            pose(model.leftLeg, w, lerp(0F, -1.3F, knee), 0F, -0.06F);
            pose(model.rightLeg, w, 0.22F, 0F, 0.05F);
        } else {
            float raise = smooth(t / 5F);
            float slam = smooth((t - 5F) / 3.5F);
            float armX = lerp(lerp(0F, -2.75F, raise), aimX + 0.3F, slam);
            float armY = lerp(lerp(0F, -0.15F, raise), aimY, slam);
            pose(model.rightArm, w, armX, armY, 0.08F);
            pose(model.leftArm, w, lerp(-0.2F, 0.4F, slam), 0F, lerp(-0.25F, -0.6F, slam));
            float brace = smooth((t - 4F) / 4F);
            pose(model.rightLeg, w, lerp(0F, -0.32F, brace), 0.1F * brace, 0.14F * brace);
            pose(model.leftLeg, w, lerp(0F, 0.38F, brace), -0.1F * brace, -0.14F * brace);
        }
    }

    private static void stomp(HumanoidModel<?> model, float t) {
        float w = weight(t, STOMP_DURATION, 1.5F, 14F);
        if (w <= 0F) return;
        float lift = smooth(t / (STOMP_IMPACT - 2F));
        float slam = smooth((t - (STOMP_IMPACT - 2F)) / 2F);
        // a little shake right after the impact
        float shake = t > STOMP_IMPACT && t < STOMP_IMPACT + 5 ? (float) Math.sin((t - STOMP_IMPACT) * 3.2F) * 0.05F * (STOMP_IMPACT + 5 - t) / 5F : 0F;

        pose(model.rightLeg, w, lerp(lerp(0F, -1.35F, lift), 0.08F, slam), 0F, lerp(0.05F, 0.16F, slam));
        pose(model.leftLeg, w, lerp(lerp(0F, 0.15F, lift), 0.3F, slam), 0F, lerp(-0.03F, -0.14F, slam));
        // arms rise out to the sides for balance, then drive down with the stomp
        pose(model.rightArm, w, lerp(lerp(0F, -0.35F, lift), 0.3F, slam) + shake, 0F, lerp(lerp(0.1F, 0.85F, lift), 0.45F, slam));
        pose(model.leftArm, w, lerp(lerp(0F, -0.35F, lift), 0.3F, slam) + shake, 0F, lerp(lerp(-0.1F, -0.85F, lift), -0.45F, slam));
        model.head.xRot += shake * w;
    }

    /** 0..1 - how far the wings should be spread for the flying Kishi cast. */
    public static float wingSpread(LivingEntity entity, float ageInTicks) {
        ClientSkillState.SkillAnim anim = ClientSkillState.currentAnim(entity);
        if (anim == null || anim.anim() != SkillAnimPacket.KISHI || !anim.airborne()) return 0F;
        return weight(ageInTicks - anim.startTick(), KISHI_DURATION, 4F, 23F);
    }

    private static float weight(float t, float duration, float in, float outStart) {
        if (t < 0F || t > duration) return 0F;
        return Math.min(smooth(t / in), 1F - smooth((t - outStart) / (duration - outStart)));
    }

    private static void pose(ModelPart part, float w, float x, float y, float z) {
        part.xRot = lerp(part.xRot, x, w);
        part.yRot = lerp(part.yRot, y, w);
        part.zRot = lerp(part.zRot, z, w);
    }

    private static float smooth(float x) {
        x = Math.max(0F, Math.min(1F, x));
        return x * x * (3F - 2F * x);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
