package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.util.*;

/**
 * With DragonMineZ: a bot teammate is as tough and as strong as its own DragonMineZ character (the one saved for that
 * character - see {@link DmzPerCharacter}; read through the copy DmzBotKi fights with), the way DragonMineZ makes a
 * player of that character:
 * <ul>
 *   <li>Health: DragonMineZ's health bonus ({@code getHealthBonus}, from Vitality - what it adds to a player's 20) on
 *       top of the bot's own level health. Forms that raise it raise it for the bot too.</li>
 *   <li>Defense: hits on it are cut by DragonMineZ's own damage mitigation ({@code calculatePostMitigationDamage},
 *       from Resistance), like hits on a player.</li>
 *   <li>Melee: its bare-hand damage becomes its DragonMineZ melee damage (Strength) when that's higher; full power and
 *       forms scale it up from there (DmzBotPower).</li>
 *   <li>Regeneration: out of a fight (not hurt for 6 s) it heals at DragonMineZ's health regen rate.</li>
 * </ul>
 * Refreshed every second, so level-ups, points spent in its Dragon Ball menu and TP show up at once. Its XW skills hit
 * with its DragonMineZ ki / strike damage too (DmzScaling). All by reflection; without DragonMineZ nothing changes.
 * <p>Also the Soul Splitter helpers: a player's live DragonMineZ health bonus, melee damage and mitigation.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DmzBotStats {
    private DmzBotStats() {}

    private static final UUID HP_ID = UUID.fromString("3e8c2b51-6a0f-4d7e-9c1b-0d7a5e2f4b61");
    private static final UUID DMG_ID = UUID.fromString("8a14f0c2-2b9d-4c6e-a3f7-5e1d9b0c7a42");
    private static final int REGEN_AFTER_HURT = 120;

    private static Boolean loaded;
    private static boolean broken;
    private static Method healthBonus, melee, mitigate, regen, kiDmg, strikeDmg, resources, getRelease, setRelease;
    private static Capability<?> cap;

    /** (For the tests) what happened, newest last. */
    public static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());
    private static final Map<UUID, String> LAST = new HashMap<>();

    private static void note(String s) {
        if (LOG.size() > 60) LOG.remove(0);
        LOG.add(s);
        XenoWarriors.LOGGER.info("XW dmz bot stats: {}", s);
    }

    static boolean ready() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("dragonminez");
        if (!loaded || broken) return false;
        if (mitigate != null) return true;
        try {
            Class<?> sd = Class.forName("com.dragonminez.common.stats.StatsData");
            healthBonus = sd.getMethod("getHealthBonus");
            melee = sd.getMethod("getMeleeDamage");
            regen = sd.getMethod("getHealthRegenPerSecond");
            cap = (Capability<?>) Class.forName("com.dragonminez.common.stats.StatsCapability").getField("INSTANCE").get(null);
            kiDmg = sd.getMethod("getKiDamage");
            strikeDmg = sd.getMethod("getStrikeDamage");
            resources = sd.getMethod("getResources");
            getRelease = resources.getReturnType().getMethod("getPowerRelease");
            setRelease = resources.getReturnType().getMethod("setPowerRelease", int.class);
            mitigate = sd.getMethod("calculatePostMitigationDamage", double.class, boolean.class, double.class);
            return true;
        } catch (Throwable t) {
            broken = true; mitigate = null;
            XenoWarriors.LOGGER.warn("Bots can't use their DragonMineZ health / defense (its API changed?)", t);
            return false;
        }
    }

    private static double num(Method m, Object o, Object... a) {
        try { double v = ((Number) m.invoke(o, a)).doubleValue(); return Double.isFinite(v) ? v : 0; } catch (Throwable t) { return 0; }
    }

    // ---------------------------------------------------------------- bots

    /** The bot's stats now (spawned, levelled, its DMZ character changed). Without DragonMineZ: nothing. */
    public static void apply(PartyBotEntity b) {
        if (!ready() || b.level().isClientSide) return;
        Object st = DmzBotKi.statsFor(b);
        AttributeInstance hp = b.getAttribute(Attributes.MAX_HEALTH), dmg = b.getAttribute(Attributes.ATTACK_DAMAGE);
        if (hp == null || dmg == null) return;
        double bonus = st == null ? 0 : Math.max(0, num(healthBonus, st));
        double rest = st == null ? 0 : DmzBotPower.restMelee(b);
        double add = Math.max(0, rest - dmg.getBaseValue());
        float before = b.getMaxHealth();
        set(hp, HP_ID, "DragonMineZ health", bonus);
        set(dmg, DMG_ID, "DragonMineZ melee", add);
        if (b.getHealth() > b.getMaxHealth()) b.setHealth(b.getMaxHealth());
        if (b.tickCount < 5 && before < b.getMaxHealth()) b.setHealth(b.getMaxHealth());   // (just out: at full health)
        String now = Math.round(b.getMaxHealth()) + "/" + Math.round(dmg.getValue() * 10) / 10.0;
        if (!now.equals(LAST.put(b.getUUID(), now)))
            note(b.character().getId() + " maxHp " + Math.round(before) + " -> " + Math.round(b.getMaxHealth()) + " (dmz +" + Math.round(bonus) + ", a player of it: "
                    + Math.round(20 + bonus) + " + XW) melee " + Math.round(dmg.getValue() * 10) / 10.0 + " (dmz rest melee " + Math.round(rest * 10) / 10.0 + ")");
    }

    private static void set(AttributeInstance a, UUID id, String name, double v) {
        AttributeModifier old = a.getModifier(id);
        if (old != null && Math.abs(old.getAmount() - v) < 1.0E-4) return;
        a.removeModifier(id);
        if (v > 0) a.addTransientModifier(new AttributeModifier(id, name, v, AttributeModifier.Operation.ADDITION));
    }

    /** Its DragonMineZ health regen per second (0 without). */
    public static double regenFor(PartyBotEntity b) {
        if (!ready()) return 0;
        Object st = DmzBotKi.statsFor(b);
        return st == null ? 0 : Math.max(0, num(regen, st));
    }

    /** A hit of {@code amount} on the bot after DragonMineZ's mitigation (unchanged without its DMZ character). */
    public static float mitigated(PartyBotEntity b, float amount) {
        if (!ready()) return amount;
        Object st = DmzBotKi.statsFor(b);
        if (st == null) return amount;
        return mit(st, amount);
    }

    /** DragonMineZ's mitigation on this character's data; the hit unchanged if it fails. */
    private static float mit(Object st, float amount) {
        try {
            double v = ((Number) mitigate.invoke(st, (double) amount, false, 0.0)).doubleValue();
            return Double.isFinite(v) && v >= 0 && v <= amount ? (float) v : amount;
        } catch (Throwable t) { return amount; }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer o) || o instanceof FakePlayer || o.tickCount % 20 != 11 || !ready()) return;
        for (PartyBotEntity b : PartyBots.bots(o)) {
            apply(b);
            if (b.downed() || !b.isAlive() || b.getHealth() >= b.getMaxHealth() || b.tickCount - b.lastHurtAt < REGEN_AFTER_HURT) continue;
            double r = regenFor(b);
            if (r > 0) b.heal((float) r);
        }
    }

    /** DragonMineZ's defense for hits on a bot (after the other changes to the hit). Hits from nothing (falls, fire...) as before. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent e) {
        if (!(e.getEntity() instanceof PartyBotEntity b) || e.getEntity().level().isClientSide || e.getSource().getEntity() == null || !ready()) return;
        float a = e.getAmount(), m = mitigated(b, a);
        if (m != a) {
            e.setAmount(m);
            if (TEST_LOG) note(b.character().getId() + " hit " + Math.round(a * 100) / 100.0 + " -> " + Math.round(m * 100) / 100.0 + " (DragonMineZ defense)");
        }
    }

    /** (Tests) log every mitigated hit. */
    public static boolean TEST_LOG;

    public static void forget(UUID bot) { LAST.remove(bot); }

    // ---------------------------------------------------------------- a player's own (Soul Splitter)

    private static Object data(Player p) {
        if (!ready() || p == null) return null;
        try { return p.getCapability(cap).resolve().orElse(null); } catch (Throwable t) { return null; }
    }

    /** Bots fight from this power release at rest (DmzBotPower); a soul's share is read at least there too. */
    public static final int REST_RELEASE = 50;

    /**
     * {DragonMineZ health bonus, melee damage, ki damage, strike damage} of this player's live character, the damage
     * read at their power release or {@link #REST_RELEASE}%, whichever is higher (a fresh player idles at a few %).
     * {0, 0, 0, 0} without DragonMineZ.
     */
    public static double[] playerStats(Player p) {
        Object d = data(p);
        if (d == null) return new double[]{0, 0, 0, 0};
        Object res = null;
        int was = -1;
        try {
            res = resources.invoke(d);
            was = ((Number) getRelease.invoke(res)).intValue();
            if (was < REST_RELEASE) setRelease.invoke(res, REST_RELEASE);              // (put straight back below)
        } catch (Throwable t) { was = -1; }
        try {
            return new double[]{Math.max(0, num(healthBonus, d)), Math.max(0, num(melee, d)), Math.max(0, num(kiDmg, d)), Math.max(0, num(strikeDmg, d))};
        } finally {
            if (was >= 0 && was < REST_RELEASE) try { setRelease.invoke(res, was); } catch (Throwable ignored) {}
        }
    }

    /** A hit of {@code amount} after this player's DragonMineZ mitigation (unchanged without). */
    public static float playerMitigated(Player p, float amount) {
        Object d = data(p);
        return d == null ? amount : mit(d, amount);
    }
}
