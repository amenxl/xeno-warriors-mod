package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.util.*;

/**
 * With DragonMineZ: a bot's ki, power release and form, run off its own DragonMineZ character (the copy DmzBotKi
 * fights with):
 * <ul>
 *   <li>Ki: its ki techniques cost what DragonMineZ says they cost ({@code getCalculatedCost}); out of ki, it can't
 *       throw them. Low on ki (under 35%) and safe, it stops and charges - the charging pose with its aura, refilling
 *       at DragonMineZ's charging rate ({@code getEnergyRegenPerSecond(true)}) until full. Out of a fight it slowly
 *       refills at the normal rate.</li>
 *   <li>Full power: when a fight starts (it picks a target, or something strong comes close) it powers up to 100%
 *       release, which DragonMineZ's damage formulas use. After a quiet spell it eases back to 50%.</li>
 *   <li>Transforming: when a fight starts or a strong enemy is near (a boss, a DragonMineZ story fighter, anything
 *       tougher than it), it goes into the best form its character has unlocked (DragonMineZ's own next-form rules,
 *       stepped up as far as they go). Forms drain ki like they do for players; out of ki, or once the fight is over,
 *       it reverts.</li>
 *   <li>Its melee hits scale with its DragonMineZ melee damage the same way: stronger in a form / at full power.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DmzBotPower {
    private DmzBotPower() {}

    public static final float LOW = 0.35F;
    private static final int BASE_RELEASE = 50, CALM_TICKS = 200;

    private static Boolean loaded;
    private static boolean broken;
    private static Method getResources, getCharacter, maxEnergy, regen, drain, melee, kiDamage, getEnergy, setEnergy, getRelease, setRelease;
    private static Method setActiveForm, clearActiveForm, activeForm, activeGroup, auraColor, nextForm, targetGroup, formName, techCost, ensureSelected;

    /** A bot's power state (kept while it's out; read into its DMZ copy whenever that's re-read). */
    static final class Power {
        float energy = -1;               // (fraction of its max ki; -1 = take its saved one)
        int release = BASE_RELEASE;
        String group, form;              // (null = base form)
        boolean charging;
        long lastFight = -100000, chargedAt;
        double restMelee;
        String formShown = "";
    }

    private static final Map<UUID, Power> POWER = new HashMap<>();

    /** (For the tests) what happened, newest last. */
    public static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());

    private static void note(String s) {
        if (LOG.size() > 60) LOG.remove(0);
        LOG.add(s);
        XenoWarriors.LOGGER.info("XW dmz bot power: {}", s);
    }

    static boolean ready() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("dragonminez");
        if (!loaded || broken) return false;
        if (setRelease != null) return true;
        try {
            Class<?> sd = Class.forName("com.dragonminez.common.stats.StatsData");
            getResources = sd.getMethod("getResources");
            getCharacter = sd.getMethod("getCharacter");
            maxEnergy = sd.getMethod("getMaxEnergy");
            regen = sd.getMethod("getEnergyRegenPerSecond", boolean.class);
            drain = sd.getMethod("getAdjustedEnergyDrain");
            melee = sd.getMethod("getMeleeDamage");
            kiDamage = sd.getMethod("getKiDamage");
            Class<?> r = getResources.getReturnType();
            getEnergy = r.getMethod("getCurrentEnergy");
            setEnergy = r.getMethod("setCurrentEnergy", float.class);
            getRelease = r.getMethod("getPowerRelease");
            Class<?> ch = getCharacter.getReturnType();
            setActiveForm = ch.getMethod("setActiveForm", String.class, String.class);
            clearActiveForm = ch.getMethod("clearActiveForm");
            activeForm = ch.getMethod("getActiveForm");
            activeGroup = ch.getMethod("getActiveFormGroup");
            auraColor = ch.getMethod("getAuraColor");
            Class<?> th = Class.forName("com.dragonminez.common.util.TransformationsHelper");
            nextForm = th.getMethod("getNextAvailableForm", sd);
            targetGroup = th.getMethod("getTransformTargetGroup", sd);
            formName = nextForm.getReturnType().getMethod("getName");
            try { ensureSelected = th.getMethod("ensureSelectedFormDefault", sd); } catch (Throwable ignored) {}
            try { techCost = Class.forName("com.dragonminez.common.stats.techniques.KiAttackData").getMethod("getCalculatedCost", sd); } catch (Throwable ignored) {}
            setRelease = r.getMethod("setPowerRelease", int.class);
            return true;
        } catch (Throwable t) {
            broken = true; setRelease = null;
            XenoWarriors.LOGGER.warn("Bots' DragonMineZ ki / transformations off (its API changed?)", t);
            return false;
        }
    }

    private static float num(Method m, Object o, Object... a) {
        try { return ((Number) m.invoke(o, a)).floatValue(); } catch (Throwable t) { return 0; }
    }

    // ---------------------------------------------------------------- the bot's DMZ copy

    /** DmzBotKi just (re)read the bot's DMZ character into this copy: put its current ki, release and form into it. */
    static void loaded(PartyBotEntity b, Object stats) {
        if (!ready() || stats == null) return;
        Power p = POWER.computeIfAbsent(b.getUUID(), u -> new Power());
        try {
            Object res = getResources.invoke(stats), ch = getCharacter.invoke(stats);
            clearActiveForm.invoke(ch);
            setRelease.invoke(res, BASE_RELEASE);
            p.restMelee = num(melee, stats);
            float max = num(maxEnergy, stats);
            if (p.energy < 0) p.energy = max > 0 ? Math.min(1F, num(getEnergy, res) / max) : 1F;
            if (p.energy <= 0.001F && num(getEnergy, res) <= 0) p.energy = 1F;      // (a fresh character: a full tank)
            write(p, stats);
        } catch (Throwable t) { fail(t); }
    }

    private static void write(Power p, Object stats) throws Exception {
        Object res = getResources.invoke(stats), ch = getCharacter.invoke(stats);
        setEnergy.invoke(res, p.energy * num(maxEnergy, stats));
        setRelease.invoke(res, p.release);
        if (p.form != null) setActiveForm.invoke(ch, p.group, p.form); else clearActiveForm.invoke(ch);
    }

    private static void fail(Throwable t) {
        if (broken) return;
        broken = true;
        XenoWarriors.LOGGER.warn("Bots' DragonMineZ ki / transformations stopped (it didn't work)", t);
    }

    private static Object stats(PartyBotEntity b) { return DmzBotKi.statsFor(b); }

    /** Pays for a ki technique from the bot's ki. False (and nothing spent) if it hasn't enough. */
    static boolean spend(PartyBotEntity b, Object stats, Object tech, String id) {
        if (!ready() || stats == null) return true;
        Power p = POWER.computeIfAbsent(b.getUUID(), u -> new Power());
        float max = num(maxEnergy, stats);
        if (max <= 0) return true;
        float cost = techCost != null ? num(techCost, tech, stats) : max * 0.12F;
        if (!(cost > 0)) cost = max * 0.08F;
        float have = p.energy * max;
        if (have < cost) { note(b.character().getId() + " too little ki for " + id + " (" + Math.round(have) + "/" + Math.round(cost) + ")"); return false; }
        p.energy = (have - cost) / max;
        try { write(p, stats); } catch (Throwable t) { fail(t); }
        note(b.character().getId() + " spent " + Math.round(cost) + " ki on " + id + " -> " + pct(p.energy));
        return true;
    }

    private static String pct(float f) { return Math.round(f * 100) + "%"; }

    // ---------------------------------------------------------------- fights

    /** Something to power up for: a boss, a DragonMineZ story fighter, a player, or anything tougher than the bot. */
    static boolean strong(PartyBotEntity b, LivingEntity t) {
        if (t == null || !t.isAlive()) return false;
        if (t instanceof Player) return true;
        if (t.getPersistentData().getBoolean("xw_boss") || t.getPersistentData().getBoolean("xw_miniboss") || t.getTags().contains("xw_miniboss")) return true;
        var id = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(t.getType());
        if (id != null && id.getNamespace().equals("dragonminez") && id.getPath().startsWith("saga_")) return true;
        return t.getMaxHealth() >= Math.max(60F, b.getMaxHealth());
    }

    /** A strong hostile close by (coming for the party or just there). */
    static LivingEntity threat(PartyBotEntity b, ServerPlayer o) {
        for (Mob m : b.level().getEntitiesOfClass(Mob.class, b.getBoundingBox().inflate(24), m -> m.isAlive() && (m instanceof Enemy || m.getTarget() == b || m.getTarget() == o)))
            if (!(m instanceof PartyBotEntity) && strong(b, m) && (m.getTarget() == b || m.getTarget() == o || m.distanceTo(b) < 16)) return m;
        return null;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer o) || o instanceof FakePlayer || o.tickCount % 10 != 5 || !ready()) return;
        for (PartyBotEntity b : PartyBots.bots(o)) {
            if (b.downed()) { Power p = POWER.get(b.getUUID()); if (p != null) p.charging = false; continue; }
            tick(b, o);
        }
    }

    /** Every half second, per bot. */
    static void tick(PartyBotEntity b, ServerPlayer o) {
        Object st = stats(b);
        if (st == null || !(b.level() instanceof ServerLevel level)) return;
        Power p = POWER.computeIfAbsent(b.getUUID(), u -> new Power());
        long now = level.getGameTime();
        LivingEntity target = b.getTarget();
        boolean fighting = target != null && target.isAlive() && !b.sparring();
        LivingEntity danger = threat(b, o);
        if (fighting || danger != null || (b.getLastHurtByMob() != null && b.tickCount - b.getLastHurtByMobTimestamp() < 60)) p.lastFight = now;
        boolean inFight = now - p.lastFight < 40;
        try {
            float max = num(maxEnergy, st);
            if (max <= 0) return;
            // power up for a fight
            if (inFight) {
                if (p.charging) { p.charging = false; note(b.character().getId() + " stops charging - a fight"); }
                if (p.release < 100) {
                    p.release = Math.min(100, p.release + 25);
                    aura(level, b, st, 10);
                    if (p.release == 100) {
                        level.playSound(null, b.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 0.7F, 1.6F);
                        note(b.character().getId() + " full power (100%) for " + (target != null ? target.getType().getDescriptionId() : danger != null ? danger.getType().getDescriptionId() : "a fight"));
                    }
                }
                LivingEntity foe = danger != null ? danger : target;
                if (p.form == null && foe != null && (strong(b, foe) || fighting) && p.energy > 0.15F) transform(b, o, st, p);
            } else if (now - p.lastFight > CALM_TICKS) {
                if (p.form != null) revert(b, o, st, p, "the fight's over");
                if (p.release > BASE_RELEASE) { p.release = BASE_RELEASE; note(b.character().getId() + " powers down to " + BASE_RELEASE + "%"); }
            }
            // ki: forms drain it, charging refills it fast, resting slowly
            if (p.form != null) {
                p.energy -= (float) (Math.max(0, num(drain, st)) / 2.0 / max);
                if (p.energy <= 0) { p.energy = 0; revert(b, o, st, p, "out of ki"); }
            }
            boolean safe = !inFight && DmzDragonBalls.calm(b, o);
            if (!p.charging && safe && p.energy < LOW) {
                p.charging = true; p.chargedAt = now;
                note(b.character().getId() + " charges ki at " + pct(p.energy) + " (rate " + Math.round(num(regen, st, true)) + "/s)");
                o.displayClientMessage(Component.literal("").append(b.label()).append(" is charging ki").withStyle(ChatFormatting.YELLOW), true);
            }
            if (p.charging) {
                if (!safe) p.charging = false;
                else {
                    b.getNavigation().stop();
                    com.xenowarriors.anim.Poses.play(b, com.xenowarriors.anim.Poses.EL_CHARGE, 14);
                    aura(level, b, st, 6);
                    if (b.tickCount % 20 < 10) level.playSound(null, b.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.NEUTRAL, 0.5F, 1.8F);
                    p.energy = Math.min(1F, p.energy + Math.max(0.02F, num(regen, st, true) / 2F / max));
                    if (p.energy >= 1F) {
                        p.charging = false;
                        note(b.character().getId() + " fully charged after " + (now - p.chargedAt) / 20 + " s");
                    }
                }
            } else if (!inFight && p.energy < 1F) p.energy = Math.min(1F, p.energy + Math.max(0.002F, num(regen, st, false) / 2F / max));
            write(p, st);
        } catch (Throwable t) { fail(t); }
    }

    private static void transform(PartyBotEntity b, ServerPlayer o, Object st, Power p) throws Exception {
        Object ch = getCharacter.invoke(st);
        if (ensureSelected != null) ensureSelected.invoke(null, st);           // (a form picked to go into, as DragonMineZ does for players: the first one it has)
        String lastGroup = null, last = null;
        for (int i = 0; i < 10; i++) {                                         // (step up as far as its unlocked forms go)
            Object fd = nextForm.invoke(null, st);
            if (fd == null) break;
            String name = (String) formName.invoke(fd), group = (String) targetGroup.invoke(null, st);
            if (name == null || name.isEmpty() || name.equals(last)) break;
            setActiveForm.invoke(ch, group, name);
            if (!name.equals(activeForm.invoke(ch))) break;
            lastGroup = (String) activeGroup.invoke(ch); last = name;
        }
        if (last == null) {
            if (!"none".equals(p.formShown)) { p.formShown = "none"; note(b.character().getId() + " has no form to transform into"); }
            return;
        }
        p.group = lastGroup; p.form = last;
        double m = num(melee, st);
        note(b.character().getId() + " transforms into " + last + " (" + lastGroup + ") kiDmg=" + Math.round(num(kiDamage, st) * 10) / 10.0
                + " melee x" + Math.round(m / Math.max(0.01, p.restMelee) * 100) / 100.0);
        if (b.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.FLASH, b.getX(), b.getY() + 1, b.getZ(), 1, 0, 0, 0, 0);
            aura(level, b, st, 40);
            level.playSound(null, b.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.NEUTRAL, 0.6F, 1.6F);
        }
        o.displayClientMessage(Component.literal("").append(b.label()).append(" transforms: " + pretty(last) + "!").withStyle(ChatFormatting.GOLD), true);
    }

    private static void revert(PartyBotEntity b, ServerPlayer o, Object st, Power p, String why) throws Exception {
        clearActiveForm.invoke(getCharacter.invoke(st));
        note(b.character().getId() + " reverts from " + p.form + " (" + why + ")");
        p.form = null; p.group = null;
    }

    static String pretty(String id) {
        StringBuilder sb = new StringBuilder();
        for (String w : id.replace('_', ' ').split(" ")) if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        return sb.toString().trim();
    }

    /** Its aura: DragonMineZ's aura color for its character, rising round it. */
    private static void aura(ServerLevel level, PartyBotEntity b, Object st, int n) {
        int rgb = 0xFFE27A;
        try { String c = String.valueOf(auraColor.invoke(getCharacter.invoke(st))).replace("#", ""); if (!c.isEmpty() && !c.equals("null")) rgb = Integer.parseInt(c, 16); } catch (Throwable ignored) {}
        Power p = POWER.get(b.getUUID());
        if (p != null && p.form != null && p.form.toLowerCase(Locale.ROOT).contains("super")) rgb = 0xFFE040;
        var dust = new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F), 1.6F);
        level.sendParticles(dust, b.getX(), b.getY() + 0.9, b.getZ(), n, 0.35, 0.7, 0.35, 0.02);
        level.sendParticles(ParticleTypes.END_ROD, b.getX(), b.getY() + 0.2, b.getZ(), Math.max(1, n / 5), 0.3, 0.1, 0.3, 0.05);
    }

    /** Its melee hits follow its DragonMineZ melee damage: full power and forms hit harder (ki attacks already do). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent e) {
        if (!(e.getSource().getEntity() instanceof PartyBotEntity b) || e.getSource().getDirectEntity() != b || !ready()) return;
        Power p = POWER.get(b.getUUID());
        if (p == null || p.restMelee <= 0 || (p.form == null && p.release <= BASE_RELEASE)) return;
        Object st = stats(b);
        if (st == null) return;
        double ratio = Math.max(0.5, Math.min(6.0, num(melee, st) / p.restMelee));
        e.setAmount((float) (e.getAmount() * ratio));
    }

    /** A bot is gone: forget it. */
    public static void forget(UUID bot) { POWER.remove(bot); }

    // ---------------------------------------------------------------- tests

    /** (Tests) "ki=..% release=..% form=.. charging=..". */
    public static String state(PartyBotEntity b) {
        Power p = POWER.get(b.getUUID());
        if (p == null) return "none";
        return "ki=" + pct(Math.max(0, p.energy)) + " release=" + p.release + "% form=" + p.form + " charging=" + p.charging;
    }

    /** (Tests) sets its ki (fraction). */
    public static void setKiForTest(PartyBotEntity b, float f) { POWER.computeIfAbsent(b.getUUID(), u -> new Power()).energy = f; stats(b); }

    /** (Tests) a quiet moment: forget the last fight. */
    public static void calmForTest(PartyBotEntity b) { Power p = POWER.get(b.getUUID()); if (p != null) p.lastFight = -100000; }
}
