package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.util.*;

/**
 * DragonMineZ's gravity, for bots and enemies too (DragonMineZ itself only weighs down players): the same world gravity
 * (its config per dimension) and King Kai's 10x around him, the same resistance from stats, and the same penalty curve.
 * Strong enough and they shrug it off; if not, they move slowly, can hardly jump and drop like stones - a Raditz on King
 * Kai's planet trudges. A bot's resistance comes from its own DragonMineZ stats, exactly as a player's; an enemy's from
 * its battle power (DragonMineZ's foes), or its health (anything else). All by reflection; without DragonMineZ, nothing.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DmzMobGravity {
    private DmzMobGravity() {}

    private static final UUID SPEED_ID = UUID.fromString("9e7c3b10-6a4d-4f2e-8c51-0d9a7e2b4f61"), FLY_ID = UUID.fromString("2d8f6a3c-1b7e-4c90-a5d2-7f3e9c1a6b08");
    private static Boolean ok;
    private static Object cfg;
    private static Method worldGravity, defaultGravity, npcValue, npcRange, divisorRatio, resistanceScale, hardStop, maxMove, maxJump, penaltyFactor,
            avgStats, playerBp, mobBp, maxValue, extraFall, maxFall;
    private static Class<?> kaioClass, bpClass;
    /** mob -> its penalty now (0-0.95), for its jumps and falls. */
    private static final Map<Integer, Double> PENALTY = new HashMap<>();
    /** Each level's King Kais (refreshed every 5 s). */
    private static final Map<ServerLevel, List<Vec3>> KAIS = new HashMap<>();
    private static long kaisAt;
    /** Stats per battle power, from a player online (how DragonMineZ's numbers line up); a guess until there's one. */
    private static double statsPerBp = 1.0 / 6.0;
    /** (Tests) the last numbers worked out: gravity, resistance, penalty. */
    public static String last = "";

    private static boolean ready() {
        if (ok != null) return ok;
        ok = false;
        if (ModList.get() == null || !ModList.get().isLoaded("dragonminez")) return false;
        try {
            Class<?> gl = Class.forName("com.dragonminez.server.util.GravityLogic");
            Method c = gl.getDeclaredMethod("cfg"); c.setAccessible(true);
            cfg = c.invoke(null);
            Class<?> gc = cfg.getClass();
            worldGravity = gc.getMethod("getWorldGravity", String.class);
            defaultGravity = gc.getMethod("getDefaultWorldGravity");
            npcValue = gc.getMethod("getNpcGravityValue");
            npcRange = gc.getMethod("getNpcGravityRange");
            divisorRatio = gc.getMethod("getResistanceStatDivisorRatio");
            resistanceScale = gc.getMethod("getResistanceScale");
            hardStop = gc.getMethod("getHardStopThreshold");
            maxMove = gc.getMethod("getMaxMovementPenalty");
            maxJump = gc.getMethod("getMaxJumpPenalty");
            extraFall = gc.getMethod("getExtraFallPerGravity");
            maxFall = gc.getMethod("getMaxExtraFall");
            penaltyFactor = gl.getMethod("getGeneralPenaltyFactor", double.class);
            Class<?> sd = Class.forName("com.dragonminez.common.stats.StatsData");
            avgStats = gl.getDeclaredMethod("avgEffectiveStats", sd); avgStats.setAccessible(true);
            playerBp = sd.getMethod("getBattlePowerExact");
            bpClass = Class.forName("com.dragonminez.common.init.entities.IBattlePower");
            mobBp = bpClass.getMethod("getBattlePower");
            Object gameplay = DmzBotQuests.m(Class.forName("com.dragonminez.common.config.ConfigManager").getMethod("getServerConfig").invoke(null), "getGameplay");
            maxValue = gameplay.getClass().getMethod("getMaxValue");
            Object mv = gameplay;
            maxValueOf = () -> { try { return ((Number) maxValue.invoke(mv)).doubleValue(); } catch (Throwable t) { return 10000.0; } };
            kaioClass = Class.forName("com.dragonminez.common.init.entities.AllMastersEntity$MasterKaiosamaEntity");
            ok = true;
        } catch (Throwable t) {
            XenoWarriors.LOGGER.warn("Bots and enemies can't feel DragonMineZ's gravity (its API changed?)", t);
        }
        return ok;
    }

    private static java.util.function.DoubleSupplier maxValueOf = () -> 10000.0;

    private static double d(Method m, Object... a) {
        try { Object v = m.invoke(cfg, a); return v instanceof Number n ? n.doubleValue() : Double.NaN; } catch (Throwable t) { return Double.NaN; }
    }

    private static boolean enabled() {
        try { return Boolean.TRUE.equals(cfg.getClass().getMethod("isEnabled").invoke(cfg)) && Boolean.TRUE.equals(cfg.getClass().getMethod("getPhysicalEnabled").invoke(cfg)); }
        catch (Throwable t) { return false; }
    }

    /** The gravity here: the world's (DragonMineZ's config), or King Kai's near him. */
    public static double gravityAt(LivingEntity e) {
        if (!ready() || !(e.level() instanceof ServerLevel l)) return 1;
        double g = d(worldGravity, l.dimension().location().toString());
        if (Double.isNaN(g)) g = d(defaultGravity);
        if (Double.isNaN(g)) g = 1;
        double range = d(npcRange), npc = d(npcValue);
        if (npc > g) for (Vec3 k : kais(l)) if (k.distanceToSqr(e.position()) < range * range) { g = npc; break; }
        return g;
    }

    private static List<Vec3> kais(ServerLevel l) {
        long now = l.getServer().getTickCount();
        if (now - kaisAt > 100) { KAIS.clear(); kaisAt = now; }
        return KAIS.computeIfAbsent(l, k -> {
            List<Vec3> out = new ArrayList<>();
            for (var en : l.getAllEntities()) if (kaioClass.isInstance(en)) out.add(en.position());
            return out;
        });
    }

    /** Resistance (0-100): from DragonMineZ stats like a player's - a bot's own; an enemy's battle power or health. */
    public static double resistance(LivingEntity e) {
        double avg;
        Object st = e instanceof PartyBotEntity b ? DmzBotKi.statsFor(b) : null;
        if (st != null) {
            try { avg = ((Number) avgStats.invoke(null, st)).doubleValue(); } catch (Throwable t) { avg = 0; }
        } else {
            double bp = 0;
            try { if (bpClass.isInstance(e)) bp = ((Number) mobBp.invoke(e)).doubleValue(); } catch (Throwable ignored) {}
            avg = bp > 0 ? bp * statsPerBp : Math.max(1, e.getMaxHealth()) / 4.0;   // (no battle power: from its health - a zombie about a new character's)
        }
        double div = Math.max(1, maxValueOf.getAsDouble() * d(divisorRatio));
        return Math.max(0, avg / div * d(resistanceScale));
    }

    /** The penalty (0 - its max): how much gravity this one can't hold up against. */
    public static double penalty(LivingEntity e) {
        double g = gravityAt(e);
        if (g <= 1) return 0;
        double r = resistance(e), net = Math.max(0, g - r);
        double max = d(maxMove), p;
        if (net <= 0) p = 0;
        else if (net >= d(hardStop)) p = max;
        else { try { p = Math.min(max, ((Number) penaltyFactor.invoke(null, net)).doubleValue()); } catch (Throwable t) { p = 0; } }
        last = String.format("%s gravity %.1f resistance %.2f -> net %.2f, penalty %.0f%%", e.getType().getDescriptionId(), g, r, net, p * 100);
        return p;
    }

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent ev) {
        LivingEntity e = ev.getEntity();
        if (e.level().isClientSide || e instanceof Player || !(e instanceof Mob) || e instanceof com.xenowarriors.kaiplanet.KaiBuilderEntity) return;
        Double p = PENALTY.get(e.getId());
        if (p != null && p > 0 && !e.onGround() && !e.isNoGravity() && !e.isInWater() && e.getDeltaMovement().y < 0.1)   // (heavy: drops like a stone)
            e.setDeltaMovement(e.getDeltaMovement().add(0, -Math.min(d(maxFall), d(extraFall) * 10 * p) * 0.08, 0));
        if ((e.tickCount + e.getId()) % 20 != 0 || !ready()) return;
        if (e.tickCount % 200 == 0) calibrate((ServerLevel) e.level());
        double pen = enabled() ? penalty(e) : 0;
        if (pen <= 0.001) { if (PENALTY.remove(e.getId()) != null) set(e, 0); return; }
        PENALTY.put(e.getId(), pen);
        set(e, pen);
    }

    private static void set(LivingEntity e, double pen) {
        for (var a : new AttributeInstance[]{e.getAttribute(Attributes.MOVEMENT_SPEED), e.getAttribute(Attributes.FLYING_SPEED)}) {
            if (a == null) continue;
            UUID id = a.getAttribute() == Attributes.MOVEMENT_SPEED ? SPEED_ID : FLY_ID;
            AttributeModifier old = a.getModifier(id);
            if (old != null && Math.abs(old.getAmount() + pen) < 0.005) continue;
            a.removeModifier(id);
            if (pen > 0) a.addTransientModifier(new AttributeModifier(id, "DragonMineZ gravity", -pen, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    /** Jumps: as weighed down as a player's (its jump penalty). */
    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent ev) {
        LivingEntity e = ev.getEntity();
        Double p = e.level().isClientSide ? null : PENALTY.get(e.getId());
        if (p == null || p <= 0) return;
        double keep = 1 - Math.min(d(maxJump), p);
        Vec3 v = e.getDeltaMovement();
        e.setDeltaMovement(v.x * keep, v.y * keep, v.z * keep);
    }

    /** How many stats a point of battle power is, from someone online (DragonMineZ's own numbers for them). */
    private static void calibrate(ServerLevel l) {
        for (ServerPlayer p : l.players()) {
            Object st = DmzPerCharacter.live(p);
            if (st == null) continue;
            try {
                double avg = ((Number) avgStats.invoke(null, st)).doubleValue(), bp = ((Number) playerBp.invoke(st)).doubleValue();
                if (avg > 0 && bp > 0) { statsPerBp = avg / bp; return; }
            } catch (Throwable ignored) {}
        }
    }

    @SubscribeEvent
    public static void onLeave(net.minecraftforge.event.entity.EntityLeaveLevelEvent e) { PENALTY.remove(e.getEntity().getId()); }

    /** (Tests) works it out now and applies it. */
    public static double applyForTest(LivingEntity e) { double p = ready() ? penalty(e) : -1; if (p > 0) { PENALTY.put(e.getId(), p); set(e, p); } return p; }
}
