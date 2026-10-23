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
 * With DragonMineZ: every Xeno Warriors skill hits as hard as the caster's DragonMineZ stats say. Energy skills
 * (Kishi, gravity, earth, fire, Blazing Wind) follow their Ki damage; physical ones (Kitsune, Netsu, Hekublade
 * Scatter) their strike damage. A fresh character stays at the normal damage; it grows with their stats and forms.
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

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        if (!ready() || !(event.getSource().getEntity() instanceof Player p)) return;
        var type = event.getSource().typeHolder().unwrapKey().orElse(null);
        if (type == null) return;
        boolean physical = PHYSICAL.contains(type);
        if (!physical && !ENERGY.contains(type)) return;
        event.setAmount((float) (event.getAmount() * multiplier(p, physical)));
    }
}
