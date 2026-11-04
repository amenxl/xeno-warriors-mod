package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.damage.ModDamageTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * With DragonMineZ: every Xeno Warriors skill, ultimate, KA and Xeno Blast hits as hard as the caster's DragonMineZ
 * stats say, so a strong DragonMineZ character finds their Xeno Warriors moves about as strong as their ki attacks.
 * Energy skills (blasts, beams, gravity, fire, Xeno Blast...) follow their Ki damage; physical ones (kicks, slashes,
 * dashes, Kitsune, Netsu, Hekublade Scatter) their strike damage. A fresh character stays at the normal damage.
 *
 * Which hits are skill hits: the mod's own damage types, hits by the mod's skill entities, and anything dealt while a
 * skill runs (combat.SkillContext, which follows a skill into its later scheduled steps).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DmzScaling {
    /** DragonMineZ damage that counts as "normal" (x1); above it the skill scales up in proportion. */
    private static final double REFERENCE = 10.0;
    private static final Set<ResourceKey<DamageType>> PHYSICAL = Set.of(ModDamageTypes.KITSUNE, ModDamageTypes.NETSU, ModDamageTypes.HEKUBLADE_SCATTER);
    private static final Set<ResourceKey<DamageType>> ENERGY = Set.of(ModDamageTypes.KISHI, ModDamageTypes.EARTH_WAVE, ModDamageTypes.GRAVITY_COMPRESS,
            ModDamageTypes.BANG, ModDamageTypes.GRAVITY_BARRAGE, ModDamageTypes.FIRE_BLITZ, ModDamageTypes.BLAZING_WIND);
    private static Boolean loaded;
    private static boolean broken;
    private static Capability<?> cap;
    private static Method kiDamage, strikeDamage;

    private DmzScaling() {}

    private static boolean ready() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("dragonminez");
        if (!loaded || broken) return false;
        if (cap == null) {
            try {
                cap = (Capability<?>) Class.forName("com.dragonminez.common.stats.StatsCapability").getField("INSTANCE").get(null);
                Class<?> data = Class.forName("com.dragonminez.common.stats.StatsData");
                kiDamage = data.getMethod("getKiDamage");
                strikeDamage = data.getMethod("getStrikeDamage");
            } catch (Throwable t) {
                broken = true;
                XenoWarriors.LOGGER.warn("DragonMineZ stats not readable; Xeno Warriors skills won't scale with them", t);
                return false;
            }
        }
        return true;
    }

    /** The caster's skill damage multiplier for this kind of skill (1 = unchanged). */
    public static double multiplier(Player p, boolean physical) {
        if (!ready()) return 1.0;
        try {
            Object data = p.getCapability(cap).resolve().orElse(null);
            if (data == null) return 1.0;
            double v = ((Number) (physical ? strikeDamage : kiDamage).invoke(data)).doubleValue();
            return Double.isFinite(v) ? Math.max(1.0, v / REFERENCE) : 1.0;
        } catch (Throwable t) {
            return 1.0;
        }
    }

    /** How much stronger a group of players' DragonMineZ hits are on average (1 without it, capped at 50). */
    public static float partyScale(java.util.List<? extends Player> ps) {
        if (ps.isEmpty()) return 1F;
        double sum = 0;
        for (Player p : ps) sum += Math.max(multiplier(p, true), multiplier(p, false));
        return (float) Math.max(1.0, Math.min(50.0, sum / ps.size()));
    }

    /** Up to x40 a skill follows the DragonMineZ damage exactly; past that it keeps growing, more slowly, to x150. */
    public static final double SOFT_CAP = 40, HARD_CAP = 150;

    /** The curve: x1 for a fresh character, DMZ damage / 10 up to the soft cap, then slower growth, capped. */
    public static double curve(double ratio) {
        if (!Double.isFinite(ratio) || ratio <= 1) return 1.0;
        if (ratio <= SOFT_CAP) return ratio;
        return Math.min(HARD_CAP, SOFT_CAP + 6.0 * Math.sqrt(ratio - SOFT_CAP) + (ratio - SOFT_CAP) * 0.25);
    }

    /** The caster's skill damage multiplier (1 without DragonMineZ). */
    public static double skillMultiplier(Player p, boolean physical) {
        return curve(multiplier(p, physical));
    }

    /** The DragonMineZ figures that drive it: {ki damage, strike damage} (0s without DragonMineZ). */
    public static double[] raw(Player p) {
        if (!ready()) return new double[]{0, 0};
        try {
            Object data = p.getCapability(cap).resolve().orElse(null);
            if (data == null) return new double[]{0, 0};
            return new double[]{((Number) kiDamage.invoke(data)).doubleValue(), ((Number) strikeDamage.invoke(data)).doubleValue()};
        } catch (Throwable t) { return new double[]{0, 0}; }
    }

    private static final Set<String> MELEE_TYPES = Set.of("player_attack", "mob_attack", "mob_attack_no_aggro", "sting", "thorns");

    /** Is this a physical (strike) skill hit? Otherwise it's an energy (ki) one. */
    public static boolean physical(net.minecraft.world.damagesource.DamageSource src) {
        var type = src.typeHolder().unwrapKey().orElse(null);
        if (type == null) return false;
        if (PHYSICAL.contains(type)) return true;
        if (ENERGY.contains(type)) return false;
        return type.location().getNamespace().equals("minecraft") && MELEE_TYPES.contains(type.location().getPath());
    }

    /** How much this hit is scaled by (1 = left alone): only Xeno Warriors skill hits by a player with DragonMineZ stats. */
    public static double scaleFor(net.minecraft.world.damagesource.DamageSource src) {
        if (!ready() || !(src.getEntity() instanceof Player p)) return 1.0;
        if (com.xenowarriors.ka.DmzKi.isKi(src.getDirectEntity())) return 1.0;      // (DragonMineZ's own ki attacks scale themselves)
        var type = src.typeHolder().unwrapKey().orElse(null);
        if (type == null) return 1.0;
        boolean listed = PHYSICAL.contains(type) || ENERGY.contains(type);
        if (!listed) {
            if (!com.xenowarriors.combat.Allies.skillHit(src)) return 1.0;           // a plain swing: DragonMineZ already handles those
            var caster = com.xenowarriors.combat.SkillContext.caster();
            if (caster != null && com.xenowarriors.combat.Allies.actor(caster) != com.xenowarriors.combat.Allies.actor(p)) return 1.0;   // (someone else's hit while this skill runs)
        }
        return skillMultiplier(p, physical(src));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        double m = scaleFor(event.getSource());
        if (m != 1.0) event.setAmount((float) (event.getAmount() * m));
    }
}
