package com.xenowarriors.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

/**
 * Rhalix's Kishi cast poses (about 1.6 seconds, eased in and out).
 *
 * In the air (like the manga panel): wings spread wide, he pulls his right hand in to his chest and
 * pushes it out at the target, the left knee comes up and the right leg hangs straight.
 *
 * On the ground: he raises his right arm overhead and slams it down toward the target, the left
 * arm swung out for balance, feet planted in a wide braced stance.
 */
public final class KishiCastAnimation {
    public static final int DURATION = 32;
    private static final float IN = 4F, OUT_START = 23F;

    private KishiCastAnimation() {}

    /** Called after the vanilla pose is set up, before sleeves/pants copy the arms and legs. */
    public static void apply(HumanoidModel<?> model, LivingEntity entity, float ageInTicks) {
        ClientSkillState.KishiCast cast = ClientSkillState.kishiCast(entity);
        if (cast == null || entity.isPassenger() || entity.isVisuallySwimming()) return;
        float t = ageInTicks - cast.startTick();
        float w = weight(t);
        if (w <= 0F) return;

        // the casting hand points where he's looking
        float aimX = model.head.xRot - (float) Math.PI / 2F;
        float aimY = model.head.yRot;

        if (cast.airborne()) {
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

    /** 0..1 - how far the wings should be spread for the flying cast. */
    public static float wingSpread(LivingEntity entity, float ageInTicks) {
        ClientSkillState.KishiCast cast = ClientSkillState.kishiCast(entity);
        if (cast == null || !cast.airborne()) return 0F;
        return weight(ageInTicks - cast.startTick());
    }

    private static float weight(float t) {
        if (t < 0F || t > DURATION) return 0F;
        return Math.min(smooth(t / IN), 1F - smooth((t - OUT_START) / (DURATION - OUT_START)));
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
