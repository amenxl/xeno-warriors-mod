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
    public static final int SCATTER_DURATION = 26, KITSUNE_DURATION = 44, NETSU_DURATION = 24, BLITZ_DURATION = 16;
    public static final int BLAZING_DURATION = com.xenowarriors.entity.BlazingWindEntity.CUTSCENE_END;
    /** Tick the stomping foot hits the ground - the Earth Wave entity waits this long before moving. */
    public static final int STOMP_IMPACT = com.xenowarriors.entity.EarthWaveEntity.IMPACT;

    private SkillAnimations() {}

    public static int duration(int anim) {
        return switch (anim) {
            case SkillAnimPacket.STOMP -> STOMP_DURATION;
            case SkillAnimPacket.COMPRESS -> COMPRESS_DURATION;
            case SkillAnimPacket.BANG -> BANG_DURATION;
            case SkillAnimPacket.BARRAGE -> BARRAGE_DURATION;
            case SkillAnimPacket.SCATTER -> SCATTER_DURATION;
            case SkillAnimPacket.KITSUNE -> KITSUNE_DURATION;
            case SkillAnimPacket.NETSU -> NETSU_DURATION;
            case SkillAnimPacket.BLITZ -> BLITZ_DURATION;
            case SkillAnimPacket.BLAZING -> BLAZING_DURATION;
            case SkillAnimPacket.TEAM_RHALIX, SkillAnimPacket.TEAM_MIKIKO -> com.xenowarriors.entity.TeamUltEntity.END;
            case SkillAnimPacket.DANCE_KICK -> 22;
            case SkillAnimPacket.NETSU_BURST -> 12;
            case SkillAnimPacket.MANIFEST -> 40;
            case SkillAnimPacket.EMPATHY -> 16;
            case SkillAnimPacket.KA_RELEASE -> 14;
            case SkillAnimPacket.CAST -> 22; case SkillAnimPacket.SLASH -> 18; case SkillAnimPacket.DASH -> 14; case SkillAnimPacket.SHOOT -> 24;
            case SkillAnimPacket.RAISE -> 30; case SkillAnimPacket.GROUND -> 22; case SkillAnimPacket.TYPE -> 26; case SkillAnimPacket.SWING -> 18;
            case SkillAnimPacket.GUARD -> 24; case SkillAnimPacket.PUNCH -> 14;
            default -> KISHI_DURATION;
        };
    }

    /**
     * Called after the vanilla pose is set up, before sleeves/pants copy the arms and legs.
     * Returns true if a skill pose was applied (so other animations stay out of the way).
     */
    public static boolean apply(HumanoidModel<?> model, LivingEntity entity, float ageInTicks) {
        ClientSkillState.SkillAnim anim = ClientSkillState.currentAnim(entity);
        if (anim == null || entity.isPassenger() || entity.isVisuallySwimming()) return false;
        float t = ageInTicks - anim.startTick();
        if (t < 0F) return false;
        switch (anim.anim()) {
            case SkillAnimPacket.STOMP -> stomp(model, t);
            case SkillAnimPacket.COMPRESS -> compress(model, t);
            case SkillAnimPacket.BANG -> bang(model, t);
            case SkillAnimPacket.BARRAGE -> barrage(model, t);
            case SkillAnimPacket.SCATTER -> scatter(model, t);
            case SkillAnimPacket.KITSUNE -> kitsune(model, t);
            case SkillAnimPacket.NETSU -> netsu(model, t);
            case SkillAnimPacket.BLITZ -> blitz(model, t);
            case SkillAnimPacket.BLAZING -> blazing(model, t);
            case SkillAnimPacket.TEAM_RHALIX -> teamRhalix(model, t);
            case SkillAnimPacket.TEAM_MIKIKO -> teamMikiko(model, t);
            case SkillAnimPacket.DANCE_KICK -> danceKick(model, t);
            case SkillAnimPacket.NETSU_BURST -> netsuBurst(model, t);
            case SkillAnimPacket.MANIFEST -> manifest(model, t);
            case SkillAnimPacket.EMPATHY -> empathy(model, t);
            case SkillAnimPacket.KA_RELEASE -> kaRelease(model, t);
            case SkillAnimPacket.CAST -> cast(model, t); case SkillAnimPacket.SLASH -> slash(model, t); case SkillAnimPacket.DASH -> dash(model, t);
            case SkillAnimPacket.SHOOT -> shoot(model, t); case SkillAnimPacket.RAISE -> raise(model, t); case SkillAnimPacket.GROUND -> ground(model, t);
            case SkillAnimPacket.TYPE -> typing(model, t); case SkillAnimPacket.SWING -> swing(model, t); case SkillAnimPacket.GUARD -> guard(model, t);
            case SkillAnimPacket.PUNCH -> punch(model, t);
            default -> kishi(model, t, anim.airborne());
        }
        return true;
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

    // ---------------- Mikiko's skills ----------------

    /** Hekublade Scatter: sweeps his arm out and points (the blades appear around him). */
    private static void scatter(HumanoidModel<?> model, float t) {
        float w = weight(t, SCATTER_DURATION, 2F, 18F);
        if (w <= 0F) return;
        float sweep = smooth(t / 8F);
        float aimX = model.head.xRot - (float) Math.PI / 2F;
        pose(model.rightArm, w, lerp(-0.4F, aimX, sweep), lerp(-1.2F, model.head.yRot, sweep), lerp(1.1F, 0F, sweep));
        pose(model.leftArm, w, -0.35F, 0F, -0.5F);
    }

    /** Kitsune Attack: crouch, blur forward, then hold the finishing pose - low, one hand to the ground,
     * other fist flung out behind (the whole-body crouch is in MikikoAnimations). */
    private static void kitsune(HumanoidModel<?> model, float t) {
        float w = weight(t, KITSUNE_DURATION, 2F, 36F);
        if (w <= 0F) return;
        float dash = smooth((t - 2F) / 2F) * (1F - smooth((t - 6F) / 2F));
        float fin = smooth((t - 6F) / 3F);
        // dash: arms swept back
        float[] dashPose = {0.9F, 0F, 0.25F, 0.9F, 0F, -0.25F, -0.6F, 0.4F};
        // finish: right fist out and up behind, left hand down to the ground, legs in a deep lunge
        float[] finPose = {-1.9F, 0.5F, 1.25F, -0.55F, 0.2F, -0.15F, 0.85F, -1.35F};
        float[] t2 = new float[8];
        for (int i = 0; i < 8; i++) t2[i] = lerp(dashPose[i] * dash, finPose[i], fin);
        pose(model.rightArm, w, t2[0], t2[1], t2[2]);
        pose(model.leftArm, w, t2[3], t2[4], t2[5]);
        pose(model.rightLeg, w, t2[6], 0F, 0.12F);
        pose(model.leftLeg, w, t2[7], 0F, -0.08F);
        model.head.xRot = lerp(model.head.xRot, model.head.xRot - 0.25F * fin, w);
    }

    /** Netsu: clenched fist raised up in front of him as the flames take him. */
    private static void netsu(HumanoidModel<?> model, float t) {
        float w = weight(t, NETSU_DURATION, 3F, 16F);
        if (w <= 0F) return;
        float shake = t > 3F && t < 14F ? (float) Math.sin(t * 3F) * 0.04F : 0F;
        pose(model.rightArm, w, -2.1F + shake, -0.35F, 0.15F);
        pose(model.leftArm, w, -0.2F, 0F, -0.35F);
        pose(model.rightLeg, w, -0.15F, 0F, 0.1F);
        pose(model.leftLeg, w, 0.2F, 0F, -0.1F);
    }

    /** Fire Blitz: fire cupped in both hands at his chest, then thrown overhand. */
    private static void blitz(HumanoidModel<?> model, float t) {
        float w = weight(t, BLITZ_DURATION, 2F, 11F);
        if (w <= 0F) return;
        float throwT = smooth((t - 5F) / 3F);
        float aimX = model.head.xRot - (float) Math.PI / 2F;
        pose(model.rightArm, w, lerp(lerp(-1.1F, -2.8F, smooth((t - 3F) / 2F)), aimX + 0.2F, throwT), lerp(-0.45F, model.head.yRot, throwT), 0F);
        pose(model.leftArm, w, lerp(-1.1F, -0.3F, throwT), lerp(0.45F, 0F, throwT), lerp(0F, -0.3F, throwT));
    }

    /** Blazing Wind: arms rise as the wind gathers, circle to spin the tornado up, then both palms thrust forward. */
    private static void blazing(HumanoidModel<?> model, float t) {
        float w = weight(t, BLAZING_DURATION, 4F, 92F);
        if (w <= 0F) return;
        float rise = smooth(t / 16F);
        float spin = smooth((t - 16F) / 6F) * (1F - smooth((t - 40F) / 4F));
        float thrust = smooth((t - 42F) / 4F);
        float circ = (float) Math.sin(t * 0.45F);
        float aimX = model.head.xRot - (float) Math.PI / 2F;
        float rx = lerp(lerp(lerp(0F, -2.8F, rise), -1.9F + circ * 0.5F, spin), aimX, thrust);
        float lx = lerp(lerp(lerp(0F, -2.8F, rise), -1.9F - circ * 0.5F, spin), aimX, thrust);
        float rz = lerp(lerp(0.2F, 0.75F, rise), 0.12F, Math.max(spin, thrust));
        pose(model.rightArm, w, rx, lerp(0F, -0.3F, thrust), rz);
        pose(model.leftArm, w, lx, lerp(0F, 0.3F, thrust), -rz);
        pose(model.rightLeg, w, lerp(0F, -0.35F, thrust), 0F, 0.12F);
        pose(model.leftLeg, w, lerp(0F, 0.4F, thrust), 0F, -0.12F);
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

    // ---------------------------------------------------------------- Gravity Fox Fire (team ultimate)

    /** Blends between keyframes: {tick, rightArm xyz, leftArm xyz, rightLeg xyz, leftLeg xyz, body xRot}. */
    private static void keyframes(HumanoidModel<?> m, float t, float[][] keys, float w) {
        int i = 0;
        while (i < keys.length - 2 && t >= keys[i + 1][0]) i++;
        float[] a = keys[i], b = keys[Math.min(i + 1, keys.length - 1)];
        float f = b[0] == a[0] ? 1F : smooth((t - a[0]) / (b[0] - a[0]));
        float[] k = new float[a.length];
        for (int j = 1; j < a.length; j++) k[j] = lerp(a[j], b[j], f);
        pose(m.rightArm, w, k[1], k[2], k[3]);
        pose(m.leftArm, w, k[4], k[5], k[6]);
        pose(m.rightLeg, w, k[7], k[8], k[9]);
        pose(m.leftLeg, w, k[10], k[11], k[12]);
        m.body.xRot = lerp(m.body.xRot, k[13], w);
    }

    /** Rhalix: rises arms flung wide, raises both hands overhead and slams the dome down, holds it with both hands
     * out (straining), floats down and ends arms folded, back to back with Mikiko. */
    private static void teamRhalix(HumanoidModel<?> m, float t) {
        float w = weight(t, com.xenowarriors.entity.TeamUltEntity.END, 3F, 100F);
        if (w <= 0F) return;
        float shake = t > 30 && t < 82 ? (float) Math.sin(t * 2.1F) * 0.04F : 0F;
        float[][] keys = {
                {0, 0.2F, 0, 0.3F, 0.2F, 0, -0.3F, 0.15F, 0, 0.08F, 0.1F, 0, -0.08F, 0},
                {10, -0.2F, 0, 1.35F, -0.2F, 0, -1.35F, 0.3F, 0, 0.1F, 0.2F, 0, -0.1F, 0},          // arms wide as he rises
                {15, -3.0F, -0.15F, 0.2F, -3.0F, 0.15F, -0.2F, 0.1F, 0, 0.05F, 0.3F, 0, -0.05F, -0.1F},   // both hands overhead
                {20, -0.7F, -0.1F, 0.1F, -0.7F, 0.1F, -0.1F, -0.4F, 0, 0.05F, 0.4F, 0, -0.05F, 0.35F}, // SLAM down
                {30, -1.55F, -0.25F, 0, -1.55F, 0.25F, 0, 0.15F, 0, 0.08F, 0.25F, 0, -0.08F, 0.1F},    // hands out at the dome
                {82, -1.55F, -0.3F, 0, -1.55F, 0.3F, 0, 0.15F, 0, 0.08F, 0.25F, 0, -0.08F, 0.1F},
                {92, -0.3F, 0, 0.9F, -0.3F, 0, -0.9F, 0.1F, 0, 0.1F, 0.1F, 0, -0.1F, 0},             // floating down
                {98, -1.05F, -0.75F, 0.15F, -1.05F, 0.75F, -0.15F, 0, 0, 0.12F, 0, 0, -0.12F, 0},       // arms folded
                {104, -1.05F, -0.75F, 0.15F, -1.05F, 0.75F, -0.15F, 0, 0, 0.12F, 0, 0, -0.12F, 0}};
        keyframes(m, t, keys, w);
        m.rightArm.xRot += shake * w;
        m.leftArm.xRot -= shake * w;
        if (t >= 98) m.head.xRot = lerp(m.head.xRot, -0.1F, w);
    }

    /** Mikiko: crouched gathering fox fire, leaps blade raised, spins on the dome with the blade held high, dives
     * blade-first, slams it into the ground, then rests it on his shoulder back to back with Rhalix. */
    private static void teamMikiko(HumanoidModel<?> m, float t) {
        float w = weight(t, com.xenowarriors.entity.TeamUltEntity.END, 3F, 100F);
        if (w <= 0F) return;
        float[][] keys = {
                {0, 0.4F, 0.2F, 0.35F, -0.6F, 0.2F, -0.3F, -0.5F, 0, 0.15F, 0.5F, 0, -0.15F, 0.45F},     // crouched, blade low behind
                {22, 0.5F, 0.2F, 0.4F, -0.7F, 0.2F, -0.35F, -0.6F, 0, 0.15F, 0.55F, 0, -0.15F, 0.5F},
                {26, -2.8F, 0, 0.15F, 0.6F, 0, -0.5F, -0.9F, 0, 0.1F, 0.45F, 0, -0.1F, -0.15F},          // leap, blade up
                {32, -3.05F, 0, 0.05F, -0.1F, 0, -1.45F, 0.1F, 0, 0.25F, -0.1F, 0, -0.25F, 0},            // spinning on top
                {78, -3.05F, 0, 0.05F, -0.1F, 0, -1.45F, 0.1F, 0, 0.25F, -0.1F, 0, -0.25F, 0},
                {80, -3.1F, 0.1F, 0, -3.1F, -0.1F, 0, 0, 0, 0.02F, 0, 0, -0.02F, 0},                     // diving, blade first
                {86, -3.1F, 0.1F, 0, -3.1F, -0.1F, 0, 0, 0, 0.02F, 0, 0, -0.02F, 0},
                {88, -0.5F, 0, 0.1F, -0.5F, 0.3F, -0.2F, -0.7F, 0, 0.15F, 0.6F, 0, -0.15F, 0.55F},        // blade slammed in
                {94, -0.5F, 0, 0.1F, -0.5F, 0.3F, -0.2F, -0.7F, 0, 0.15F, 0.6F, 0, -0.15F, 0.55F},
                {99, -2.3F, -0.4F, 0.2F, 0.15F, 0, -0.55F, 0, 0, 0.12F, 0, 0, -0.12F, 0},               // blade on shoulder, hand on hip
                {104, -2.3F, -0.4F, 0.2F, 0.15F, 0, -0.55F, 0, 0, 0.12F, 0, 0, -0.12F, 0}};
        keyframes(m, t, keys, w);
    }

    /** Dance Kick: disco point to the sky, a spinning kick with the leg out wide, then a flying front kick. */
    private static void danceKick(HumanoidModel<?> m, float t) {
        float w = weight(t, 22, 1.5F, 18F);
        if (w <= 0F) return;
        float spin = smooth((t - 2F) / 3F) * (1F - smooth((t - 10F) / 2F));
        float fin = smooth((t - 12F) / 2F);
        float swirl = (float) Math.sin(t * 1.4F);
        pose(m.rightArm, w, lerp(lerp(-2.9F, -1.6F, spin), -0.6F, fin), 0F, lerp(lerp(0.2F, 1.2F, spin), 0.6F, fin));
        pose(m.leftArm, w, lerp(lerp(0.3F, -1.6F, spin), -0.6F, fin), 0F, lerp(lerp(-0.4F, -1.2F, spin), -0.6F, fin));
        pose(m.rightLeg, w, lerp(lerp(0F, -0.9F + swirl * 0.3F, spin), -1.55F, fin), 0F, lerp(lerp(0.05F, 1.0F, spin), 0.05F, fin));
        pose(m.leftLeg, w, lerp(0F, 0.35F, Math.max(spin, fin)), 0F, -0.08F);
    }

    /** Netsu Burst: crouched sprint start, then leaning far forward with arms swept back. */
    private static void netsuBurst(HumanoidModel<?> m, float t) {
        float w = weight(t, 12, 1F, 9F);
        if (w <= 0F) return;
        float run = (float) Math.sin(t * 2.2F);
        pose(m.rightArm, w, 1.3F, 0.2F, 0.35F);
        pose(m.leftArm, w, 1.3F, -0.2F, -0.35F);
        pose(m.rightLeg, w, run * 1.1F - 0.2F, 0F, 0.05F);
        pose(m.leftLeg, w, -run * 1.1F - 0.2F, 0F, -0.05F);
        m.head.xRot -= 0.3F * w;
    }

    /** Manifest Greatness: fists clenched at his sides while he chants, then both arms thrown up. */
    private static void manifest(HumanoidModel<?> m, float t) {
        float w = weight(t, 40, 3F, 34F);
        if (w <= 0F) return;
        float shake = t < 26F ? (float) Math.sin(t * 3.5F) * 0.04F * smooth(t / 20F) : 0F;
        float up = smooth((t - 25F) / 3F);
        pose(m.rightArm, w, lerp(0.25F + shake, -2.9F, up), 0F, lerp(0.35F, 0.45F, up));
        pose(m.leftArm, w, lerp(0.25F - shake, -2.9F, up), 0F, lerp(-0.35F, -0.45F, up));
        pose(m.rightLeg, w, 0F, 0F, lerp(0.12F, 0.2F, up));
        pose(m.leftLeg, w, 0F, 0F, lerp(-0.12F, -0.2F, up));
        m.head.xRot += lerp(0.35F, -0.45F, up) * w;
    }

    /** True Empathy: two fingers to his temple, then the hand opens toward them. */
    private static void empathy(HumanoidModel<?> m, float t) {
        float w = weight(t, 16, 2F, 12F);
        if (w <= 0F) return;
        float open = smooth((t - 7F) / 3F);
        float aimX = m.head.xRot - (float) Math.PI / 2F;
        pose(m.rightArm, w, lerp(-2.2F, aimX + 0.2F, open), lerp(-0.9F, m.head.yRot, open), lerp(0.3F, 0F, open));
        pose(m.leftArm, w, -0.15F, 0F, -0.2F);
    }

    /** KA Release: arms flung out and wide as the hold breaks. */
    private static void kaRelease(HumanoidModel<?> m, float t) {
        float w = weight(t, 14, 1F, 10F);
        if (w <= 0F) return;
        float burst = smooth((t - 2F) / 2F);
        pose(m.rightArm, w, lerp(-0.6F, -0.3F, burst), 0F, lerp(0.2F, 1.9F, burst));
        pose(m.leftArm, w, lerp(-0.6F, -0.3F, burst), 0F, lerp(-0.2F, -1.9F, burst));
        pose(m.rightLeg, w, 0F, 0F, 0.25F * burst);
        pose(m.leftLeg, w, 0F, 0F, -0.25F * burst);
        m.head.xRot -= 0.35F * burst * w;
    }

    // ------------------------------------------------------------------ general moves for the newer characters

    /** Cast: the right hand thrown out where they're looking, the other drawn back, feet braced. */
    private static void cast(HumanoidModel<?> m, float t) {
        float w = weight(t, 22, 3F, 16F); if (w <= 0F) return;
        float aimX = m.head.xRot - (float) Math.PI / 2F, aimY = m.head.yRot;
        float push = smooth((t - 1F) / 4F);
        pose(m.rightArm, w, lerp(-0.6F, aimX, push), lerp(0.3F, aimY, push), 0F);
        pose(m.leftArm, w, 0.35F, 0F, -0.35F);
        pose(m.rightLeg, w, -0.25F, 0F, 0.08F); pose(m.leftLeg, w, 0.3F, 0F, -0.08F);
        m.body.yRot = lerp(m.body.yRot, -0.2F * push, w);
    }

    /** Slash / thrust: the weapon arm up high and cutting down across the body. */
    private static void slash(HumanoidModel<?> m, float t) {
        float w = weight(t, 18, 2F, 13F); if (w <= 0F) return;
        float cut = smooth((t - 2F) / 4F);
        pose(m.rightArm, w, lerp(-2.6F, -0.6F, cut), lerp(-0.3F, 0.7F, cut), lerp(0.2F, -0.2F, cut));
        pose(m.leftArm, w, -0.4F, 0F, -0.3F);
        m.body.yRot = lerp(m.body.yRot, lerp(-0.35F, 0.35F, cut), w);
        pose(m.rightLeg, w, -0.35F, 0F, 0.05F); pose(m.leftLeg, w, 0.35F, 0F, -0.05F);
    }

    /** Dash: leaning hard into it, weapon arm out front, the other swept back. */
    private static void dash(HumanoidModel<?> m, float t) {
        float w = weight(t, 14, 1.5F, 10F); if (w <= 0F) return;
        m.body.xRot = lerp(m.body.xRot, 0.45F, w);
        pose(m.rightArm, w, -1.4F, 0.2F, 0.1F);
        pose(m.leftArm, w, 0.9F, 0F, -0.2F);
        pose(m.rightLeg, w, -0.6F, 0F, 0F); pose(m.leftLeg, w, 0.7F, 0F, 0F);
        m.head.xRot = lerp(m.head.xRot, -0.3F, w);
    }

    /** Shoot: both arms out at the target, jolting with each shot. */
    private static void shoot(HumanoidModel<?> m, float t) {
        float w = weight(t, 24, 2F, 18F); if (w <= 0F) return;
        float aimX = m.head.xRot - (float) Math.PI / 2F, aimY = m.head.yRot;
        float kick = (float) Math.abs(Math.sin(t * 1.6F)) * 0.18F;
        pose(m.rightArm, w, aimX - kick, aimY - 0.15F, 0F);
        pose(m.leftArm, w, aimX - kick * 0.8F, aimY + 0.15F, 0F);
        pose(m.rightLeg, w, -0.2F, 0F, 0.1F); pose(m.leftLeg, w, 0.25F, 0F, -0.1F);
    }

    /** Raise: both arms up and out, chest open to the sky - for ultimates and power-ups. */
    private static void raise(HumanoidModel<?> m, float t) {
        float w = weight(t, 30, 5F, 22F); if (w <= 0F) return;
        float up = smooth(t / 8F);
        float shake = (float) Math.sin(t * 2.4F) * 0.03F * up;
        pose(m.rightArm, w, lerp(-0.5F, -2.8F, up) + shake, 0F, lerp(0.3F, 0.55F, up));
        pose(m.leftArm, w, lerp(-0.5F, -2.8F, up) - shake, 0F, lerp(-0.3F, -0.55F, up));
        m.head.xRot = lerp(m.head.xRot, -0.45F * up, w);
        m.body.xRot = lerp(m.body.xRot, -0.1F * up, w);
        pose(m.rightLeg, w, 0F, 0F, 0.12F); pose(m.leftLeg, w, 0F, 0F, -0.12F);
    }

    /** Ground: arms up, then both fists slam down into the ground with the knees bent. */
    private static void ground(HumanoidModel<?> m, float t) {
        float w = weight(t, 22, 2F, 16F); if (w <= 0F) return;
        float lift = smooth(t / 4F), slam = smooth((t - 4F) / 2.5F);
        float armX = lerp(lerp(0F, -2.9F, lift), -0.4F, slam);
        pose(m.rightArm, w, armX, 0F, 0.15F); pose(m.leftArm, w, armX, 0F, -0.15F);
        m.body.xRot = lerp(m.body.xRot, 0.5F * slam, w);
        pose(m.rightLeg, w, -0.5F * slam, 0F, 0.15F); pose(m.leftLeg, w, -0.5F * slam, 0F, -0.15F);
    }

    /** Typing: hands low in front, fingers going, head down at the screen. */
    private static void typing(HumanoidModel<?> m, float t) {
        float w = weight(t, 26, 3F, 20F); if (w <= 0F) return;
        float a = (float) Math.sin(t * 1.4F) * 0.08F, b = (float) Math.sin(t * 1.4F + 1.7F) * 0.08F;
        pose(m.rightArm, w, -0.7F + a, -0.2F, 0.1F); pose(m.leftArm, w, -0.7F + b, 0.2F, -0.1F);
        m.head.xRot = lerp(m.head.xRot, 0.35F, w);
    }

    /** Swing: a big two-handed swing (bat, broom, great cut) from behind the shoulder right across. */
    private static void swing(HumanoidModel<?> m, float t) {
        float w = weight(t, 18, 2F, 13F); if (w <= 0F) return;
        float s = smooth((t - 2F) / 4F);
        float yaw = lerp(-1.2F, 1.0F, s);
        pose(m.rightArm, w, -1.5F, yaw, 0F); pose(m.leftArm, w, -1.4F, yaw + 0.45F, 0F);
        m.body.yRot = lerp(m.body.yRot, lerp(-0.7F, 0.6F, s), w);
        pose(m.rightLeg, w, -0.3F, 0F, 0.1F); pose(m.leftLeg, w, 0.3F, 0F, -0.1F);
    }

    /** Guard: arms crossed in front, braced. */
    private static void guard(HumanoidModel<?> m, float t) {
        float w = weight(t, 24, 3F, 18F); if (w <= 0F) return;
        pose(m.rightArm, w, -1.45F, -0.75F, 0F); pose(m.leftArm, w, -1.3F, 0.75F, 0F);
        m.head.xRot = lerp(m.head.xRot, 0.15F, w);
        pose(m.rightLeg, w, -0.15F, 0F, 0.12F); pose(m.leftLeg, w, 0.15F, 0F, -0.12F);
    }

    /** Punch: a straight right, the shoulder and body behind it. */
    private static void punch(HumanoidModel<?> m, float t) {
        float w = weight(t, 14, 1F, 9F); if (w <= 0F) return;
        float hit = smooth((t - 1F) / 2F);
        float aimX = m.head.xRot - (float) Math.PI / 2F;
        pose(m.rightArm, w, lerp(-0.4F, aimX, hit), m.head.yRot, 0F);
        pose(m.leftArm, w, -0.9F, 0.4F, -0.1F);
        m.body.yRot = lerp(m.body.yRot, 0.35F * hit, w);
        pose(m.rightLeg, w, 0.3F, 0F, 0F); pose(m.leftLeg, w, -0.35F, 0F, 0F);
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
