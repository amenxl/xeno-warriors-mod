package com.xenowarriors.client.mikiko;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import com.xenowarriors.client.LimbBend;
import com.xenowarriors.client.Surroundings;
import com.xenowarriors.mikiko.Hekublade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Mikiko with enemies around: on guard.
 * <ul>
 * <li>Bare hands - a wide, low fighting stance: right fist raised by his face, left hand open and forward at his
 *     waist, bouncing lightly on bent knees.</li>
 * <li>Hekublade in hand - a low lunge: front knee bent, back leg stretched out, the blade held low and forward in his
 *     right hand, left arm out for balance.</li>
 * </ul>
 * Either way he glances left and right, and every so often shows he's ready for whoever comes close (a quick jab,
 * or a flick of the blade).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CombatStance {
    public static final int NONE = 0, FISTS = 1, SWORD = 2;
    private static final int FLOURISH_EVERY = 170, FLOURISH_T = 22;
    private static final Map<Integer, float[]> STATE = new HashMap<>();   // id -> {weight, previous weight, ticks on guard, style}

    private CombatStance() {}

    /** Which stance he wants right now (NONE if he shouldn't be on guard). */
    public static int wanted(Player p) {
        if (ClientCharacterData.get(p) != CharacterType.MIKIKO) return NONE;
        if (!p.onGround() || p.isSprinting() || p.isCrouching() || p.isPassenger() || p.isSleeping() || p.isSwimming()
                || p.isFallFlying() || p.isUsingItem() || p.isInWater()) return NONE;
        if (com.xenowarriors.client.ClientSkillState.currentAnim(p) != null || com.xenowarriors.client.compat.DmzCompat.charging(p)
                || com.xenowarriors.client.compat.DmzCompat.blocking(p) || com.xenowarriors.client.SurfPose.surfing(p)) return NONE;
        if (p instanceof AbstractClientPlayer cp && com.xenowarriors.client.FlightTracker.isFlying(cp)) return NONE;
        if (Surroundings.of(p) != Surroundings.ENEMIES) return NONE;
        if (p.getMainHandItem().isEmpty()) return FISTS;
        if (Hekublade.isBlade(p.getMainHandItem())) return SWORD;
        return NONE;
    }

    public static boolean active(Player p) {
        float[] s = STATE.get(p.getId());
        return s != null && s[0] > 0F;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) return;
        for (AbstractClientPlayer p : mc.level.players()) {
            int want = wanted(p);
            float[] s = STATE.get(p.getId());
            if (s == null) { if (want == NONE) continue; STATE.put(p.getId(), s = new float[]{0F, 0F, 0F, want}); }
            s[1] = s[0];
            if (want != NONE && want != (int) s[3] && s[0] > 0F) s[0] = Math.max(0F, s[0] - 0.25F);   // fade out, then switch
            else {
                if (want != NONE) s[3] = want;
                s[0] = Mth.clamp(s[0] + (want != NONE ? 0.15F : -0.12F), 0F, 1F);
            }
            s[2] = want != NONE ? s[2] + 1 : 0;
        }
        STATE.entrySet().removeIf(e -> (e.getValue()[0] <= 0F && e.getValue()[1] <= 0F) || mc.level.getEntity(e.getKey()) == null);
    }

    private static float sm(float x) { x = Mth.clamp(x, 0F, 1F); return x * x * (3 - 2 * x); }

    public static void apply(HumanoidModel<?> m, Player p, float age) {
        float[] s = STATE.get(p.getId());
        if (s == null || LimbBend.firstPerson) return;                       // (not on your own first-person hand)
        float pt = Minecraft.getInstance().getFrameTime();
        float e = sm(Mth.lerp(pt, s[1], s[0]));
        if (e <= 0F) return;
        boolean sword = (int) s[3] == SWORD;
        double speed = Math.sqrt(Math.pow(p.getX() - p.xo, 2) + Math.pow(p.getZ() - p.zo, 2));
        float legs = e * (1F - sm((float) speed / 0.1F));                       // walking: his legs walk, the guard stays up
        boolean attacking = m.attackTime > 0F || MikikoAnimations.attacking(p);
        float arms = attacking ? 0F : e;
        float t = s[2] + pt;

        // glancing left and right (held a moment each way), on top of where he's looking
        float[] looks = {0F, -0.6F, -0.2F, 0.55F, 0.1F, -0.45F, 0.65F};
        int seg = (int) (t / 45F);
        float from = looks[Math.floorMod(seg - 1, looks.length)], to = looks[Math.floorMod(seg, looks.length)];
        float glance = Mth.lerp(sm((t % 45F) / 12F), from, to);
        // now and then: ready for them
        float ft = (t + p.getId() * 13) % FLOURISH_EVERY;
        float fl = t > 60 && ft < FLOURISH_T ? Mth.sin(ft / FLOURISH_T * (float) Math.PI) : 0F;
        glance *= 1F - fl;

        if (!sword) fists(m, p, age, e, legs, arms, fl);
        else blade(m, p, age, e, legs, arms, fl);
        m.head.yRot += glance * e;
        m.hat.copyFrom(m.head);
    }

    private static void fists(HumanoidModel<?> m, Player p, float age, float e, float legs, float arms, float fl) {
        float bounce = Mth.sin(age * 0.3F);
        float sink = (1.5F + bounce * 0.35F) * legs;
        // legs: wide and low, knees bent, feet turned out
        m.rightLeg.xRot = Mth.lerp(legs, m.rightLeg.xRot, -0.22F);
        m.leftLeg.xRot = Mth.lerp(legs, m.leftLeg.xRot, -0.12F);
        m.rightLeg.yRot = Mth.lerp(legs, m.rightLeg.yRot, 0.2F);
        m.leftLeg.yRot = Mth.lerp(legs, m.leftLeg.yRot, -0.2F);
        m.rightLeg.zRot = Mth.lerp(legs, m.rightLeg.zRot, 0.32F);
        m.leftLeg.zRot = Mth.lerp(legs, m.leftLeg.zRot, -0.32F);
        LimbBend.extraKnees(p, (0.6F + bounce * 0.08F) * legs, (0.55F + bounce * 0.08F) * legs);
        sinkBody(m, sink);
        m.body.xRot = Mth.lerp(e, m.body.xRot, 0.1F);
        m.head.xRot = Mth.lerp(e, m.head.xRot, -0.05F);
        if (arms <= 0F) return;
        // right fist raised by his face (a jab forward when he shows he's ready); left hand open, forward at the waist
        float jab = fl;
        m.rightArm.xRot = Mth.lerp(arms, m.rightArm.xRot, Mth.lerp(jab, -1.25F, -1.6F) + bounce * 0.04F);
        m.rightArm.yRot = Mth.lerp(arms, m.rightArm.yRot, Mth.lerp(jab, -0.35F, -0.05F));
        m.rightArm.zRot = Mth.lerp(arms, m.rightArm.zRot, Mth.lerp(jab, 0.35F, 0.05F));
        m.leftArm.xRot = Mth.lerp(arms, m.leftArm.xRot, -0.75F - bounce * 0.04F);
        m.leftArm.yRot = Mth.lerp(arms, m.leftArm.yRot, 0.3F);
        m.leftArm.zRot = Mth.lerp(arms, m.leftArm.zRot, -0.3F);
        LimbBend.extraElbows(p, Mth.lerp(jab, -1.7F, -0.2F) * arms, -0.65F * arms);
    }

    private static void blade(HumanoidModel<?> m, Player p, float age, float e, float legs, float arms, float fl) {
        float breathe = Mth.sin(age * 0.12F);
        float sink = (2.4F + breathe * 0.2F) * legs;
        // a low lunge: left knee bent out front, right leg stretched out behind
        m.leftLeg.xRot = Mth.lerp(legs, m.leftLeg.xRot, -0.75F);
        m.leftLeg.zRot = Mth.lerp(legs, m.leftLeg.zRot, -0.15F);
        m.leftLeg.yRot = Mth.lerp(legs, m.leftLeg.yRot, -0.1F);
        m.rightLeg.xRot = Mth.lerp(legs, m.rightLeg.xRot, 0.6F);
        m.rightLeg.zRot = Mth.lerp(legs, m.rightLeg.zRot, 0.18F);
        m.rightLeg.yRot = Mth.lerp(legs, m.rightLeg.yRot, 0.15F);
        LimbBend.extraKnees(p, 0.1F * legs, 1.05F * legs);
        sinkBody(m, sink);
        // leaning in, right shoulder drawn back a little, eyes forward
        float twist = 0.25F * e;
        m.body.xRot = Mth.lerp(e, m.body.xRot, 0.32F + breathe * 0.02F);
        m.body.yRot = Mth.lerp(e, m.body.yRot, twist);
        m.head.xRot = Mth.lerp(e, m.head.xRot, -0.25F);
        if (arms <= 0F) return;
        float bs = Mth.sin(m.body.yRot);
        float bc = Mth.cos(m.body.yRot);
        m.rightArm.z = Mth.lerp(arms, m.rightArm.z, bs * 5F);
        m.rightArm.x = Mth.lerp(arms, m.rightArm.x, -bc * 5F);
        m.leftArm.z = Mth.lerp(arms, m.leftArm.z, -bs * 5F);
        m.leftArm.x = Mth.lerp(arms, m.leftArm.x, bc * 5F);
        // the blade held low and forward, angled across him (raised and pointed at them when he shows he's ready)
        m.rightArm.xRot = Mth.lerp(arms, m.rightArm.xRot, Mth.lerp(fl, -0.55F, -1.45F) + breathe * 0.03F);
        m.rightArm.yRot = Mth.lerp(arms, m.rightArm.yRot, Mth.lerp(fl, 0.4F, 0.1F));
        m.rightArm.zRot = Mth.lerp(arms, m.rightArm.zRot, -0.05F);
        // left arm out behind / to the side for balance
        m.leftArm.xRot = Mth.lerp(arms, m.leftArm.xRot, 0.35F - breathe * 0.03F);
        m.leftArm.yRot = Mth.lerp(arms, m.leftArm.yRot, -0.2F);
        m.leftArm.zRot = Mth.lerp(arms, m.leftArm.zRot, -0.85F);
        LimbBend.extraElbows(p, -0.35F * arms, -0.3F * arms);
    }

    /** The whole upper body drops into the stance so hips and legs stay joined. */
    private static void sinkBody(HumanoidModel<?> m, float sink) {
        m.rightLeg.y += sink * 0.5F;
        m.leftLeg.y += sink * 0.5F;
        m.body.y += sink;
        m.head.y += sink;
        m.rightArm.y += sink;
        m.leftArm.y += sink;
    }
}
