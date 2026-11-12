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
 *   <li>Following your lead: when you transform (see BotForms), each bot near you transforms too a moment later if a
 *       fight is on or close by and it has the ki (no strong enemy needed), and stays in its form while you stay in
 *       yours. Out of a fight its power release follows yours (never under 50%).</li>
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
    // Kaioken (DragonMineZ's "stack" forms): optional
    private static Method setStack, clearStack, activeStack, selectedStackGroup, nextStack, ensureStack, healthDrain, activeFormData, formStackable, stackFormData;

    /** A bot's power state (kept while it's out; read into its DMZ copy whenever that's re-read). */
    static final class Power {
        float energy = -1;               // (fraction of its max ki; -1 = take its saved one)
        int release = BASE_RELEASE;
        String group, form;              // (null = base form)
        String stackGroup, stack;        // (Kaioken: null = none)
        boolean stackRested = true;      // (dropped it for low health: not again until it's back over 60%)
        float kaiokenDrained;
        boolean charging;
        long lastFight = -100000, chargedAt;
        double restMelee;
        String formShown = "";
        long followAt, followUntil = -1;  // (you just transformed: it follows your lead in this window, see BotForms)
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
            try {
                setStack = ch.getMethod("setActiveStackForm", String.class, String.class);
                clearStack = ch.getMethod("clearActiveStackForm");
                activeStack = ch.getMethod("getActiveStackForm");
                selectedStackGroup = ch.getMethod("getSelectedStackFormGroup");
                activeFormData = ch.getMethod("getActiveFormData");
                stackFormData = ch.getMethod("getActiveStackFormData");
                nextStack = th.getMethod("getNextAvailableStackForm", sd);
                ensureStack = th.getMethod("ensureSelectedStackFormDefault", sd);
                healthDrain = nextStack.getReturnType().getMethod("getHealthDrain");
                formStackable = nextStack.getReturnType().getMethod("getFormStackable");
            } catch (Throwable t) { setStack = null; XenoWarriors.LOGGER.warn("Bots won't use Kaioken (DragonMineZ's API changed?)", t); }
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
            if (clearStack != null) clearStack.invoke(ch);
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
        if (setStack != null) { if (p.stack != null) setStack.invoke(ch, p.stackGroup, p.stack); else clearStack.invoke(ch); }
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

    /** {ki it has, ki this technique costs} - the same sums as {@link #spend}, nothing spent; null = no ki rule. */
    static float[] kiCost(PartyBotEntity b, Object stats, Object tech) {
        if (!ready() || stats == null) return null;
        float max = num(maxEnergy, stats);
        if (max <= 0) return null;
        Power p = POWER.get(b.getUUID());
        float cost = techCost != null ? num(techCost, tech, stats) : max * 0.12F;
        if (!(cost > 0)) cost = max * 0.08F;
        return new float[]{(p == null || p.energy < 0 ? 1F : p.energy) * max, cost};
    }

    /** The ki a technique costs this character (DragonMineZ's getCalculatedCost; as {@link #spend} reckons it). */
    static float kiCostOf(Object tech, Object stats) {
        if (!ready() || stats == null) return 0;
        float max = num(maxEnergy, stats);
        float cost = techCost != null ? num(techCost, tech, stats) : max * 0.12F;
        return cost > 0 ? cost : max * 0.08F;
    }

    static float maxKi(Object stats) { return !ready() || stats == null ? 0 : num(maxEnergy, stats); }

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
        boolean following = now >= p.followAt && now < p.followUntil;
        boolean ownerFormed = playerForm(o) != null;
        int ownerRelease = playerRelease(o);
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
                if (p.form == null && foe != null && (strong(b, foe) || fighting) && p.energy > 0.15F) transform(b, o, st, p, "");
            } else {
                // power release: follows yours out of a fight (50% at least)
                int want = Math.max(BASE_RELEASE, Math.min(100, Math.round(ownerRelease / 5F) * 5));
                if (p.release < want) {
                    p.release = Math.min(want, p.release + 25);
                    aura(level, b, st, 6);
                    if (p.release == want) note(b.character().getId() + " powers up to " + want + "% with " + o.getGameProfile().getName());
                } else if (now - p.lastFight > CALM_TICKS && p.release > want) { p.release = want; note(b.character().getId() + " powers down to " + want + "%"); }
                if (now - p.lastFight > CALM_TICKS && p.form != null && !ownerFormed) revert(b, o, st, p, "the fight's over");
            }
            kaioken(b, o, st, p, inFight, inFight ? (danger != null ? danger : target) : null);
            // you transformed: it follows (a fight on or close by, and the ki for it - no strong enemy needed)
            if (following && p.form == null && p.energy > 0.08F && com.xenowarriors.bot.BotForms.nearCombat(b, o)) {
                if (transform(b, o, st, p, " following " + o.getGameProfile().getName())) p.followUntil = -1;
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
            } else if (!inFight && p.energy < 1F && !(b.dmzFly() && !b.naturalFlier() && DmzBotSkills.flying(b)))   // (no resting regen while it flies on its ki)
                p.energy = Math.min(1F, p.energy + Math.max(0.002F, num(regen, st, false) / 2F / max));
            write(p, st);
        } catch (Throwable t) { fail(t); }
    }

    private static boolean transform(PartyBotEntity b, ServerPlayer o, Object st, Power p, String why) throws Exception {
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
            return false;
        }
        p.group = lastGroup; p.form = last;
        double m = num(melee, st);
        note(b.character().getId() + " transforms into " + last + " (" + lastGroup + ")" + why + " kiDmg=" + Math.round(num(kiDamage, st) * 10) / 10.0
                + " melee x" + Math.round(m / Math.max(0.01, p.restMelee) * 100) / 100.0);
        if (b.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.FLASH, b.getX(), b.getY() + 1, b.getZ(), 1, 0, 0, 0, 0);
            aura(level, b, st, 40);
            level.playSound(null, b.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.NEUTRAL, 0.6F, 1.6F);
        }
        o.displayClientMessage(Component.literal("").append(b.label()).append(" transforms: " + pretty(last) + "!").withStyle(ChatFormatting.GOLD), true);
        return true;
    }

    // ---------------------------------------------------------------- Kaioken

    /** Health share it goes into Kaioken above, and drops it below. */
    static final float KAIOKEN_ON = 0.6F, KAIOKEN_OFF = 0.35F;

    /**
     * Kaioken (a DragonMineZ "stack" form, unlocked by its Kaioken skill level: x2 at level 1 ... x20 at level 5):
     * against a strong enemy with over 60% health it goes as high as its skill allows, the way DragonMineZ's stack-form
     * key steps a player up (getNextAvailableStackForm) - on top of a form only if that form is stackable. Every second
     * it costs the form's health drain (x2: 3% of its max health); under 35% health, or when the fight's over, it drops
     * it, and it doesn't go back in until it's over 60% again.
     */
    private static void kaioken(PartyBotEntity b, ServerPlayer o, Object st, Power p, boolean inFight, LivingEntity foe) throws Exception {
        if (setStack == null) return;
        float hp = b.getHealth() / Math.max(1F, b.getMaxHealth());
        if (hp >= KAIOKEN_ON) p.stackRested = true;
        Object ch = getCharacter.invoke(st);
        if (p.stack != null) {
            if (!inFight) { dropStack(b, st, p, "the fight's over"); return; }
            if (hp < KAIOKEN_OFF) { p.stackRested = false; dropStack(b, st, p, "low health " + Math.round(hp * 100) + "%"); o.displayClientMessage(Component.literal("").append(b.label()).append(" drops Kaioken").withStyle(ChatFormatting.GRAY), true); return; }
            if (b.tickCount % 20 < 10) {                                          // (once a second)
                Object fd = stackFormData.invoke(ch);
                double d = fd == null ? 0 : ((Number) healthDrain.invoke(fd)).doubleValue();
                if (d > 0) { b.setHealth(Math.max(1F, b.getHealth() - (float) (b.getMaxHealth() * d))); p.kaiokenDrained += (float) (b.getMaxHealth() * d); }
                if (b.level() instanceof ServerLevel level) level.sendParticles(new DustParticleOptions(new Vector3f(1F, 0.1F, 0.1F), 1.8F), b.getX(), b.getY() + 0.9, b.getZ(), 10, 0.4, 0.7, 0.4, 0.02);
            }
            return;
        }
        if (!inFight || foe == null || !strong(b, foe) || hp < KAIOKEN_ON || !p.stackRested) return;
        if (p.form != null) {                                                     // (on top of a form: only a stackable one)
            Object af = activeFormData.invoke(ch);
            Object ok = af == null ? null : formStackable.invoke(af);
            if (!(ok instanceof Boolean bo && bo)) return;
        }
        ensureStack.invoke(null, st);
        String group = null, last = null;
        for (int i = 0; i < 8; i++) {
            Object fd = nextStack.invoke(null, st);
            if (fd == null) break;
            String name = (String) formName.invoke(fd), g = (String) selectedStackGroup.invoke(ch);
            if (name == null || name.isEmpty() || name.equals(last)) break;
            setStack.invoke(ch, g, name);
            if (!name.equals(activeStack.invoke(ch))) break;
            group = g; last = name;
        }
        if (last == null) return;
        p.stackGroup = group; p.stack = last;
        kaiokens++;
        note(b.character().getId() + " goes Kaioken " + last + " (" + group + ") vs " + foe.getType().getDescriptionId() + " at " + Math.round(hp * 100) + "% health, melee x"
                + Math.round(num(melee, st) / Math.max(0.01, p.restMelee) * 100) / 100.0);
        if (b.level() instanceof ServerLevel level) {
            level.sendParticles(new DustParticleOptions(new Vector3f(1F, 0.1F, 0.1F), 2.2F), b.getX(), b.getY() + 0.9, b.getZ(), 50, 0.5, 0.9, 0.5, 0.05);
            level.playSound(null, b.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.NEUTRAL, 0.8F, 0.7F);
        }
        o.displayClientMessage(Component.literal("").append(b.label()).append(" Kaioken " + last + "!").withStyle(ChatFormatting.RED), true);
    }

    private static void dropStack(PartyBotEntity b, Object st, Power p, String why) throws Exception {
        clearStack.invoke(getCharacter.invoke(st));
        note(b.character().getId() + " drops Kaioken " + p.stack + " (" + why + ")");
        p.stack = null; p.stackGroup = null;
    }

    /** Its ki now, as a share of its max (-1 = not known yet). */
    static float kiFraction(PartyBotEntity b) { Power p = POWER.get(b.getUUID()); return p == null || p.energy < 0 ? -1 : p.energy; }

    /** Spends this share of its max ki (for flying); written into its DragonMineZ copy at its next half-second tick. */
    static void useKi(PartyBotEntity b, float share) { Power p = POWER.get(b.getUUID()); if (p != null && p.energy >= 0) p.energy = Math.max(0, p.energy - share); }

    /** (Tests) Kaioken uses so far. */
    public static int kaiokens;

    /** (Tests) its Kaioken form now (null = none) and the health it has cost it. */
    public static String kaiokenForTest(PartyBotEntity b) { Power p = POWER.get(b.getUUID()); return p == null ? null : p.stack; }
    public static float kaiokenDrainForTest(PartyBotEntity b) { Power p = POWER.get(b.getUUID()); return p == null ? 0 : p.kaiokenDrained; }

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
        if (p == null || p.restMelee <= 0 || (p.form == null && p.stack == null && p.release <= BASE_RELEASE)) return;
        Object st = stats(b);
        if (st == null) return;
        double ratio = Math.max(0.5, Math.min(6.0, num(melee, st) / p.restMelee));
        e.setAmount((float) (e.getAmount() * ratio));
    }

    /** {group, form} the bot is in right now, or null in its base form. */
    static String[] formOf(PartyBotEntity b) {
        Power p = POWER.get(b.getUUID());
        return p == null || p.form == null ? null : new String[]{p.group, p.form};
    }

    /** DragonMineZ's data for the form the bot's fighting copy is in (FormConfig.FormData), or null. */
    static Object activeFormData(PartyBotEntity b) {
        if (!ready() || activeFormData == null) return null;
        try { Object st = stats(b); return st == null ? null : activeFormData.invoke(getCharacter.invoke(st)); } catch (Throwable t) { return null; }
    }

    /** A bot is gone: forget it. */
    public static void forget(UUID bot) { POWER.remove(bot); }

    // ---------------------------------------------------------------- following the player

    /** You transformed (BotForms): this bot may follow into its best form between these game times. */
    public static void follow(PartyBotEntity b, long at, long until) {
        if (!ready()) return;
        Power p = POWER.computeIfAbsent(b.getUUID(), u -> new Power());
        p.followAt = at; p.followUntil = until;
    }

    /** The player's DragonMineZ form right now (null = base form, or no DragonMineZ). */
    public static String playerForm(ServerPlayer o) {
        if (o == null || !ready()) return null;
        try {
            Object d = DmzPerCharacter.live(o);
            if (d == null) return null;
            Object f = activeForm.invoke(getCharacter.invoke(d));
            String s = f == null ? "" : f.toString();
            return s.isEmpty() || s.equalsIgnoreCase("base") || s.equalsIgnoreCase("none") ? null : s;
        } catch (Throwable t) { return null; }
    }

    /** The player's DragonMineZ power release (%), or 0. */
    public static int playerRelease(ServerPlayer o) {
        if (o == null || !ready()) return 0;
        try {
            Object d = DmzPerCharacter.live(o);
            return d == null ? 0 : ((Number) getRelease.invoke(getResources.invoke(d))).intValue();
        } catch (Throwable t) { return 0; }
    }

    /** Its ki now and its most, or null (no DragonMineZ, no character read yet). */
    public static float[] ki(PartyBotEntity b) {
        if (!ready()) return null;
        Power p = POWER.get(b.getUUID());
        Object st = stats(b);
        if (p == null || st == null) return null;
        float max = num(maxEnergy, st);
        return max <= 0 ? null : new float[]{Math.max(0, p.energy) * max, max};
    }

    /** A player's DragonMineZ ki now and their most, or null. */
    public static float[] playerKi(ServerPlayer o) {
        if (o == null || !ready()) return null;
        try {
            Object d = DmzPerCharacter.live(o);
            if (d == null) return null;
            float max = num(maxEnergy, d);
            return max <= 0 ? null : new float[]{num(getEnergy, getResources.invoke(d)), max};
        } catch (Throwable t) { return null; }
    }

    /** Its DragonMineZ melee damage at rest (base form, 50% release - what its hits are scaled up from), or 0. */
    static double restMelee(PartyBotEntity b) { Power p = POWER.get(b.getUUID()); return p == null ? 0 : p.restMelee; }

    // ---------------------------------------------------------------- tests

    /** (Tests) "ki=..% release=..% form=.. charging=..". */
    public static String state(PartyBotEntity b) {
        Power p = POWER.get(b.getUUID());
        if (p == null) return "none";
        return "ki=" + pct(Math.max(0, p.energy)) + " release=" + p.release + "% form=" + p.form + " kaioken=" + p.stack + " charging=" + p.charging
                + (p.followUntil > 0 ? " follow=" + p.followAt + ".." + p.followUntil : "");
    }

    /** (Tests) sets its ki (fraction). */
    public static void setKiForTest(PartyBotEntity b, float f) { POWER.computeIfAbsent(b.getUUID(), u -> new Power()).energy = f; stats(b); }

    /** (Tests) a quiet moment: forget the last fight. */
    public static void calmForTest(PartyBotEntity b) { Power p = POWER.get(b.getUUID()); if (p != null) p.lastFight = -100000; }
}
