package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.mikiko.GeoAnims;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Rhalix fights bare-handed with quick punches and a lot of kicks: every charged swing plays the next move of a
 * five-hit combo (jabs, crosses, an uppercut, front kicks, a roundhouse, low kicks, an axe kick, a spinning back kick).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RhalixCombatAnim {
    private static final String[][] COMBOS = {
            {"rh_jab_right", "rh_jab_left", "rh_front_kick_right", "rh_roundhouse_left", "rh_axe_kick_right"},
            {"rh_low_kick_right", "rh_jab_left", "rh_gut_kick_left", "rh_uppercut_right", "rh_spin_back_kick"},
            {"rh_front_kick_right", "rh_gut_kick_left", "rh_roundhouse_left", "rh_jab_right", "rh_spin_back_kick"},
            {"rh_jab_left", "rh_low_kick_right", "rh_jab_right", "rh_front_kick_right", "rh_axe_kick_right"},
    };
    /** With a sword: clean, trained cuts. */
    private static final String[][] SWORD_COMBOS = {
            {"rh_sw_down_slash", "rh_sw_rising_slash", "rh_sw_thrust", "rh_sw_horizontal", "rh_sw_whirl"},
            {"rh_sw_horizontal", "rh_sw_down_slash", "rh_sw_rising_slash", "rh_sw_thrust", "rh_sw_overhead"},
            {"rh_sw_thrust", "rh_sw_rising_slash", "rh_sw_down_slash", "rh_sw_horizontal", "rh_sw_overhead"},
            {"rh_sw_rising_slash", "rh_sw_horizontal", "rh_sw_thrust", "rh_sw_down_slash", "rh_sw_whirl"},
    };
    /** Standing about with a sword: mostly inspecting it, now and then sharpening it or running through a form. */
    private static final String[] SWORD_IDLES = {"rh_sw_inspect", "rh_sw_inspect", "rh_sw_sharpen", "rh_sw_inspect",
            "rh_sw_practice", "rh_sw_inspect", "rh_sw_inspect", "rh_sw_sharpen"};
    private static final int SWORD_IDLE_AFTER = 60;
    /** In the air: nothing but kicks, arms folded across his chest. */
    private static final String[][] AIR_COMBOS = {
            {"rh_front_kick_right", "rh_gut_kick_left", "rh_roundhouse_left", "rh_low_kick_right", "rh_axe_kick_right"},
            {"rh_roundhouse_left", "rh_front_kick_right", "rh_low_kick_right", "rh_gut_kick_left", "rh_spin_back_kick"},
            {"rh_gut_kick_left", "rh_low_kick_right", "rh_front_kick_right", "rh_roundhouse_left", "rh_spin_back_kick"},
    };
    private static final int STICK_AFTER = 100, STICK_EVERY = 360;
    private static final int COMBO_TIMEOUT = 30;
    private static final Random RANDOM = new Random();

    private static final class State {
        boolean lastSwinging;
        int lastSwingTime, lastSwingStart = -100, lastAttack = -100, step, variant = -1;
        String move;
        int moveStart, still, clock, calm;
        boolean air;
        float strength, idle, idleO;
        boolean sword;
        float scale = 1F;           // attack playback stretch (Epic Fight battle mode)
        float fold, foldO;          // arms folded across his chest (flying / fighting in the air)
    }

    private static final Map<Integer, State> STATES = new HashMap<>();

    private RhalixCombatAnim() {}

    private static boolean rhalix(Player p) {
        return p instanceof AbstractClientPlayer cp && ClientCharacterData.get(cp) == CharacterType.RHALIX;
    }

    public static boolean attacking(Player p) {
        State s = STATES.get(p.getId());
        return s != null && s.move != null;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null) {
            if (mc.level == null) STATES.clear();
            return;
        }
        STATES.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        for (AbstractClientPlayer p : mc.level.players()) {
            if (!rhalix(p)) continue;
            State s = STATES.computeIfAbsent(p.getId(), k -> new State());
            int now = p.tickCount;
            boolean start = p.swinging && p.swingingArm == InteractionHand.MAIN_HAND && (!s.lastSwinging || p.swingTime < s.lastSwingTime)
                    && !(p == mc.player && (mc.options.keyUse.isDown() || mc.options.keyDrop.isDown()));   // right-click / drop swings aren't attacks
            s.lastSwinging = p.swinging;
            s.lastSwingTime = p.swingTime;
            boolean charged = p == mc.player ? s.strength >= 0.9F : now - s.lastSwingStart >= 9;
            if (com.xenowarriors.client.combo.ComboClient.comboSwing(p)) charged = true;   // a combo swing (its own timing)
            boolean external = AttackHooks.externalAttack.test(p);
            if (external) {                                    // an Epic Fight battle-mode attack
                start = true;
                charged = true;
            }
            boolean sword = isSword(p);
            // in the air: the floaty aerial combo (launch, juggle, slam), on top of his own moves
            if (start && charged && p == mc.player && mc.player != null && com.xenowarriors.client.mikiko.AerialCombo.applies(mc.player))
                com.xenowarriors.client.mikiko.AerialCombo.swing(mc.player);
            if (com.xenowarriors.client.compat.DmzCompat.blocking(p)) start = false;   // strikes from the guard: BlockPose
            if (start && sword && charged && !(p == mc.player && mc.hitResult != null
                    && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK)) {
                if (now - s.lastAttack > COMBO_TIMEOUT || s.step >= 5 || s.variant < 0 || !s.sword) {
                    s.step = 0;
                    s.variant = RANDOM.nextInt(SWORD_COMBOS.length);
                }
                s.sword = true;
                s.move = SWORD_COMBOS[s.variant][s.step++];
                s.moveStart = now;
                s.scale = external ? AttackHooks.timeScale(p, GeoAnims.length(GeoAnims.RHALIX, s.move), 0.42F) : 1F;
                s.lastAttack = now;
            }
            // standing about holding a sword
            boolean still = Math.abs(p.getX() - p.xo) + Math.abs(p.getZ() - p.zo) < 0.02 && !p.swinging && p.onGround() && !Busy.busy(p)
                    && !p.isCrouching() && !p.isPassenger() && !p.isUsingItem() && !FlightTracker.isFlying(p);
            s.still = still && sword && s.move == null ? s.still + 1 : 0;
            boolean idling = s.still >= SWORD_IDLE_AFTER && !RhalixWingsClient.busy(p);
            s.idleO = s.idle;
            s.idle = Mth.clamp(s.idle + (idling ? 0.08F : -0.2F), 0F, 1F);
            if (idling) s.clock++;
            else if (s.idle <= 0F) s.clock = 0;          // (keeps its place while fading out)
            boolean calm = Math.abs(p.getX() - p.xo) + Math.abs(p.getZ() - p.zo) < 0.02 && !p.swinging && p.onGround() && !Busy.busy(p)
                    && !p.isCrouching() && !p.isPassenger() && !p.isUsingItem() && !FlightTracker.isFlying(p) && !p.isSleeping()
                    && p.getMainHandItem().isEmpty() && s.move == null && !RhalixWingsClient.busy(p) && !RhalixHungerClient.hungry(p)
                    && !RhalixBookIdle.hasBook(p);     // (his Origins outfits read a book instead)
            int stickT = s.calm >= STICK_AFTER ? (s.calm - STICK_AFTER) % STICK_EVERY : -1;
            float stickLen = GeoAnims.length(GeoAnims.RHALIX, fidget(s, p));
            if (calm) s.calm++;
            else if (stickT < 0 || stickT >= stickLen) s.calm = 0;      // (finishes putting the stick back first)
            else s.calm++;
            if (s.idle > 0.5F && "rh_sw_sharpen".equals(idleAnim(s, 0F)[0]) && now % 7 == 0 && sharpenStroke(s)) sparks(mc, p);
            if (start && p.getMainHandItem().isEmpty() && charged && !(mc.hitResult != null && p == mc.player
                    && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK)) {
                boolean air = FlightTracker.isFlying(p) || !p.onGround();      // flying or jumping: kicks only
                if (now - s.lastAttack > COMBO_TIMEOUT || s.step >= 5 || s.variant < 0 || s.sword || air != s.air) {
                    s.step = 0;
                    s.variant = RANDOM.nextInt(air ? AIR_COMBOS.length : COMBOS.length);
                }
                s.sword = false;
                s.air = air;
                s.move = (air ? AIR_COMBOS : COMBOS)[s.variant][s.step++];
                s.moveStart = now;
                // on the ground he steps into his punches like Mikiko does (stopping at a locked-on target)
                if (!air && p == mc.player && mc.player != null && !p.isCrouching() && p.onGround()
                        && (s.move.contains("jab") || s.move.contains("uppercut"))) {
                    boolean first = s.step <= 2;
                    com.xenowarriors.client.mikiko.DashGuard.dash(mc.player, first ? 0.4 : 0.34, first ? 0.18 : 0.08);
                }
                s.scale = external ? AttackHooks.timeScale(p, GeoAnims.length(GeoAnims.RHALIX, s.move), 0.42F) : 1F;
                s.lastAttack = now;
            }
            if (start) s.lastSwingStart = now;
            // arms folded whenever he's flying, and all through an aerial combo
            boolean airFight = s.air && !s.sword && now - s.lastAttack <= COMBO_TIMEOUT && !p.onGround();
            boolean fold = (FlightTracker.isFlying(p) && !sword && !p.isUsingItem()) || airFight;
            s.foldO = s.fold;
            s.fold = Mth.clamp(s.fold + (fold ? 0.2F : -0.15F), 0F, 1F);
            if (p == mc.player) s.strength = p.getAttackStrengthScale(0F);
            if (s.move != null && now - s.moveStart > GeoAnims.length(GeoAnims.RHALIX, s.move) * s.scale + 1) s.move = null;   // (length is in ticks)
        }
    }

    public static boolean isSword(Player p) {
        var item = p.getMainHandItem().getItem();
        return item instanceof net.minecraft.world.item.SwordItem;
    }

    /** {animation name, time into it (ticks)} for the sword idle right now. */
    private static Object[] idleAnim(State s, float partial) {
        float t = s.clock + (s.idle > s.idleO ? partial : 0F);
        float total = 0F;
        for (String n : SWORD_IDLES) total += Math.max(1F, GeoAnims.length(GeoAnims.RHALIX, n));
        t %= total;
        for (String n : SWORD_IDLES) {
            float len = Math.max(1F, GeoAnims.length(GeoAnims.RHALIX, n));
            if (t < len) return new Object[]{n, t};
            t -= len;
        }
        return new Object[]{SWORD_IDLES[0], 0F};
    }

    /** Mid-sharpen, the whetstone reaching the end of a stroke (strokes run from 0.7 s, every 0.56 s, ten of them). */
    private static boolean sharpenStroke(State s) {
        float t = (float) idleAnim(s, 0F)[1] / 20F;
        return t > 0.8F && t < 6.3F;
    }

    private static void sparks(Minecraft mc, Player p) {
        float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        double x = p.getX() + fx * 0.45 + fz * 0.1, y = p.getY() + 0.95, z = p.getZ() + fz * 0.45 - fx * 0.1;
        for (int i = 0; i < 3; i++) {
            mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, x, y, z,
                    (RANDOM.nextDouble() - 0.5) * 0.15, RANDOM.nextDouble() * 0.1, (RANDOM.nextDouble() - 0.5) * 0.15);
        }
    }

    /** Sharpening right now (draws the whetstone in his other hand). */
    public static boolean sharpening(Player p) {
        State s = STATES.get(p.getId());
        return s != null && s.idle > 0.3F && s.move == null && isSword(p) && "rh_sw_sharpen".equals(idleAnim(s, 0F)[0]);
    }

    /** Knees through the leg stretch: hamstrings (back knee soft), then hugging his right knee up to his chest. */
    private static final float[][] LEG_STRETCH_KNEES = {{0F, 0F, 0F}, {0.7F, 0F, 0.3F}, {1.9F, 0F, 0.3F}, {2.5F, 0.3F, 0F},
            {3.7F, 0.3F, 0F}, {4.2F, 1.9F, 0F}, {4.9F, 1.9F, 0F}, {5.3F, 0F, 0F}, {6.0F, 0F, 0F}};

    /** Which fidget this round is: the stick, or (after ten minutes without a fight, every other time) a leg stretch. */
    private static String fidget(State s, Player p) {
        int round = Math.max(0, s.calm - STICK_AFTER) / STICK_EVERY;
        return p instanceof AbstractClientPlayer cp && CalmTracker.longCalm(cp) && round % 2 == 1 ? "rh_leg_stretch" : "rh_stick_idle";
    }

    /** Ticks into the fidget, or -1. */
    private static float stickTime(State s, float partial) {
        if (s.calm < STICK_AFTER) return -1F;
        float t = (s.calm - STICK_AFTER) % STICK_EVERY + partial;
        return t <= Math.max(GeoAnims.length(GeoAnims.RHALIX, "rh_stick_idle"), GeoAnims.length(GeoAnims.RHALIX, "rh_leg_stretch")) ? t : -1F;
    }

    /** Where the stick is: 0 in his mouth, 1 in his hand, 2 in his pocket. */
    public static int stickState(Player p, float partial) {
        State s = STATES.get(p.getId());
        if (s == null) return 0;
        float t = stickTime(s, partial) / 20F;
        if (t < 0F || !"rh_stick_idle".equals(fidget(s, p))) return 0;
        if (t < 0.8F) return 0;
        if (t < 1.5F) return 1;
        if (t < 2.6F) return 2;
        if (t < 4.4F) return 1;
        return 0;
    }

    public static void apply(HumanoidModel<?> m, Player p, float age) {
        State s = STATES.get(p.getId());
        if (s == null || !rhalix(p)) return;
        float partial = age - p.tickCount;
        float st = s.move == null ? stickTime(s, Mth.clamp(partial, 0F, 1F)) : -1F;
        String fid = fidget(s, p);
        float fidLen = GeoAnims.length(GeoAnims.RHALIX, fid);
        if (st >= 0F && st <= fidLen) {
            float fw = GeoAnims.envelope(st, fidLen, 4F);
            GeoAnims.apply(m, GeoAnims.RHALIX, fid, st, false, fw);
            if (fid.equals("rh_leg_stretch")) {
                float[] k = LimbBend.kneeKeys(LEG_STRETCH_KNEES, st / 20F);
                LimbBend.extraKnees(p, k[0] * fw, k[1] * fw);
            }
            m.hat.copyFrom(m.head);
        }
        float idle = Mth.lerp(partial, s.idleO, s.idle);
        if (s.move == null && idle > 0F && isSword(p)) {
            Object[] ia = idleAnim(s, partial);
            GeoAnims.apply(m, GeoAnims.RHALIX, (String) ia[0], (float) ia[1], false, idle);
            m.hat.copyFrom(m.head);
            return;
        }
        if (s.move == null) return;
        float t = (age - s.moveStart) / s.scale;                      // ticks, like GeoAnims expects
        float len = GeoAnims.length(GeoAnims.RHALIX, s.move);
        if (t < 0 || t > len) return;
        float w = GeoAnims.envelope(t, len, 1.5F);
        GeoAnims.apply(m, GeoAnims.RHALIX, s.move, t, false, w);
        m.hat.copyFrom(m.head);
    }

    /** Flying (any speed) and fighting in the air: arms folded across his chest, his legs do the work. */
    public static void foldArms(HumanoidModel<?> m, Player p, float age) {
        State s = STATES.get(p.getId());
        if (s == null || !rhalix(p)) return;
        float w = Mth.lerp(Mth.clamp(age - p.tickCount, 0F, 1F), s.foldO, s.fold);
        if (w <= 0F) return;
        w = w * w * (3F - 2F * w);
        float breathe = Mth.sin(age * 0.08F) * 0.02F;
        m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, -0.95F + breathe);
        m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, -0.62F);
        m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, 0.12F);
        m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, -1.05F + breathe);
        m.leftArm.yRot = Mth.lerp(w, m.leftArm.yRot, 0.62F);
        m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, -0.12F);
    }
}
