package com.xenowarriors.client;

import com.xenowarriors.anim.Poses;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

/** Draws anim/Poses on any humanoid model: one lasting state underneath, one short action on top. */
public final class EntityPoses {
    private record P(int pose, int start, int ticks) {}

    private static final Map<Integer, P> STATE = new HashMap<>(), ACTION = new HashMap<>();

    private EntityPoses() {}

    public static void set(int id, int pose, int ticks) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        Entity e = level.getEntity(id);
        if (e == null) return;
        if (pose == Poses.CLEAR) { STATE.remove(id); return; }
        (Poses.isState(pose) ? STATE : ACTION).put(id, new P(pose, e.tickCount, ticks));
    }

    /** The action pose an entity is in (for non-humanoid models to read), or -1. */
    public static int action(Entity e) {
        P p = ACTION.get(e.getId());
        return p != null && e.tickCount - p.start <= p.ticks ? p.pose : -1;
    }

    public static float actionTime(Entity e, float partial) {
        P p = ACTION.get(e.getId());
        return p == null ? 0 : (e.tickCount - p.start + partial) / Math.max(1, p.ticks);
    }

    public static void apply(HumanoidModel<?> m, LivingEntity e, float age) {
        P s = STATE.get(e.getId());
        if (s != null) {
            float t = age - s.start;
            if (t > s.ticks || t < -2) STATE.remove(e.getId());
            else state(m, s.pose, t, s.ticks);
        }
        P a = ACTION.get(e.getId());
        if (a != null) {
            float t = age - a.start;
            if (t > a.ticks || t < -2) ACTION.remove(e.getId());
            else action(m, a.pose, Math.max(0, t), a.ticks);
        }
    }

    // ---------------------------------------------------------------- helpers

    private static float smooth(float x) { x = Mth.clamp(x, 0F, 1F); return x * x * (3F - 2F * x); }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    /** Fade in over the first {@code in} ticks and out over the last {@code out}. */
    private static float w(float t, float dur, float in, float out) {
        return Math.min(smooth(t / in), smooth((dur - t) / out));
    }

    private static void pose(ModelPart p, float w, float x, float y, float z) {
        p.xRot = lerp(p.xRot, x, w);
        p.yRot = lerp(p.yRot, y, w);
        p.zRot = lerp(p.zRot, z, w);
    }

    // ---------------------------------------------------------------- lasting states

    private static void state(HumanoidModel<?> m, int pose, float t, int dur) {
        float w = w(t, dur, 3F, 3F);
        switch (pose) {
            case Poses.CLASH -> {           // both hands thrust out holding the blast, leaning in and straining
                float shake = Mth.sin(t * 2.7F) * 0.06F;
                float aimX = m.head.xRot - Mth.HALF_PI;
                pose(m.rightArm, w, aimX + shake, -0.25F, 0.05F);
                pose(m.leftArm, w, aimX - shake, 0.25F, -0.05F);
                pose(m.rightLeg, w, -0.45F, 0F, 0.1F);
                pose(m.leftLeg, w, 0.5F, 0F, -0.1F);
                m.body.xRot = lerp(m.body.xRot, 0.22F, w);
                m.head.zRot = shake * 0.5F * w;
            }
            case Poses.BOUND -> {           // arms pinned to the sides, twisting to get free
                float tw = Mth.sin(t * 1.1F) * 0.18F, jerk = Mth.sin(t * 3.3F) * 0.05F;
                pose(m.rightArm, w, 0.05F + jerk, 0F, 0.05F);
                pose(m.leftArm, w, 0.05F - jerk, 0F, -0.05F);
                pose(m.rightLeg, w, 0F, 0F, 0.02F);
                pose(m.leftLeg, w, 0F, 0F, -0.02F);
                m.body.yRot = lerp(m.body.yRot, tw, w);
                m.head.xRot += 0.25F * w;
                m.head.zRot = tw * 0.6F * w;
            }
            case Poses.HEAVY -> {           // hunched under the weight, arms hanging, dragging steps
                m.body.xRot = lerp(m.body.xRot, 0.45F, w);
                pose(m.rightArm, w, m.rightArm.xRot * 0.3F + 0.35F, 0F, 0.1F);
                pose(m.leftArm, w, m.leftArm.xRot * 0.3F + 0.35F, 0F, -0.1F);
                m.rightLeg.xRot *= lerp(1F, 0.4F, w);
                m.leftLeg.xRot *= lerp(1F, 0.4F, w);
                m.head.xRot += 0.2F * w;
            }
            case Poses.SLIDING -> {         // no grip: arms windmilling for balance
                float flail = Mth.sin(t * 0.9F);
                pose(m.rightArm, w, -1.2F + flail * 0.9F, 0F, 1.1F);
                pose(m.leftArm, w, -1.2F - flail * 0.9F, 0F, -1.1F);
                pose(m.rightLeg, w, 0.3F * flail, 0F, 0.25F);
                pose(m.leftLeg, w, -0.3F * flail, 0F, -0.25F);
                m.body.zRot = flail * 0.15F * w;
            }
            default -> {}
        }
    }

    // ---------------------------------------------------------------- actions

    private static void action(HumanoidModel<?> m, int pose, float t, int dur) {
        float aimX = m.head.xRot - Mth.HALF_PI, aimY = m.head.yRot;
        switch (pose) {
            case Poses.EL_CHARGE -> {       // power gathering in cupped hands at the hip
                float w = w(t, dur, 3F, 2F), sh = Mth.sin(t * 3F) * 0.04F;
                pose(m.rightArm, w, -0.6F + sh, -0.6F, 0.2F);
                pose(m.leftArm, w, -0.6F - sh, 0.6F, -0.2F);
                m.body.yRot = lerp(m.body.yRot, -0.35F, w);
                pose(m.rightLeg, w, 0.3F, 0F, 0.08F);
                pose(m.leftLeg, w, -0.3F, 0F, -0.08F);
            }
            case Poses.EL_THROW -> {        // wind back and hurl overhand
                float w = w(t, dur, 2F, 4F), go = smooth((t - 3F) / 3F);
                pose(m.rightArm, w, lerp(-2.8F, aimX + 0.3F, go), aimY, 0F);
                pose(m.leftArm, w, lerp(-0.4F, 0.3F, go), 0F, -0.3F);
                m.body.yRot = lerp(m.body.yRot, lerp(0.4F, -0.3F, go), w);
                pose(m.rightLeg, w, lerp(0.2F, -0.3F, go), 0F, 0.05F);
                pose(m.leftLeg, w, lerp(-0.3F, 0.35F, go), 0F, -0.05F);
            }
            case Poses.EL_THRUST -> {       // both palms driven forward
                float w = w(t, dur, 2F, 4F), go = smooth((t - 2F) / 2.5F);
                pose(m.rightArm, w, lerp(-0.8F, aimX, go), lerp(-0.6F, aimY - 0.15F, go), 0F);
                pose(m.leftArm, w, lerp(-0.8F, aimX, go), lerp(0.6F, aimY + 0.15F, go), 0F);
                m.body.xRot = lerp(m.body.xRot, 0.15F * go, w);
                pose(m.rightLeg, w, -0.35F * go, 0F, 0.08F);
                pose(m.leftLeg, w, 0.4F * go, 0F, -0.08F);
            }
            case Poses.EL_STOMP -> {        // knee up, stomp, fists down
                float w = w(t, dur, 2F, 4F), lift = smooth(t / 5F), slam = smooth((t - 5F) / 2F);
                pose(m.rightLeg, w, lerp(lerp(0F, -1.5F, lift), 0.1F, slam), 0F, 0.12F);
                pose(m.leftLeg, w, 0.2F, 0F, -0.1F);
                pose(m.rightArm, w, lerp(-0.4F, 0.35F, slam), 0F, lerp(0.8F, 0.4F, slam));
                pose(m.leftArm, w, lerp(-0.4F, 0.35F, slam), 0F, lerp(-0.8F, -0.4F, slam));
                m.body.xRot = lerp(m.body.xRot, 0.3F * slam, w);
            }
            case Poses.EL_RAISE -> {        // palms raised to call it up
                float w = w(t, dur, 3F, 4F), up = smooth(t / 6F);
                pose(m.rightArm, w, lerp(-0.5F, -2.6F, up), 0F, 0.5F);
                pose(m.leftArm, w, lerp(-0.5F, -2.6F, up), 0F, -0.5F);
                m.head.xRot -= 0.35F * up * w;
            }
            case Poses.KA_CAST -> {         // a hand over one eye, the other pointing - "KA!"
                float w = w(t, dur, 3F, 4F), point = smooth((t - 6F) / 3F);
                pose(m.leftArm, w, -2.0F, 0.75F, 0.15F);
                pose(m.rightArm, w, lerp(-0.4F, aimX + 0.05F, point), lerp(0F, aimY, point), 0F);
                m.head.xRot += 0.15F * w;
            }
            case Poses.KA_BURNOUT -> {      // drops to a knee clutching their head
                float w = w(t, dur, 4F, 8F);
                pose(m.rightArm, w, -2.6F, -0.3F, -0.5F);
                pose(m.leftArm, w, -2.6F, 0.3F, 0.5F);
                pose(m.rightLeg, w, -1.4F, 0F, 0.1F);
                pose(m.leftLeg, w, 0.2F, 0F, -0.1F);
                m.body.xRot = lerp(m.body.xRot, 0.5F, w);
                m.head.xRot += 0.6F * w;
            }
            case Poses.HOLD_JOLT -> {       // hands spring open
                float w = w(t, dur, 1F, 6F);
                pose(m.rightArm, w, -0.9F, 0F, 1.0F);
                pose(m.leftArm, w, -0.9F, 0F, -1.0F);
                m.head.xRot -= 0.25F * w;
            }
            case Poses.CLASH_WIN -> {       // fist up
                float w = w(t, dur, 3F, 6F);
                pose(m.rightArm, w, -2.9F, 0F, 0.2F);
                m.head.xRot -= 0.3F * w;
            }
            case Poses.CLASH_LOSE -> {      // thrown back, arms flung
                float w = w(t, dur, 1F, 8F);
                pose(m.rightArm, w, -1.6F, 0F, 1.3F);
                pose(m.leftArm, w, -1.6F, 0F, -1.3F);
                m.body.xRot = lerp(m.body.xRot, -0.4F, w);
                m.head.xRot -= 0.5F * w;
                pose(m.rightLeg, w, -0.6F, 0F, 0.2F);
                pose(m.leftLeg, w, -0.2F, 0F, -0.2F);
            }
            case Poses.MIRROR -> {          // hands framing his face, then opening like a mirror
                float w = w(t, dur, 1.5F, 2F), open = smooth((t - 3F) / 2F);
                pose(m.rightArm, w, -2.2F, lerp(-0.6F, -0.2F, open), lerp(0.2F, 0.9F, open));
                pose(m.leftArm, w, -2.2F, lerp(0.6F, 0.2F, open), lerp(-0.2F, -0.9F, open));
            }
            case Poses.KA_BREAK -> {        // tensed and curled... then everything flung out at once
                float w = w(t, dur, 1F, 6F), burst = smooth((t - 6F) / 2F), shake = t < 6F ? Mth.sin(t * 4F) * 0.07F : 0F;
                pose(m.rightArm, w, lerp(-1.4F + shake, -0.2F, burst), lerp(-0.7F, 0F, burst), lerp(-0.3F, 1.9F, burst));
                pose(m.leftArm, w, lerp(-1.4F - shake, -0.2F, burst), lerp(0.7F, 0F, burst), lerp(0.3F, -1.9F, burst));
                pose(m.rightLeg, w, lerp(-0.4F, 0F, burst), 0F, lerp(0.05F, 0.3F, burst));
                pose(m.leftLeg, w, lerp(0.3F, 0F, burst), 0F, lerp(-0.05F, -0.3F, burst));
                m.body.xRot = lerp(m.body.xRot, lerp(0.45F, -0.15F, burst), w);
                m.head.xRot += lerp(0.4F, -0.5F, burst) * w;
            }
            case Poses.CHOP -> {            // two-handed axe swing from the side
                float w = w(t, dur, 1F, 3F), sw = smooth(t / (dur * 0.6F));
                pose(m.rightArm, w, lerp(-1.6F, -0.9F, sw), lerp(1.0F, -0.4F, sw), 0F);
                pose(m.leftArm, w, lerp(-1.5F, -0.9F, sw), lerp(1.2F, -0.2F, sw), 0F);
                m.body.yRot = lerp(m.body.yRot, lerp(0.6F, -0.3F, sw), w);
            }
            case Poses.MINE, Poses.SKY_MINE -> {   // pickaxe raised high and brought down
                float w = w(t, dur, 1F, 3F), sw = smooth(t / (dur * 0.6F));
                pose(m.rightArm, w, lerp(-2.9F, -0.6F, sw), 0F, 0.1F);
                if (pose == Poses.MINE) pose(m.leftArm, w, lerp(-2.6F, -0.7F, sw), 0F, -0.1F);
                m.body.xRot = lerp(m.body.xRot, 0.3F * sw, w);
            }
            case Poses.PLACE -> {           // crouched, reaching down to set a block
                float w = w(t, dur, 2F, 3F);
                pose(m.rightArm, w, -0.9F, -0.1F, 0F);
                m.body.xRot = lerp(m.body.xRot, 0.5F, w);
                pose(m.rightLeg, w, -0.5F, 0F, 0.05F);
                pose(m.leftLeg, w, 0.3F, 0F, -0.05F);
                m.head.xRot += 0.4F * w;
            }
            case Poses.WIPE -> {            // wipes the sweat off their forehead
                float w = w(t, dur, 4F, 5F), rub = Mth.sin(t * 0.8F) * 0.25F;
                pose(m.rightArm, w, -2.3F, -0.6F + rub, 0.3F);
                pose(m.leftArm, w, 0.1F, 0F, -0.15F);
                m.head.xRot -= 0.25F * w;
            }
            case Poses.PLANT -> {           // kneels, hand pressed to the soil
                float w = w(t, dur, 4F, 5F);
                pose(m.rightLeg, w, -1.4F, 0F, 0.1F);
                pose(m.leftLeg, w, 0.3F, 0F, -0.1F);
                pose(m.rightArm, w, -0.6F, 0F, 0F);
                m.body.xRot = lerp(m.body.xRot, 0.6F, w);
                m.head.xRot += 0.5F * w;
            }
            case Poses.STUNNED -> {         // dazed: head rolling in circles, arms hanging limp, swaying on bent knees
                float w = w(t, dur, 2F, 6F), a = t * 0.45F;
                m.head.xRot = lerp(m.head.xRot, 0.25F + Mth.sin(a) * 0.18F, w);
                m.head.zRot = lerp(m.head.zRot, Mth.cos(a) * 0.25F, w);
                m.body.zRot = lerp(m.body.zRot, Mth.sin(a * 0.7F) * 0.12F, w);
                m.body.xRot = lerp(m.body.xRot, 0.18F, w);
                pose(m.rightArm, w, 0.25F + Mth.sin(a + 1F) * 0.12F, 0F, 0.12F + Mth.cos(a) * 0.05F);
                pose(m.leftArm, w, 0.25F + Mth.sin(a) * 0.12F, 0F, -0.12F - Mth.cos(a) * 0.05F);
                pose(m.rightLeg, w, -0.15F, 0F, 0.08F + Mth.sin(a * 0.7F) * 0.05F);
                pose(m.leftLeg, w, 0.1F, 0F, -0.08F + Mth.sin(a * 0.7F) * 0.05F);
            }
            case Poses.XENO_MIKIKO -> {     // Xeno Blast (from the art): hands cupped back at the hip, then both thrust out in a deep lunge
                float rel = dur > 100 ? com.xenowarriors.skill.XenoBlast.RELEASE - 2F : 14F;   // (the 20 s cutscene charges for 10 s)
                float w = w(t, dur, 3F, 5F), go = smooth((t - rel) / 3F), sh = (1F - go) * Mth.sin(t * 3.1F) * (dur > 100 ? 0.09F : 0.05F);
                // charge: hands cupped behind at the hip, body wound back
                float rx = lerp(0.35F + sh, aimX, go), lx = lerp(0.25F - sh, aimX + 0.08F, go);
                pose(m.rightArm, w, rx, lerp(-0.9F, aimY - 0.12F, go), lerp(0.25F, 0.05F, go));
                pose(m.leftArm, w, lx, lerp(-0.4F, aimY + 0.12F, go), lerp(0.1F, -0.05F, go));
                m.body.yRot = lerp(m.body.yRot, lerp(-0.55F, 0.15F, go), w);
                m.body.xRot = lerp(m.body.xRot, lerp(0.05F, 0.28F, go), w);
                pose(m.leftLeg, w, -0.75F, 0F, -0.12F);          // front leg forward, knee bent
                pose(m.rightLeg, w, 0.7F, 0F, 0.15F);            // back leg stretched behind
                m.head.xRot += 0.12F * w;
            }
            case Poses.XENO_RHALIX -> {     // Xeno Blast (from the art): side-on stance, pointing finger out, other hand steadying the wrist
                float w = w(t, dur, 3F, 5F), sh = Mth.sin(t * 2.3F) * 0.03F;
                pose(m.rightArm, w, aimX + sh, aimY - 0.05F, 0F);
                pose(m.leftArm, w, aimX + 0.22F - sh, aimY - 0.55F, 0.1F);
                m.body.yRot = lerp(m.body.yRot, 0.45F, w);
                pose(m.rightLeg, w, -0.45F, 0F, 0.22F);
                pose(m.leftLeg, w, 0.45F, 0F, -0.22F);
                m.head.yRot -= 0.3F * w;
            }
            case Poses.SHAPE -> {           // hands held out, shaping crystal into place
                float w = w(t, dur, 2F, 3F), circ = Mth.sin(t * 0.7F) * 0.3F;
                pose(m.rightArm, w, -1.4F + circ, -0.35F, 0F);
                pose(m.leftArm, w, -1.4F - circ, 0.35F, 0F);
                m.head.xRot += 0.2F * w;
            }
            default -> {}
        }
    }
}
