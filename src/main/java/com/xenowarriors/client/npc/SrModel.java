package com.xenowarriors.client.npc;

import com.xenowarriors.sr.SrMemberEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The named Sky Rebellion members' animations, on top of the normal walk:
 * <ul>
 * <li>idle: each their own (Senju holds his sword low, Logan hands on hips, Kill hands behind his back,
 *     Keita slouches and yawns, Maddie hands folded, Persephone hugs her arm, Ava arms crossed) and breathing;</li>
 * <li>combat stance when they're after someone, and their own attack swings (Senju's two-handed overhead cut, Kill and
 *     Keita's fast slashes, Logan's thrust, Maddie's backhand, the demons' clawing);</li>
 * <li>their special moves (Senju's spinning sweep, Logan's Redirect palm, the dash slash, Maddie's glare, the demons'
 *     fire burst), flying with the wings out, and the demon waking up.</li>
 * </ul>
 * The UI test can drive it with a fixed clock ({@link #force}).
 */
public class SrModel extends PlayerModel<SrMemberEntity> {
    /** UI test: forced state ("idle", "walk", "combat", "attack", "special", "fly", "demon", "redirect") and clock; null = normal. */
    public static String force;
    public static float clock;

    /** Whole-body forward lean in degrees (the renderer tips the entire model, so the hips stay attached). */
    public static float lean(SrMemberEntity e, float pt) {
        if (!e.isNamed()) return 0;
        String k = e.key();
        boolean f = force != null;
        float demon = f ? (force.equals("demon") ? clock % 40F : -1) : e.demonAge(pt);
        if (demon >= 0 && demon < 40) { float p = demon / 40F; return p < 0.5F ? 28F * Math.min(1F, p / 0.2F) : 28F * Math.max(0F, 1F - (p - 0.5F) / 0.15F) - 8F * Math.min(1F, (p - 0.5F) / 0.2F); }
        float sp = f ? (force.equals("special") || force.equals("redirect") ? clock % 30F : -1) : e.specialAge(pt);
        if (sp >= 0 && sp < 30) {
            float p = sp / 30F;
            if (k.equals("kill") || k.equals("keita")) return p < 0.3F ? -6F * p / 0.3F : 26F * Math.max(0F, 1F - Math.max(0F, p - 0.7F) / 0.3F);
            if (k.equals("persephone") || k.equals("ava")) return p < 0.4F ? 25F * p / 0.4F : 25F - 35F * Math.min(1F, (p - 0.4F) / 0.15F);
        }
        boolean fight = f ? (force.equals("combat") || force.equals("attack")) : e.isAggressive();
        if (fight && !k.equals("senju") && !k.equals("maddie")) return 10F;
        if (!fight && k.equals("keita")) return 6F;           // lazy slouch
        return 0F;
    }

    /** Senju's sweep turns his whole body around (0..1 of a turn), for the renderer. */
    public static float spin(SrMemberEntity e, float pt) {
        if (!"senju".equals(e.key())) return 0;
        float t = force != null ? (force.equals("special") ? clock % 30F : -1) : e.specialAge(pt);
        if (t < 0 || t >= 30) return 0;
        float p = t / 30F;
        return p < 0.3F ? 0 : Math.min(1F, (p - 0.3F) / 0.55F);
    }

    public SrModel(ModelPart root) { super(root, false); }

    private static final float PI = Mth.PI;

    @Override
    public void setupAnim(SrMemberEntity e, float limb, float limbAmt, float age, float yaw, float pitch) {
        String st = force;
        if (st != null) {
            age = clock;
            if (st.equals("walk")) { limb = clock * 0.6F; limbAmt = 0.9F; } else { limb = 0; limbAmt = 0; }
            attackTime = st.equals("attack") ? (clock % 14F) / 14F : 0F;
            yaw = 0; pitch = 0;
        }
        super.setupAnim(e, limb, limbAmt, age, yaw, pitch);
        if (!e.isNamed()) { sync(); return; }
        String k = e.key();
        boolean moving = limbAmt > 0.15F;
        boolean fight = st != null ? (st.equals("combat") || st.equals("attack")) : e.isAggressive();
        float special = st != null ? (st.equals("special") || st.equals("redirect") ? (clock % 30F) : -1) : e.specialAge(Mth.frac(age));
        float demonT = st != null ? (st.equals("demon") ? clock % 40F : -1) : e.demonAge(Mth.frac(age));
        boolean flying = st != null ? st.equals("fly") : e.wings();
        boolean redirect = st != null ? st.equals("redirect") : e.redirecting();
        float breath = Mth.sin(age * 0.08F);

        // breathing for everyone
        head.xRot += breath * 0.02F;

        if (flying) fly(age);
        else if (demonT >= 0 && demonT < 40) demonWake(k, demonT, age);
        else if (special >= 0 && special < 30) special(k, special, age, redirect);
        else if (fight) { stance(k, age, moving); swing(k); }
        else if (!moving) idle(k, age, breath);
        else walkStyle(k, age, limb, limbAmt);
        sync();
    }

    /** Copy the main parts onto the outer layers (sleeves, pants, jacket, hat). */
    private void sync() {
        // the arms stay on the shoulders when the body twists (like vanilla's attack swing does)
        float by = body.yRot;
        if (by != 0F) {
            rightArm.z = Mth.sin(by) * 5F; rightArm.x = -Mth.cos(by) * 5F;
            leftArm.z = -Mth.sin(by) * 5F; leftArm.x = Mth.cos(by) * 5F;
            rightArm.yRot += by; leftArm.yRot += by;
        }
        hat.copyFrom(head); jacket.copyFrom(body);
        leftSleeve.copyFrom(leftArm); rightSleeve.copyFrom(rightArm);
        leftPants.copyFrom(leftLeg); rightPants.copyFrom(rightLeg);
    }

    private static void set(ModelPart p, float x, float y, float z) { p.xRot = x; p.yRot = y; p.zRot = z; }

    private void idle(String k, float age, float breath) {
        float t = age * 0.05F;
        switch (k) {
            case "senju" -> {                    // sword held low at his side, the other hand loose, calm - watching
                set(rightArm, -0.3F + breath * 0.03F, -0.1F, 0.12F);
                set(leftArm, 0.05F, 0, -0.12F);
                head.yRot += Mth.sin(t * 0.7F) * 0.25F;
            }
            case "logan" -> {                    // hands on hips, grinning, bouncing on his heels
                set(rightArm, -0.3F, 0, 0.95F); set(leftArm, -0.3F, 0, -0.95F);
            }
            case "kill" -> {                     // hands behind his back, looking around
                set(rightArm, 0.55F, 0, 0.35F); set(leftArm, 0.55F, 0, -0.35F);
                head.yRot += Mth.sin(t * 0.9F) * 0.6F;
            }
            case "keita" -> {                    // slouched; every so often a big lazy stretch and yawn
                float c = (age % 120F) / 120F; head.xRot += 0.25F;
                set(rightArm, 0.1F, 0, 0.1F); set(leftArm, 0.1F, 0, -0.1F);
                if (c > 0.7F) {
                    float s = Mth.sin((c - 0.7F) / 0.3F * PI);
                    set(rightArm, -2.9F * s, 0, 0.3F * s); set(leftArm, -2.9F * s, 0, -0.3F * s);
                    head.xRot -= 0.6F * s;
                }
            }
            case "maddie" -> {                   // hands folded in front, head tilted, a slow sway
                set(rightArm, -0.6F, -0.35F, 0.1F); set(leftArm, -0.6F, 0.35F, -0.1F);
            }
            case "persephone" -> {               // hugging her own arm, looking down and away
                set(rightArm, -0.9F, -0.7F, 0.25F); set(leftArm, 0.1F, 0, -0.1F);
                head.xRot += 0.3F; head.yRot += -0.35F + Mth.sin(t) * 0.1F;
            }
            default -> {                         // Ava: arms crossed, tapping a foot
                set(rightArm, -1.25F, -0.75F, 0.15F); set(leftArm, -1.15F, 0.75F, -0.15F);
                leftLeg.xRot = -Math.max(0, Mth.sin(age * 0.4F)) * 0.25F;
            }
        }
    }

    /** A bit of personality on the walk. */
    private void walkStyle(String k, float age, float limb, float amt) {
        switch (k) {
            case "senju" -> { rightArm.xRot = rightArm.xRot * 0.4F - 0.25F; body.yRot = Mth.cos(limb * 0.6662F) * amt * 0.1F; }   // a measured stride, sword steady
            case "logan" -> { }   // a bouncy swagger
            case "kill" -> { set(rightArm, 0.55F, 0, 0.35F); set(leftArm, 0.55F, 0, -0.35F); }
            case "keita" -> { head.xRot += 0.2F; rightArm.xRot *= 0.3F; leftArm.xRot *= 0.3F; }   // shuffling
            case "maddie" -> { rightArm.xRot *= 0.4F; leftArm.xRot *= 0.4F; }
            default -> {}
        }
    }

    private void stance(String k, float age, boolean moving) {
        float b = Mth.sin(age * 0.2F) * 0.05F;
        switch (k) {
            case "senju" -> {                    // two-handed guard, blade forward
                set(rightArm, -1.1F + b, -0.35F, 0); set(leftArm, -1.0F + b, 0.55F, 0);
                body.yRot = -0.25F;
            }
            case "logan" -> {                    // crouched, sword hand back, free hand forward
                set(rightArm, -0.2F + b, 0.4F, 0.5F); set(leftArm, -1.2F, 0.2F, 0);
            }
            case "kill", "keita" -> {            // low one-handed guard, leaning in
                set(rightArm, -0.6F + b, 0.1F, 0.25F); set(leftArm, -0.4F, 0, -0.5F); head.xRot -= 0.25F;
            }
            case "maddie" -> {                   // unnervingly still: one hand slightly raised, head tilted
                set(rightArm, -0.5F, 0, 0.3F); set(leftArm, 0.1F, 0, -0.1F);
            }
            default -> {                         // demons: claws up
                set(rightArm, -1.4F + b, -0.2F, 0.3F); set(leftArm, -1.4F - b, 0.2F, -0.3F);
            }
        }
        if (!moving) { rightLeg.xRot = -0.25F; leftLeg.xRot = 0.25F; rightLeg.zRot = 0.08F; leftLeg.zRot = -0.08F; }
    }

    private void swing(String k) {
        if (attackTime <= 0) return;
        float a = attackTime, s = Mth.sin(a * PI), up = a < 0.4F ? a / 0.4F : 1F - (a - 0.4F) / 0.6F;
        switch (k) {
            case "senju" -> {                    // two-handed overhead cut
                float x = a < 0.35F ? -1.1F - 1.9F * (a / 0.35F) : -3.0F + 3.3F * Math.min(1F, (a - 0.35F) / 0.3F);
                rightArm.xRot = x; leftArm.xRot = x; rightArm.yRot = -0.1F; leftArm.yRot = 0.3F;
            }
            case "kill", "keita" -> {            // fast flat slash across
                rightArm.xRot = -1.5F; rightArm.yRot = 0.9F - 2.2F * a; body.yRot = 0.5F - 1.0F * a;
            }
            case "logan" -> {                    // thrust
                rightArm.xRot = -0.3F - 1.3F * s; rightArm.yRot = 0.4F * (1 - s); body.yRot = -0.4F * s;
            }
            case "maddie" -> {                   // backhand
                rightArm.xRot = -1.4F; rightArm.yRot = -1.0F + 2.2F * a; body.yRot = -0.6F + 1.2F * a;
            }
            default -> {                         // demons: alternating claw rakes
                rightArm.xRot = -2.6F + 2.4F * a; leftArm.xRot = -0.6F - 2.0F * up;
            }
        }
    }

    private void special(String k, float t, float age, boolean redirect) {
        float p = t / 30F, s = Mth.sin(p * PI);
        switch (k) {
            case "senju" -> {                    // spinning sweep: wind up, then the whole body turns through the cut
                float wind = Math.min(1F, p / 0.3F), turn = p < 0.3F ? 0 : (p - 0.3F) / 0.7F;
                body.yRot = 0.4F * wind * (1F - turn);          // (the whole model spins in the renderer)
                set(rightArm, -1.5F, -1.1F * wind + 2.4F * turn, 0); set(leftArm, -1.4F, 0.6F, 0); rightLeg.xRot = -0.4F * s; leftLeg.xRot = 0.4F * s;
            }
            case "logan" -> {                    // Redirect: palm thrust out, the other hand pulling back - then the price, a stagger
                if (p < 0.7F) {
                    float q = Math.min(1F, p / 0.2F);
                    set(leftArm, -1.55F * q, 0.15F, -0.1F); set(rightArm, -0.2F, 0.6F * q, 0.6F * q);
                    body.yRot = 0.35F * q; head.xRot -= 0.1F;
                    leftArm.zRot += Mth.sin(age * 1.7F) * 0.05F;        // the strain
                } else {
                    float q = (p - 0.7F) / 0.3F;
                    set(rightArm, -0.4F, 0, 0.6F); set(leftArm, -0.4F, 0, -0.6F);
                }
            }
            case "kill", "keita" -> {            // dash slash: crouch, lunge forward, follow through
                float lunge = p < 0.3F ? -p / 0.3F * 0.3F : Math.min(1F, (p - 0.3F) / 0.2F); head.xRot -= 0.5F * Math.max(0, lunge);
                set(rightArm, -1.6F * Math.max(0, lunge), 1.2F - 2.6F * Math.max(0, (p - 0.3F) / 0.7F), 0);
                set(leftArm, 0.8F * Math.max(0, lunge), 0, -0.3F);
                rightLeg.xRot = -1.0F * Math.max(0, lunge); leftLeg.xRot = 0.7F * Math.max(0, lunge);
            }
            case "maddie" -> {                   // the red eye: still, head forward, one hand lifting her hair from her eye
                head.xRot -= 0.15F;
                set(rightArm, -2.4F * Math.min(1F, p / 0.3F), -0.5F, 0.2F); set(leftArm, 0.1F, 0, -0.15F);
            }
            default -> {                         // demons' fire burst: hunch, then fling the arms wide
                if (p < 0.4F) { float q = p / 0.4F; set(rightArm, -0.4F * q, 0, 0.3F * q); set(leftArm, -0.4F * q, 0, -0.3F * q); head.xRot += 0.4F * q; }
                else { float q = Math.min(1F, (p - 0.4F) / 0.15F); set(rightArm, -1.2F * q, 0, 1.6F * q); set(leftArm, -1.2F * q, 0, -1.6F * q); head.xRot -= 0.5F * q; }
            }
        }
    }

    /** Wings out: arms swept back, legs trailing, a little bob. */
    private void fly(float age) {
        float f = Mth.sin(age * 0.3F);
        set(rightArm, 0.5F + f * 0.1F, 0, 0.35F); set(leftArm, 0.5F + f * 0.1F, 0, -0.35F);
        rightLeg.xRot = 0.3F + f * 0.08F; leftLeg.xRot = 0.15F - f * 0.08F;
        head.xRot -= 0.3F;
    }

    /** The demon waking: doubled over clutching the head, then rising with arms spread. */
    private void demonWake(String k, float t, float age) {
        float p = t / 40F;
        if (p < 0.5F) {
            float q = Math.min(1F, p / 0.2F), shake = Mth.sin(age * 2.5F) * 0.06F; head.xRot += 0.6F * q + shake;
            set(rightArm, -2.6F * q, -0.4F, 0.4F + shake); set(leftArm, -2.6F * q, 0.4F, -0.4F - shake);
            rightLeg.xRot = -0.3F * q; leftLeg.xRot = 0.3F * q;
        } else {
            float q = Math.min(1F, (p - 0.5F) / 0.2F); head.xRot -= 0.5F * q;
            set(rightArm, -0.8F * q, 0, 1.4F * q); set(leftArm, -0.8F * q, 0, -1.4F * q);
        }
    }
}
