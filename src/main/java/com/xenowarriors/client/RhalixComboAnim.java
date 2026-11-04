package com.xenowarriors.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.combo.ComboClient;
import com.xenowarriors.combat.ComboStyle;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rhalix's own ground combo ({@link ComboStyle#RHALIX}), hand-keyed on the player model after the reference video:
 * Shadow Rush (a low dash in, skidding to a stop, a rising claw), Low Lunge, Crescent Slash, Crow Wing (arm thrown up),
 * Gravity Whirl (a full spin, arm out), Reaping Grab (a low reach, then hauling them up), Raven Launcher (thrown up
 * overhead) and Gravity Burst (a downward diagonal slash, then a palm held out at the sphere).
 *
 * <p>Each move is a set of keyframes in its own progress (0..1, the hit lands at {@link ComboStyle#STRIKE}): arms,
 * legs, torso twist, head, knee and elbow bends, and whole-body motion (lift, forward, spin, lean, roll). Legs are
 * keyed in the world's frame (they stay planted however far he leans) and he's lowered automatically so his feet stay
 * on the ground in the deep stances. Moves blend straight from one into the next, and back to normal after the string.
 * In the air his old kick combo plays instead (RhalixCombatAnim).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RhalixComboAnim {
    // pose array layout
    static final int RA = 0, LA = 3, RL = 6, LL = 9, TW = 12, HEAD = 14, KR = 15, KL = 16, ER = 17, EL = 18,
            LIFT = 19, FWD = 20, SPIN = 21, LEAN = 22, ROLL = 23, N = 24;
    private static final float[] READY = new float[N];
    static {
        set(READY, RA, -0.75F, 0.25F, 0.12F);
        set(READY, LA, -1.0F, 0.35F, -0.1F);
        set(READY, RL, -0.25F, 0F, 0.07F);
        set(READY, LL, 0.3F, 0F, -0.07F);
        READY[TW] = 0.15F; READY[KR] = 0.35F; READY[KL] = 0.4F; READY[ER] = -1.1F; READY[EL] = -1.3F; READY[LEAN] = 4F;
    }

    /** The move names, hit by hit (for logs and the test sheets). */
    public static final String[] NAMES = {"Shadow Rush", "Low Lunge", "Crescent Slash", "Crow Wing", "Gravity Whirl",
            "Reaping Grab", "Raven Launcher", "Gravity Burst"};

    private static final class Key {
        final float t; final float[] v; boolean snap;
        Key(float t, float[] v) { this.t = t; this.v = v; }
    }

    /** Keyframes builder: each key starts as a copy of the one before it. */
    private static final class Keys {
        final List<Key> list = new ArrayList<>();
        float[] cur;
        final float[] base;
        Keys() { this(READY); }
        Keys(float[] base) { this.base = base; cur = base.clone(); list.add(new Key(0F, base.clone())); }
        Keys k(float t) { cur = cur.clone(); Key k = new Key(t, cur); list.add(k); return this; }
        Keys snap() { list.get(list.size() - 1).snap = true; return this; }
        Keys ra(float x, float y, float z) { set(cur, RA, x, y, z); return this; }
        Keys la(float x, float y, float z) { set(cur, LA, x, y, z); return this; }
        Keys rl(float x, float y, float z) { set(cur, RL, x, y, z); return this; }
        Keys ll(float x, float y, float z) { set(cur, LL, x, y, z); return this; }
        Keys tw(float v) { cur[TW] = v; return this; }
        Keys hd(float v) { cur[HEAD] = v; return this; }
        Keys kn(float r, float l) { cur[KR] = r; cur[KL] = l; return this; }
        Keys el(float r, float l) { cur[ER] = r; cur[EL] = l; return this; }
        Keys up(float v) { cur[LIFT] = v; return this; }
        Keys fw(float v) { cur[FWD] = v; return this; }
        Keys sp(float v) { cur[SPIN] = v; return this; }
        Keys ln(float v) { cur[LEAN] = v; return this; }
        Keys ro(float v) { cur[ROLL] = v; return this; }
        Keys ready(float t) { cur = base.clone(); list.add(new Key(t, cur)); return this; }
        Key[] done() { return list.toArray(new Key[0]); }
    }

    private static final Key[][] MOVES = new Key[8][];
    private static final Key[][] AIR = new Key[5][];
    private static final Key[] HEAVY, BURST, LEAP, CROUCH, CHARGE, WARP_SLASH;
    /** In the air: legs drawn up under him, arms up on guard. */
    private static final float[] AIR_READY = READY.clone();
    static {
        set(AIR_READY, RL, -0.75F, 0F, 0.08F);
        set(AIR_READY, LL, 0.15F, 0F, -0.08F);
        AIR_READY[KR] = 1.3F; AIR_READY[KL] = 1.0F; AIR_READY[LEAN] = 6F;
    }
    static {
        // 0 Shadow Rush: running in low, the back leg kicked up behind; a wide skid; the claw rises up across him
        MOVES[0] = new Keys()
                .k(0.0F).ra(0.75F, 0F, 0.22F).la(0.6F, 0F, -0.22F).rl(-0.75F, 0F, 0.05F).ll(0.85F, 0F, -0.05F).kn(0.6F, 1.35F).el(-0.5F, -0.5F).tw(0F).ln(28F).hd(-0.1F)
                .k(0.12F).ra(0.55F, 0.1F, 0.25F).la(0.4F, 0F, -0.2F).rl(-0.45F, 0F, 0.05F).ll(1.05F, 0F, -0.05F).kn(0.45F, 1.6F).ln(30F)
                .k(0.24F).ra(0.45F, 0.4F, 0.6F).la(-1.1F, 0.45F, -0.1F).rl(-0.95F, 0F, 0.14F).ll(0.75F, 0F, -0.12F).kn(1.05F, 0.2F).el(-0.3F, -1.0F).tw(-0.55F).ln(14F).hd(0F)
                .k(0.36F).snap().ra(-2.35F, -0.55F, -0.25F).el(-0.1F, -1.0F).la(0.35F, 0F, -0.4F).tw(0.6F).ln(6F).rl(-0.8F, 0F, 0.14F).ll(0.65F, 0F, -0.12F).kn(0.8F, 0.2F).hd(-0.25F)
                .k(0.55F).ra(-2.75F, -0.8F, -0.2F).tw(0.7F).ln(3F)
                .ready(1F).done();
        // 1 Low Lunge: fist pulled back to the hip, then driven low into their body off a deep front lunge
        MOVES[1] = new Keys()
                .k(0.24F).ra(0.45F, 0.1F, 0.25F).el(-1.5F, -1.0F).la(-1.35F, 0.25F, -0.1F).tw(-0.5F).rl(-0.6F, 0F, 0.1F).ll(0.6F, 0F, -0.08F).kn(0.95F, 0.35F).ln(10F)
                .k(0.38F).snap().ra(-1.3F, -0.1F, 0F).el(0F, -1.2F).la(0.45F, 0F, -0.35F).tw(0.6F).rl(-1.05F, 0F, 0.12F).ll(0.85F, 0F, -0.08F).kn(1.3F, 0.12F).ln(24F).hd(-0.1F).fw(0.25F)
                .k(0.7F).ln(20F).fw(0.2F)
                .ready(1F).done();
        // 2 Crescent Slash: wound out to the right, then a big flat sweep across to the left
        MOVES[2] = new Keys()
                .k(0.24F).ra(-1.35F, 1.25F, 0.35F).el(-0.2F, -1.0F).la(-0.7F, 0F, -0.65F).tw(-0.75F).rl(-0.5F, 0F, 0.2F).ll(0.5F, 0F, -0.2F).kn(0.7F, 0.45F).ln(6F)
                .k(0.38F).snap().ra(-1.5F, -1.15F, 0F).el(0F, -1.0F).la(0.25F, 0F, -0.9F).tw(0.85F).ln(9F).fw(0.12F)
                .k(0.6F).ra(-1.35F, -1.45F, 0.1F).tw(0.95F)
                .ready(1F).done();
        // 3 Crow Wing: down low, then the arm thrown straight up as the black wing sweeps up, up on his toes
        MOVES[3] = new Keys()
                .k(0.2F).ra(-0.35F, -0.3F, 0.15F).el(-0.2F, -1.1F).la(-0.9F, 0.3F, -0.2F).tw(-0.35F).rl(-0.45F, 0F, 0.1F).ll(0.5F, 0F, -0.1F).kn(0.95F, 0.6F).ln(14F)
                .k(0.38F).snap().ra(-2.95F, 0.15F, 0.3F).el(-0.05F, -1.2F).la(-0.3F, 0F, -0.55F).tw(0.3F).rl(-0.15F, 0F, 0.08F).ll(0.3F, 0F, -0.08F).kn(0.12F, 0.3F).ln(-8F).hd(-0.35F).up(0.05F)
                .k(0.65F).ra(-3.0F, 0.1F, 0.35F).ln(-6F).up(0.03F)
                .ready(1F).done();
        // 4 Gravity Whirl: a full turn with the arm swung out wide, ending pressing down at them
        MOVES[4] = new Keys()
                .k(0.15F).ra(-1.5F, 1.3F, 0.4F).el(0F, -1.0F).la(-0.4F, 0F, -0.5F).tw(-0.8F).rl(-0.4F, 0F, 0.16F).ll(0.45F, 0F, -0.16F).kn(0.75F, 0.55F).ln(8F)
                .k(0.36F).snap().ra(0F, 0F, 1.45F).la(0F, 0F, -1.2F).el(0F, -0.3F).tw(0F).sp(-210F).ln(4F)
                .k(0.55F).ra(-1.3F, -0.6F, 0.3F).la(0.3F, 0F, -0.6F).el(0F, -1.0F).tw(0.5F).ln(14F).kn(0.95F, 0.35F).sp(-360F)
                .k(0.78F).ra(-1.0F, -0.3F, 0.2F).ln(12F)
                .k(1F).ra(READY[RA], READY[RA + 1], READY[RA + 2]).la(READY[LA], READY[LA + 1], READY[LA + 2]).el(READY[ER], READY[EL]).tw(READY[TW])
                .rl(READY[RL], 0F, READY[RL + 2]).ll(READY[LL], 0F, READY[LL + 2]).kn(READY[KR], READY[KL]).ln(READY[LEAN]).done();
        // 5 Reaping Grab: a deep low reach and a sweep along the floor, then hauling them up and holding them there
        MOVES[5] = new Keys()
                .k(0.18F).ra(-0.85F, 0.1F, 0.1F).el(-0.1F, -1.0F).la(0.45F, 0F, -0.3F).rl(-0.95F, 0F, 0.12F).ll(0.85F, 0F, -0.1F).kn(1.35F, 0.12F).ln(26F).tw(-0.2F).fw(0.15F).hd(0.1F)
                .k(0.36F).snap().ra(-0.75F, -1.0F, 0F).tw(0.55F).ln(28F)
                .k(0.62F).ra(-2.45F, -0.1F, 0.2F).el(-0.9F, -1.0F).la(-1.15F, 0.3F, -0.1F).tw(0.2F).rl(-0.4F, 0F, 0.1F).ll(0.45F, 0F, -0.1F).kn(0.45F, 0.35F).ln(-2F).hd(-0.45F).fw(0.1F)
                .k(1F).ra(-2.3F, 0F, 0.2F).el(-0.8F, -1.1F).ln(0F).hd(-0.35F).fw(0F).done();
        // 6 Raven Launcher: dropping low, then a rising slash thrown all the way up overhead, leaning back to watch them fly
        MOVES[6] = new Keys()
                .k(0.2F).ra(0.55F, 0.3F, 0.45F).el(-0.3F, -1.1F).la(-1.2F, 0.35F, -0.1F).tw(-0.6F).rl(-0.6F, 0F, 0.12F).ll(0.55F, 0F, -0.1F).kn(1.05F, 0.55F).ln(14F).hd(-0.1F)
                .k(0.38F).snap().ra(-2.95F, -0.25F, -0.15F).el(0F, -0.6F).la(0.5F, 0F, -0.55F).tw(0.6F).rl(-0.15F, 0F, 0.1F).ll(0.4F, 0F, -0.1F).kn(0.2F, 0.45F).ln(-12F).hd(-0.55F).up(0.06F)
                .k(0.7F).ra(-2.85F, -0.2F, -0.1F).ln(-10F).up(0.03F)
                .k(1F).ra(-2.2F, 0F, 0.15F).el(-0.5F, -1.0F).la(-0.6F, 0.2F, -0.2F).ln(-4F).hd(-0.45F).up(0F).done();
        // 7 Gravity Burst: reaching up at them, the hand raised high - a slash down across - then the palm held out at the sphere
        MOVES[7] = new Keys()
                .k(0.18F).ra(-2.8F, 0.4F, 0.45F).el(-0.3F, -0.1F).la(-2.05F, 0.25F, -0.1F).tw(-0.5F).ln(-8F).hd(-0.45F).rl(-0.35F, 0F, 0.12F).ll(0.4F, 0F, -0.1F).kn(0.45F, 0.45F)
                .k(0.36F).snap().ra(-0.55F, -0.85F, -0.2F).el(-0.1F, -1.0F).la(0.4F, 0F, -0.35F).tw(0.7F).ln(14F).hd(-0.2F).rl(-0.7F, 0F, 0.12F).ll(0.6F, 0F, -0.1F).kn(0.95F, 0.25F).fw(0.1F)
                .k(0.56F).ra(-2.0F, -0.1F, 0F).el(0F, -1.0F).tw(0.3F).ln(0F).hd(-0.35F).fw(0F)
                .k(0.8F).ra(-1.8F, -0.05F, 0F).hd(-0.3F).kn(0.5F, 0.25F)
                .k(1F).ra(0.05F, 0F, 0.08F).la(0.05F, 0F, -0.08F).el(-0.15F, -0.15F).rl(0F, 0F, 0.04F).ll(0F, 0F, -0.04F).kn(0.05F, 0.05F).tw(0F).ln(0F).hd(0F).done();
        HEAVY = MOVES[6];
        // the Kishi Warp's slash: warped in beside them, a big flat slash across that sweeps them off their feet
        WARP_SLASH = new Keys()
                .k(0.2F).ra(-1.3F, 1.35F, 0.4F).el(-0.2F, -1.0F).la(-0.6F, 0F, -0.7F).tw(-0.95F).rl(-0.6F, 0F, 0.2F).ll(0.55F, 0F, -0.2F).kn(0.95F, 0.45F).ln(10F)
                .k(0.36F).snap().ra(-1.45F, -1.3F, 0F).el(0F, -1.0F).la(0.3F, 0F, -0.95F).tw(1.0F).ln(12F).fw(0.12F)
                .k(0.62F).ra(-1.3F, -1.55F, 0.1F).tw(1.05F)
                .ready(1F).done();
        // Ki Burst (half charge): the palm drawn back to the hip, then driven out point-blank, low, off a deep lunge
        BURST = new Keys()
                .k(0.25F).ra(0.6F, 0.2F, 0.3F).el(-1.6F, -1.0F).la(-1.3F, 0.3F, -0.1F).tw(-0.6F).rl(-0.6F, 0F, 0.12F).ll(0.6F, 0F, -0.1F).kn(1.0F, 0.3F).ln(12F)
                .k(0.38F).snap().ra(-1.3F, 0F, 0F).el(0F, -1.1F).la(0.4F, 0F, -0.4F).tw(0.7F).ln(18F).rl(-0.9F, 0F, 0.14F).ll(0.8F, 0F, -0.1F).kn(1.25F, 0.12F).fw(0.15F).hd(0.1F)
                .k(0.7F).ln(15F)
                .ready(1F).done();
        // Rising Leap (full charge): a deep crouch, then springing up with the claw raised, legs drawn up under him
        LEAP = new Keys()
                .k(0.12F).ra(0.5F, 0.2F, 0.4F).el(-0.4F, -1.1F).rl(-0.6F, 0F, 0.1F).ll(0.4F, 0F, -0.1F).kn(1.35F, 1.1F).ln(12F)
                .k(0.3F).snap().ra(-2.6F, -0.4F, -0.2F).el(-0.1F, -1.0F).la(-0.4F, 0F, -0.5F).tw(0.5F).rl(-0.3F, 0F, 0.08F).ll(0.3F, 0F, -0.08F).kn(0.3F, 0.9F).ln(-8F).hd(-0.4F)
                .k(0.6F).ra(-2.8F, -0.5F, -0.2F).rl(-0.9F, 0F, 0.08F).ll(-0.2F, 0F, -0.08F).kn(1.4F, 1.2F).ln(-4F)
                .k(1F).ra(AIR_READY[RA], AIR_READY[RA + 1], AIR_READY[RA + 2]).la(AIR_READY[LA], AIR_READY[LA + 1], AIR_READY[LA + 2]).el(AIR_READY[ER], AIR_READY[EL])
                .rl(AIR_READY[RL], 0F, AIR_READY[RL + 2]).ll(AIR_READY[LL], 0F, AIR_READY[LL + 2]).kn(AIR_READY[KR], AIR_READY[KL]).ln(AIR_READY[LEAN]).tw(AIR_READY[TW]).hd(0F).done();
        // the Kishi Warp: dropping to one knee, a hand pressed flat to the floor
        CROUCH = new Keys()
                .k(0.45F).rl(-1.3F, 0F, 0.12F).ll(0.5F, 0F, -0.1F).kn(2.3F, 1.9F).ln(38F).ra(0.35F, 0F, 0.1F).el(0F, -1.0F).la(0.2F, 0F, -0.4F).hd(-0.35F).tw(0F)
                .k(1F).ln(40F).done();
        // charging (attack held): the palm drawn back, sinking into his stance
        CHARGE = new Keys()
                .k(1F).ra(0.55F, 0.25F, 0.35F).el(-1.5F, -1.1F).la(-1.2F, 0.35F, -0.1F).tw(-0.5F).rl(-0.45F, 0F, 0.1F).ll(0.5F, 0F, -0.1F).kn(0.9F, 0.5F).ln(10F).done();
        // ---- the air string (after the air video): rising leap, a flat slash, a close strike, a big slash, the spike slam
        AIR[0] = new Keys(AIR_READY)
                .k(0.15F).ra(0.5F, 0.2F, 0.4F).el(-0.4F, -1.1F).la(-0.6F, 0F, -0.3F).rl(-0.9F, 0F, 0.08F).ll(-0.3F, 0F, -0.08F).kn(1.6F, 1.2F).ln(10F)
                .k(0.36F).snap().ra(-2.6F, -0.5F, -0.2F).el(-0.1F, -1.0F).tw(0.5F).ln(-6F).rl(-0.2F, 0F, 0.08F).ll(0.4F, 0F, -0.08F).kn(0.4F, 0.9F).hd(-0.3F)
                .ready(1F).done();
        AIR[1] = new Keys(AIR_READY)
                .k(0.24F).ra(-1.4F, 1.2F, 0.35F).el(-0.2F, -1.0F).la(-0.7F, 0F, -0.65F).tw(-0.7F)
                .k(0.38F).snap().ra(-1.5F, -1.15F, 0F).el(0F, -1.0F).la(0.25F, 0F, -0.9F).tw(0.85F).ln(10F)
                .k(0.6F).ra(-1.35F, -1.45F, 0.1F).tw(0.95F)
                .ready(1F).done();
        AIR[2] = new Keys(AIR_READY)
                .k(0.24F).ra(0.4F, 0.1F, 0.25F).el(-1.5F, -1.0F).la(-1.3F, 0.3F, -0.1F).tw(-0.5F)
                .k(0.38F).snap().ra(-1.45F, -0.05F, 0F).el(0F, -1.2F).la(0.4F, 0F, -0.4F).tw(0.6F).ln(14F).rl(-1.2F, 0F, 0.08F).kn(0.6F, 1.0F)
                .ready(1F).done();
        AIR[3] = new Keys(AIR_READY)
                .k(0.2F).ra(-2.8F, 0.5F, 0.5F).el(-0.3F, -1.0F).la(-1.2F, 0.3F, -0.1F).tw(-0.6F).ln(-10F).hd(-0.2F)
                .k(0.36F).snap().ra(-0.5F, -0.9F, -0.2F).el(-0.1F, -1.0F).la(0.4F, 0F, -0.4F).tw(0.8F).ln(18F).ro(-8F)
                .k(0.6F).ra(-0.4F, -1.0F, -0.2F).ln(15F).ro(-6F)
                .ready(1F).done();
        AIR[4] = new Keys(AIR_READY)
                .k(0.18F).ra(-2.9F, 0.2F, 0.3F).la(-2.9F, -0.2F, -0.3F).el(-0.6F, -0.6F).ln(-15F).hd(-0.4F).rl(-1.0F, 0F, 0.1F).ll(-0.6F, 0F, -0.1F).kn(1.6F, 1.5F)
                .k(0.36F).snap().ra(-0.3F, -0.1F, 0.1F).la(-0.3F, 0.1F, -0.1F).el(-0.1F, -0.1F).ln(35F).hd(0.3F).rl(-0.3F, 0F, 0.1F).ll(0.4F, 0F, -0.1F).kn(0.5F, 0.6F)
                .k(0.62F).ln(30F)
                .k(1F).ra(READY[RA], READY[RA + 1], READY[RA + 2]).la(READY[LA], READY[LA + 1], READY[LA + 2]).el(READY[ER], READY[EL])
                .rl(-0.6F, 0F, 0.1F).ll(0.5F, 0F, -0.1F).kn(1.2F, 0.6F).ln(10F).hd(0F).tw(0F).done();
    }

    private static final class St {
        int start = Integer.MIN_VALUE, hit = -2; boolean heavy;
        int groundFor = Integer.MIN_VALUE, groundHit = -2; boolean ground;
        float[] from, last;
        float lastAge, lastDur;
        int lastMoveHit = -1;
        boolean lastGrounded = true;
    }
    private static final Map<Integer, St> STATES = ClientReset.track(new HashMap<>());

    private RhalixComboAnim() {}

    private static boolean rhalix(Player p) {
        return p instanceof AbstractClientPlayer cp && ClientCharacterData.get(cp) == CharacterType.RHALIX;
    }

    /** This swing is one of his ground combo's (started on the ground): this class animates it, not his kick combo. */
    public static boolean owns(Player p) {
        if (!rhalix(p)) return false;
        ComboClient.Swing sw = ComboClient.swing(p);
        if (sw == null || !sw.style().rhalix()) return false;
        St st = STATES.computeIfAbsent(p.getId(), k -> new St());
        if (sw.style() == ComboStyle.RHALIX_AIR) return true;
        if (st.groundFor != sw.start() || st.groundHit != sw.hit()) {
            st.groundFor = sw.start(); st.groundHit = sw.hit();
            st.ground = !p.isFallFlying() && !(p instanceof AbstractClientPlayer cp && FlightTracker.isFlying(cp))
                    && (p.onGround() || sw.hit() > 0 && st.ground || sw.style().special(sw.hit()));   // (mid-string: a little hop doesn't change moves)
        }
        return st.ground;
    }

    /** The move a swing plays (heavy: the launcher). */
    private static Key[] keys(Player p, ComboClient.Swing sw) {
        if (sw.heavy()) return HEAVY;
        if (sw.style() == ComboStyle.RHALIX_AIR) return AIR[Math.floorMod(sw.hit(), AIR.length)];
        if (sw.hit() == ComboStyle.BURST) return BURST;
        if (sw.hit() == ComboStyle.LEAP) return LEAP;
        if (sw.hit() == 4 && KishiWarpClient.warpSlash(p, sw.start())) return WARP_SLASH;
        return MOVES[Math.floorMod(sw.hit(), MOVES.length)];
    }

    /** On his feet for this swing (his feet are kept on the floor), not in the air. */
    private static boolean grounded(ComboClient.Swing sw) {
        return sw.style() == ComboStyle.RHALIX && sw.hit() != ComboStyle.LEAP;
    }

    private static float smooth(float x) { x = Mth.clamp(x, 0F, 1F); return x * x * (3F - 2F * x); }

    private static void set(float[] a, int i, float x, float y, float z) { a[i] = x; a[i + 1] = y; a[i + 2] = z; }

    /** The pose at progress t of a move. */
    static float[] sample(Key[] ks, float t) {
        t = Mth.clamp(t, 0F, 1F);
        for (int i = 1; i < ks.length; i++) {
            if (t <= ks[i].t || i == ks.length - 1) {
                Key a = ks[i - 1], b = ks[i];
                float f = b.t <= a.t ? 1F : Mth.clamp((t - a.t) / (b.t - a.t), 0F, 1F);
                f = b.snap ? 1F - (1F - f) * (1F - f) * (1F - f) : f * f * (3F - 2F * f);      // strikes snap out, the rest eases
                float[] o = new float[N];
                for (int j = 0; j < N; j++) o[j] = Mth.lerp(f, a.v[j], b.v[j]);
                return o;
            }
        }
        return ks[ks.length - 1].v.clone();
    }

    /** {pose, weight} right now, or null. Keeps the blends between moves (and out of the string). */
    private static Object[] current(Player p, float age) {
        ComboClient.Swing sw = owns(p) ? ComboClient.swing(p) : null;
        St st = STATES.computeIfAbsent(p.getId(), k -> new St());
        float partial = age - p.tickCount;
        // not swinging: knelt for the Kishi Warp, or charging a held attack
        if (sw == null || (age - sw.start()) / sw.duration() > 1.05F) {
            float ck = KishiWarpClient.crouchTicks(p, Mth.clamp(partial, 0F, 1F));
            int charge = p == net.minecraft.client.Minecraft.getInstance().player ? ComboClient.charge() : -1;
            Key[] pseudo = ck >= 0F ? CROUCH : charge >= 4 ? CHARGE : null;
            if (pseudo != null) {
                int id = ck >= 0F ? -11 : -12;
                float in = ck >= 0F ? ck : charge - 4 + Mth.clamp(partial, 0F, 1F);
                if (st.start != id) {
                    st.from = st.last != null && age - st.lastAge < 4F ? st.last.clone() : null;
                    if (st.from != null) st.from[SPIN] = Mth.wrapDegrees(st.from[SPIN]);
                    st.start = id; st.hit = -5; st.ground = true; st.groundFor = Integer.MIN_VALUE;
                }
                float[] pose = sample(pseudo, ck >= 0F ? in / 12F : 1F);
                float k = smooth(in / 4F), w = 1F;
                if (st.from != null) for (int j = 0; j < N; j++) pose[j] = Mth.lerp(k, st.from[j], pose[j]);
                else w = k;
                st.last = pose; st.lastAge = age; st.lastGrounded = true;
                return new Object[]{pose, w};
            }
        }
        if (sw != null) {
            float t = (age - sw.start()) / sw.duration();
            if (t >= 0F && t <= 1.05F) {
                if (st.start != sw.start() || st.hit != sw.hit() || st.heavy != sw.heavy()) {
                    st.from = st.last != null && age - st.lastAge < 4F ? st.last.clone() : null;
                    if (st.from != null) st.from[SPIN] = Mth.wrapDegrees(st.from[SPIN]);
                    st.start = sw.start(); st.hit = sw.hit(); st.heavy = sw.heavy();
                }
                float[] pose = sample(keys(p, sw), t);
                st.lastGrounded = grounded(sw);
                float in = age - sw.start();
                float w = 1F;
                if (st.from != null) {
                    float k = smooth(in / 2.5F);
                    for (int j = 0; j < N; j++) pose[j] = Mth.lerp(k, st.from[j], pose[j]);
                } else w = smooth((in + 0.5F) / 2.5F);
                st.last = pose; st.lastAge = age; st.lastMoveHit = sw.hit();
                return new Object[]{pose, w};
            }
        }
        if (st.last == null) return null;
        float w = 1F - smooth((age - st.lastAge) / 5F);                    // after the string: easing back to normal
        if (w <= 0F || age < st.lastAge - 1F) { st.last = null; return null; }
        return new Object[]{st.last, w};
    }

    /** How far his body is lowered to keep his feet on the floor in this stance (blocks, <= 0). */
    private static float autoLift(float[] v) {
        float th = v[LEAN] * Mth.DEG_TO_RAD;
        float dr = legDrop(v[RL], v[RL + 2], knee(v, true, th)), dl = legDrop(v[LL], v[LL + 2], knee(v, false, th));
        return Math.max(dr, dl) - 0.75F;
    }

    private static float legDrop(float legX, float legZ, float knee) {
        return 0.375F * (Mth.cos(legX) + Mth.cos(legX + knee)) * Mth.cos(legZ);
    }

    /** The knee bend asked for (the keyed total). */
    private static float knee(float[] v, boolean right, float th) { return right ? v[KR] : v[KL]; }

    public static void apply(HumanoidModel<?> m, Player p, float age) {
        if (LimbBend.firstPerson || !rhalix(p)) return;
        Object[] c = current(p, age);
        if (c == null) return;
        float[] v = (float[]) c[0];
        float w = (float) c[1];
        float th = v[LEAN] * Mth.DEG_TO_RAD;
        // arms are the torso's; legs are keyed upright in the world, so they're turned back by his lean
        pose(m.rightArm, v[RA], v[RA + 1], v[RA + 2], w);
        pose(m.leftArm, v[LA], v[LA + 1], v[LA + 2], w);
        pose(m.rightLeg, v[RL] - th, v[RL + 1], v[RL + 2], w);
        pose(m.leftLeg, v[LL] - th, v[LL + 1], v[LL + 2], w);
        float tw = Mth.lerp(w, m.body.yRot, v[TW]);
        m.body.yRot = tw;
        m.rightArm.z = Mth.sin(tw) * 5F;
        m.rightArm.x = -Mth.cos(tw) * 5F;
        m.leftArm.z = -Mth.sin(tw) * 5F;
        m.leftArm.x = Mth.cos(tw) * 5F;
        m.head.xRot = Mth.lerp(w * 0.75F, m.head.xRot, v[HEAD]) - th * w * 0.85F;    // still looking at them however he leans
        m.head.yRot -= tw * 0.4F;
        m.hat.copyFrom(m.head);
        // knees and elbows: the keyed totals, less what the model bends by itself
        float kr = v[KR] - LimbBend.knee(m.rightLeg.xRot, m.leftLeg.xRot, false), kl = v[KL] - LimbBend.knee(m.leftLeg.xRot, m.rightLeg.xRot, false);
        float er = v[ER] - LimbBend.elbow(m.rightArm.xRot), el = v[EL] - LimbBend.elbow(m.leftArm.xRot);
        LimbBend.extraKnees(p, kr * w, kl * w);
        LimbBend.extraElbows(p, er * w, el * w);
    }

    private static void pose(ModelPart part, float x, float y, float z, float w) {
        part.xRot = Mth.lerp(w, part.xRot, x);
        part.yRot = Mth.lerp(w, part.yRot, y);
        part.zRot = Mth.lerp(w, part.zRot, z);
    }

    /** {lift, forward, spin, lean, roll} of his whole body right now, or null. */
    public static float[] bodyMotion(Player p, float partial) {
        if (!rhalix(p)) return null;
        Object[] c = current(p, p.tickCount + partial);
        if (c == null) return null;
        float[] v = (float[]) c[0];
        float w = (float) c[1];
        St st = STATES.get(p.getId());
        float lift = st != null && st.lastGrounded ? autoLift(v) : 0F;        // (in the air nothing holds his feet down)
        return new float[]{(lift + v[LIFT]) * w, v[FWD] * w, v[SPIN] * w, v[LEAN] * w, v[ROLL] * w};
    }

    /** How far his wings open for the move (0..1), for RhalixWingsClient: flared on the big ones. */
    public static float wingSpread(Player p, float partial) {
        Object[] c = current(p, p.tickCount + partial);
        St st = STATES.get(p.getId());
        if (c == null || st == null) return 0F;
        float w = (float) c[1];
        ComboClient.Swing sw = ComboClient.swing(p);
        float t = sw == null ? 1F : Mth.clamp((p.tickCount + partial - sw.start()) / sw.duration(), 0F, 1F);
        float flare = smooth((t - 0.2F) / 0.18F) * (1F - smooth((t - 0.7F) / 0.3F));
        // (only a little: open wide they hide the moves, and the reference doesn't show them at all)
        float big = switch (st.lastMoveHit) { case 3, 6 -> 0.3F; case 4, 7 -> 0.2F; default -> 0.08F; };
        return Mth.clamp(big * flare * w, 0F, 1F);
    }

    private static final java.util.Set<Integer> TRANSFORMED = ClientReset.trackAll(new java.util.HashSet<>());

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer p) || !rhalix(p)) return;
        if (FirstPersonHead.ownFirstPerson(p) || ClientCharacterData.standStill) return;
        float[] b = bodyMotion(p, event.getPartialTick());
        if (b == null) return;
        PoseStack pose = event.getPoseStack();
        PoseGuard.push(pose);
        TRANSFORMED.add(p.getId());
        float yaw = Mth.rotLerp(event.getPartialTick(), p.yBodyRotO, p.yBodyRot);
        // in his own facing: +z forward; pivoting round his hips
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.translate(0, b[0], b[1]);
        pose.translate(0, 0.75, 0);
        pose.mulPose(Axis.YP.rotationDegrees(b[2]));
        pose.mulPose(Axis.XP.rotationDegrees(b[3]));
        pose.mulPose(Axis.ZP.rotationDegrees(b[4]));
        pose.translate(0, -0.75, 0);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
    }

    @SubscribeEvent
    public static void onRenderPost(RenderPlayerEvent.Post event) {
        if (TRANSFORMED.remove(event.getEntity().getId())) PoseGuard.pop(event.getPoseStack());
    }

    /** The move playing now and how far into it (for tests): {hit, progress}, or null. */
    public static float[] playing(Player p, float partial) {
        ComboClient.Swing sw = owns(p) ? ComboClient.swing(p) : null;
        return sw == null ? null : new float[]{sw.heavy() ? -1 : sw.hit(), (p.tickCount + partial - sw.start()) / sw.duration()};
    }
}
