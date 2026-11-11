package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * With DragonMineZ: Rhalix's Kishi becomes a DragonMineZ gravity field ("Kishi 40G").
 *
 * <p><b>How strong.</b> {@link #formula}: G = 10 x dome level (1-3, pressing again) x DMZ factor (1-5, from the
 * caster's DragonMineZ level on a log scale up to DMZ's max level) x XW factor (1-3, Character Level on a log scale up
 * to 88,888) x skill factor (1-2, Kishi's ability level 1-100: tier and level). A brand-new Rhalix makes 10G, a maxed
 * one 900G at gravity level 3. A bot uses its own DMZ character and its own Character Level, and its owner's Kishi
 * levels for Rhalix (the same as every other bot skill).
 *
 * <p><b>What it does</b> (DragonMineZ's own gravity rules, read from its server config; defaults in brackets):
 * the net gravity is G minus the target's resistance (from their DMZ stats; 0 for a plain mob). Then
 * <ul>
 *   <li>players: DragonMineZ itself applies it - the dome adds its G like a gravity machine (mixin on
 *       GravityLogic.getGravityMultiplier), so movement/attack speed, stat reduction, heavier falls, slower or stopped
 *       ki flight, higher ki/stamina use and DMZ's gravity HUD all come from DragonMineZ;</li>
 *   <li>mobs and bots: the same formulas here - movement -min(95%, 1.6 x sqrt(net/100)), attack damage
 *       -min(90%, net%), jumps x(1 - that penalty), extra fall pull min(0.6, 0.02 x net) a tick, and from 75 net G
 *       ("hard stop") no flying at all;</li>
 *   <li>everyone with DMZ stats (players, bots): stamina and ki drain each second while inside.</li>
 * </ul>
 * Allies are never affected (combat.Allies). Without DragonMineZ nothing here runs and Kishi works as before.
 */
public final class DmzKishiGravity {
    private DmzKishiGravity() {}

    public static final double BASE_G = 10, DMZ_SPAN = 4, XW_SPAN = 2, SKILL_SPAN = 1, MIN_G = 10, MAX_G = 900;
    public static final int XW_MAX_LEVEL = com.xenowarriors.stats.Stats.MAX_CHARACTER_LEVEL;
    private static final UUID SPEED_ID = UUID.fromString("3b1c6e0a-6c21-4f7e-a0d4-5a1f0b7c9e01");
    private static final UUID ATTACK_ID = UUID.fromString("3b1c6e0a-6c21-4f7e-a0d4-5a1f0b7c9e02");

    private static Boolean loaded;
    private static boolean broken;
    private static Capability<?> cap;
    private static Method getLevel, getMaxValue, cfg, avgStats, getResources, getMaxStamina, getMaxEnergy, removeStamina, removeEnergy,
            dmzTick, dmzSync, gameplay, serverCfg, maxValueCfg;

    /** Set by the mixin the first time DragonMineZ asks for a player's gravity: DMZ's own gravity is running. */
    public static volatile boolean mixinLive;

    /** DragonMineZ is here and readable. */
    public static boolean on() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("dragonminez");
        if (!loaded || broken) return false;
        if (cap != null) return true;
        try {
            cap = (Capability<?>) Class.forName("com.dragonminez.common.stats.StatsCapability").getField("INSTANCE").get(null);
            Class<?> data = Class.forName("com.dragonminez.common.stats.StatsData");
            getLevel = data.getMethod("getLevel");
            getMaxValue = data.getMethod("getConfiguredMaxValue");
            getResources = data.getMethod("getResources");
            getMaxStamina = data.getMethod("getMaxStamina");
            getMaxEnergy = data.getMethod("getMaxEnergy");
            Class<?> res = Class.forName("com.dragonminez.common.stats.character.Resources");
            removeStamina = res.getMethod("removeStamina", float.class);
            removeEnergy = res.getMethod("removeEnergy", float.class);
            Class<?> gl = Class.forName("com.dragonminez.server.util.GravityLogic");
            cfg = gl.getDeclaredMethod("cfg");
            cfg.setAccessible(true);
            avgStats = gl.getDeclaredMethod("avgEffectiveStats", data);
            avgStats.setAccessible(true);
            dmzTick = gl.getMethod("tick", ServerPlayer.class);
            try { dmzSync = Class.forName("com.dragonminez.server.util.GravityStateSync").getMethod("sync", ServerPlayer.class); } catch (Throwable ignored) {}
            try {
                Class<?> cm = Class.forName("com.dragonminez.common.config.ConfigManager");
                serverCfg = cm.getMethod("getServerConfig");
                gameplay = serverCfg.getReturnType().getMethod("getGameplay");
                maxValueCfg = gameplay.getReturnType().getMethod("getMaxValue");
            } catch (Throwable ignored) {}
            return true;
        } catch (Throwable t) {
            broken = true;
            cap = null;
            XenoWarriors.LOGGER.warn("DragonMineZ gravity not readable; Kishi keeps its normal gravity", t);
            return false;
        }
    }

    // ------------------------------------------------------------------ how strong

    /** The gravity (in G) of a Kishi dome at this gravity level for this caster (a player, or a bot's stand-in). */
    public static double gravityFor(Entity caster, int domeLevel) {
        int[] in = inputs(caster);
        return formula(in[0], in[1], in[2], in[3], domeLevel);
    }

    /** {DMZ level, DMZ max level, XW Character Level, Kishi ability level 1-100} for this caster. */
    public static int[] inputs(Entity caster) {
        int dmz = 1, dmzMax = 1, xw = 1;
        int[] sk = com.xenowarriors.skill.SkillLevels.of(caster, "kishi");
        PartyBotEntity bot = caster == null ? null : PartyBots.botOf(caster);
        Object data = null;
        if (bot != null) { xw = bot.botLevel(); data = DmzBotKi.statsFor(bot); }
        else if (caster instanceof ServerPlayer p) { xw = com.xenowarriors.stats.Stats.characterLevel(p); data = on() ? p.getCapability(cap).resolve().orElse(null) : null; }
        if (data != null) {
            try { dmz = ((Number) getLevel.invoke(data)).intValue(); dmzMax = ((Number) getMaxValue.invoke(data)).intValue(); } catch (Throwable ignored) {}
        }
        return new int[]{Math.max(1, dmz), Math.max(1, dmzMax), Math.max(1, xw), (sk[0] - 1) * 10 + sk[1]};
    }

    /** G = 10 x domeLevel x (1 + 4 fDmz) x (1 + 2 fXw) x (1 + fSkill), each f in 0..1; 10G..900G. */
    public static double formula(int dmzLevel, int dmzMax, int xwLevel, int ability, int domeLevel) {
        double fD = dmzMax <= 1 ? 0 : clamp01(Math.log(Math.max(1, dmzLevel)) / Math.log(dmzMax));
        double fX = clamp01(Math.log(Math.max(1, xwLevel)) / Math.log(XW_MAX_LEVEL));
        double fA = clamp01((ability - 1) / 99.0);
        double g = BASE_G * Math.max(1, Math.min(3, domeLevel)) * (1 + DMZ_SPAN * fD) * (1 + XW_SPAN * fX) * (1 + SKILL_SPAN * fA);
        return Math.max(MIN_G, Math.min(MAX_G, Math.round(g)));
    }

    private static double clamp01(double v) { return Double.isFinite(v) ? Math.max(0, Math.min(1, v)) : 0; }

    public static String label(double g) { return String.format(Locale.ROOT, "%.0fG", g); }

    // ------------------------------------------------------------------ DragonMineZ's gravity rules (its config, or its defaults)

    private static final Map<String, Method> CFG_GETTERS = new java.util.concurrent.ConcurrentHashMap<>();

    private static double c(String getter, double def) {
        try {
            Object o = cfg.invoke(null);
            if (o == null) return def;
            Method g = CFG_GETTERS.get(getter);
            if (g == null) { g = o.getClass().getMethod(getter); CFG_GETTERS.put(getter, g); }
            Object v = g.invoke(o);
            if (v instanceof Number n && Double.isFinite(n.doubleValue())) return n.doubleValue();
            if (v instanceof Boolean b) return b ? 1 : 0;
        } catch (Throwable ignored) {}
        return def;
    }

    /** DMZ's gravity is switched on (and its physical effects). */
    public static boolean dmzGravityOn() { return on() && c("isEnabled", 1) > 0 && c("getPhysicalEnabled", 1) > 0; }

    /** Players are handled by DragonMineZ itself (its gravity is on and our mixin is in). */
    public static boolean dmzHandlesPlayers() { return mixinLive && dmzGravityOn(); }

    public static double hardStop() { return c("getHardStopThreshold", 75); }

    /** DMZ's penalty curve: sqrt(net / 100) x 1.6. */
    public static double penaltyFactor(double net) { return net <= 0 ? 0 : Math.sqrt(net / 100.0) * c("getPenaltyCurveFactor", 1.6); }

    public static double movePenalty(double net) {
        double max = c("getMaxMovementPenalty", 0.95);
        if (net <= 0) return 0;
        return net >= hardStop() ? max : Math.min(max, penaltyFactor(net));
    }

    public static double jumpFactor(double net) {
        double max = c("getMaxJumpPenalty", 0.95);
        if (net <= 0) return 1;
        return 1 - (net >= hardStop() ? max : Math.min(max, penaltyFactor(net)));
    }

    public static double fallExtra(double net) { return net <= 0 ? 0 : Math.min(c("getMaxExtraFall", 0.6), net * c("getExtraFallPerGravity", 0.02)); }

    public static double statReduction(double net) {
        if (net <= 0) return 0;
        return Math.max(c("getMinStatReduction", 0), Math.min(c("getMaxStatReduction", 0.9), net * c("getStatReductionPerGravity", 0.01)));
    }

    /** DMZ resistance (in G) of this living thing: from its DMZ stats (players, bots), 0 for anything else. */
    public static double resistance(LivingEntity e) {
        if (!on()) return 0;
        Object data = statsOf(e);
        if (data == null) return 0;
        try {
            double avg = ((Number) avgStats.invoke(null, data)).doubleValue();
            int max = 10000;
            try { max = ((Number) maxValueCfg.invoke(gameplay.invoke(serverCfg.invoke(null)))).intValue(); } catch (Throwable ignored) {}
            double div = Math.max(1, max * c("getResistanceStatDivisorRatio", 0.9));
            double r = avg / div * c("getResistanceScale", 100);
            return Double.isFinite(r) ? Math.max(0, r) : 0;
        } catch (Throwable t) { return 0; }
    }

    /** Net gravity on this target from a dome of {@code g}: DMZ's "G - resistance" (0 at 1G or less). */
    public static double net(LivingEntity e, double g) { return g <= 1 ? 0 : Math.max(0, g - resistance(e)); }

    static Object statsOf(LivingEntity e) {
        if (!on() || e == null) return null;
        PartyBotEntity bot = PartyBots.botOf(e);
        if (bot != null) return e.level().isClientSide ? null : DmzBotKi.statsFor(bot);
        if (e instanceof Player p) return p.getCapability(cap).resolve().orElse(null);
        return null;
    }

    // ------------------------------------------------------------------ players: the dome's G handed to DragonMineZ (both sides)

    private record Mark(double g, long until) {}
    private static final Map<UUID, Mark> SERVER = new HashMap<>(), CLIENT = new HashMap<>();

    /** This player is under a dome of {@code g} for the next few ticks (each dome refreshes it every tick). */
    public static void mark(Player p, double g) {
        Map<UUID, Mark> m = p.level().isClientSide ? CLIENT : SERVER;
        long now = p.level().getGameTime();
        Mark old = m.get(p.getUUID());
        if (old != null && old.until >= now && old.g > g) return;        // (the heaviest dome wins)
        m.put(p.getUUID(), new Mark(g, now + 3));
    }

    /** The Kishi gravity on this player right now (0 = none). Called by the mixin for DragonMineZ. */
    public static double extraFor(Player p) {
        if (p == null) return 0;
        Map<UUID, Mark> m = p.level().isClientSide ? CLIENT : SERVER;
        Mark k = m.get(p.getUUID());
        if (k == null) return 0;
        if (k.until < p.level().getGameTime()) { m.remove(p.getUUID()); return 0; }
        return k.g;
    }

    /** Run DragonMineZ's gravity update for this player now (it only runs once a second by itself). */
    public static void refreshDmz(ServerPlayer p) {
        if (!on() || p == null) return;
        try { dmzTick.invoke(null, p); } catch (Throwable ignored) {}
        try { if (dmzSync != null) dmzSync.invoke(null, p); } catch (Throwable ignored) {}
    }

    /** Forget a player (left the dome early / dome gone) and let DragonMineZ drop the penalty now. */
    public static void release(ServerPlayer p) {
        SERVER.remove(p.getUUID());
        refreshDmz(p);
    }

    // ------------------------------------------------------------------ mobs and bots: DMZ's rules applied here

    /** Movement and attack penalties on a mob or bot for this net gravity (refreshed each tick it's inside). */
    public static void applyPenalties(LivingEntity e, double net) {
        set(e.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_ID, "Kishi gravity", -movePenalty(net));
        set(e.getAttribute(Attributes.ATTACK_DAMAGE), ATTACK_ID, "Kishi gravity", -statReduction(net));
    }

    public static void clearPenalties(LivingEntity e) {
        AttributeInstance a = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a != null) a.removeModifier(SPEED_ID);
        a = e.getAttribute(Attributes.ATTACK_DAMAGE);
        if (a != null) a.removeModifier(ATTACK_ID);
    }

    public static double speedModifier(LivingEntity e) {
        AttributeInstance a = e.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeModifier m = a == null ? null : a.getModifier(SPEED_ID);
        return m == null ? 0 : m.getAmount();
    }

    private static void set(AttributeInstance a, UUID id, String name, double amount) {
        if (a == null) return;
        AttributeModifier old = a.getModifier(id);
        if (old != null && Math.abs(old.getAmount() - amount) < 1e-4) return;
        if (old != null) a.removeModifier(id);
        if (amount < -1e-4) a.addTransientModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    /** Each second inside: stamina and ki drain from anyone with DMZ stats (min(25%, 0.3% x net) / min(15%, 0.15% x net) of max). */
    public static float[] drain(LivingEntity e, double net) {
        if (net <= 0) return null;
        Object data = statsOf(e);
        if (data == null) return null;
        try {
            Object res = getResources.invoke(data);
            float st = (float) (((Number) getMaxStamina.invoke(data)).doubleValue() * Math.min(0.25, net * 0.003));
            float ki = (float) (((Number) getMaxEnergy.invoke(data)).doubleValue() * Math.min(0.15, net * 0.0015));
            if (st > 0) removeStamina.invoke(res, st);
            if (ki > 0) removeEnergy.invoke(res, ki);
            return new float[]{st, ki};
        } catch (Throwable t) { return null; }
    }

    /** (self test / logs) the formula at a few points and whether DMZ's gravity hook is in. */
    public static String selfTest() {
        return "on=" + on() + " dmzGravity=" + dmzGravityOn() + " mixinLive=" + mixinLive
                + " new=" + label(formula(1, 10000, 1, 1, 1)) + " new3=" + label(formula(1, 10000, 1, 1, 3))
                + " mid=" + label(formula(100, 10000, 500, 15, 1)) + " max3=" + label(formula(10000, 10000, XW_MAX_LEVEL, 100, 3))
                + " move(10G)=" + String.format(Locale.ROOT, "%.2f", on() ? movePenalty(10) : -1);
    }
}
