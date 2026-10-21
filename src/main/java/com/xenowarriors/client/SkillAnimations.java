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
 * Gravity Compress: both hands reach out at the target and slowly squeeze together, trembling.
 * Bang: finger-gun - right arm snaps up to aim, with a kick of recoil on every shot.
 * Gravity Barrage (ultimate): arms flung wide as he rises, then his hand aimed down at the
 * targets with a recoil on every volley, arms spread as he descends.
 */
public final class SkillAnimations {
    public static final int KISHI_DURATION = 32;
    public static final int STOMP_DURATION = 22;
    public static final int COMPRESS_DURATION = 60;
    public static final int BANG_DURATION = 14;
    public static final int BARRAGE_DURATION = com.xenowarriors.entity.GravityBarrageEntity.END;
    /** Tick the stomping foot hits the ground - the Earth Wave entity waits this long before moving. */
    public static final int STOMP_IMPACT = com.xenowarriors.entity.EarthWaveEntity.IMPACT;

    private SkillAnimations() {}

    public static int duration(int anim) {
        return switch (anim) {
            case SkillAnimPacket.STOMP -> STOMP_DURATION;
            case SkillAnimPacket.COMPRESS -> COMPRESS_DURATION;
            case SkillAnimPacket.BANG -> BANG_DURATION;
            case SkillAnimPacket.BARRAGE -> BARRAGE_DURATION;
            default -> KISHI_DURATION;
        };
    }

    /** Called after the vanilla pose is set up, before sleeves/pants copy the arms and legs. */
    public static void apply(HumanoidModel<?> model, LivingEntity entity, float ageInTicks) {
        ClientSkillState.SkillAnim anim = ClientSkillState.currentAnim(entity);
        if (anim == null || entity.isPassenger() || entity.isVisuallySwimming()) return;
        float t = ageInTicks - anim.startTick();
        switch (anim.anim()) {
            case SkillAnimPacket.STOMP -> stomp(model, t);
            case SkillAnimPacket.COMPRESS -> compress(model, t);
            case SkillAnimPacket.BANG -> bang(model, t);
            case SkillAnimPacket.BARRAGE -> barrage(model, t);
            default -> kishi(model, t, anim.airborne());
        }
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

    private static void compress(HumanoidModel<?> model, float t) {
        float w = weight(t, COMPRESS_DURATION, 4F, 50F);
        if (w <= 0F) return;
        float aimX = model.head.xRot - (float) Math.PI / 2F + 0.05F;
        float aimY = model.head.yRot;
        float squeeze = smooth((t - 6F) / 24F);
        float spread = lerp(0.45F, 0.06F, squeeze);
        float tremble = t > 10F ? (float) Math.sin(t * 2.6F) * 0.035F * squeeze : 0F;
        pose(model.rightArm, w, aimX + tremble, aimY + spread, 0F);
        pose(model.leftArm, w, aimX - tremble, aimY - spread, 0F);
        float brace = smooth(t / 6F);
        pose(model.rightLeg, w, -0.2F * brace, 0F, 0.1F * brace);
        pose(model.leftLeg, w, 0.25F * brace, 0F, -0.1F * brace);
    }

    private static void bang(HumanoidModel<?> model, float t) {
        float w = weight(t, BANG_DURATION, 1F, 8F);
        if (w <= 0F) return;
        float aimX = model.head.xRot - (float) Math.PI / 2F;
        float aimY = model.head.yRot - 0.05F;
        float recoil = t > 0.5F ? (float) Math.exp(-(t - 0.5F) * 0.7F) * 0.5F : t * 1F;
        pose(model.rightArm, w, aimX - recoil, aimY, 0F);
        pose(model.leftArm, w * 0.5F, 0.15F, 0F, -0.15F);
    }

    private static void barrage(HumanoidModel<?> model, float t) {
        float w = weight(t, BARRAGE_DURATION, 3F, 140F);
        if (w <= 0F) return;
        float aimX = model.head.xRot - (float) Math.PI / 2F + 0.1F;
        float aimY = model.head.yRot;
        float aimBlend = smooth((t - 24F) / 12F) * (1F - smooth((t - 108F) / 8F));
        // recoil right as each volley flies
        float recoil = 0F;
        int v = com.xenowarriors.entity.GravityBarrageEntity.volley(t);
        if (v >= 0) {
            float c = t - com.xenowarriors.entity.GravityBarrageEntity.BARRAGE - v * com.xenowarriors.entity.GravityBarrageEntity.VOLLEY_EVERY;
            recoil = c < 3F ? c / 3F * 0.35F : c < 6F ? 0.35F - (c - 3F) / 3F * 0.75F : -0.4F * (float) Math.exp(-(c - 6F) * 0.5F);
        }
        float rx = lerp(-0.45F, aimX + recoil, aimBlend), ry = lerp(0F, aimY, aimBlend), rz = lerp(1.35F, 0F, aimBlend);
        float descend = smooth((t - 110F) / 10F);
        pose(model.rightArm, w, lerp(rx, 0.15F, descend), lerp(ry, 0F, descend), lerp(rz, 0.9F, descend));
        pose(model.leftArm, w, lerp(lerp(-0.45F, -0.2F, aimBlend), 0.15F, descend), 0F, lerp(lerp(-1.35F, -1.05F, aimBlend), -0.9F, descend));
        pose(model.rightLeg, w, lerp(0.25F, 0.05F, descend), 0F, 0.05F);
        pose(model.leftLeg, w, lerp(-0.4F, 0.05F, descend), 0F, -0.05F);
    }

    /** 0..1 - how far the wings should be spread (flying Kishi cast, ultimate). */
    public static float wingSpread(LivingEntity entity, float ageInTicks) {
        ClientSkillState.SkillAnim anim = ClientSkillState.currentAnim(entity);
        if (anim == null) return 0F;
        float t = ageInTicks - anim.startTick();
        if (anim.anim() == SkillAnimPacket.BARRAGE) {
            if (t < 0F || t > 140F) return 0F;
            return Math.min(smooth(t / 6F), 1F - smooth((t - 128F) / 10F));
        }
        if (anim.anim() != SkillAnimPacket.KISHI || !anim.airborne()) return 0F;
        return weight(t, KISHI_DURATION, 4F, 23F);
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
