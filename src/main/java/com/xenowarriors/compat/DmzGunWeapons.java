package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * DragonMineZ's ranged weapons - the Blaster Cannon and Merus' Laser - count as guns: their shots train your gun level
 * (XP = damage dealt, a bonus per kill) and hit harder the higher it is (the same bonus as every gun: +0.2% a level,
 * up to +150%; Technica's +25% on top). Their shots are ki blasts like a ki attack's, so the ones fired from the
 * weapon are marked the moment they appear (fired by someone who just used one of these weapons) - plain ki attacks
 * don't count.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DmzGunWeapons {
    private DmzGunWeapons() {}

    public static final Set<String> WEAPONS = Set.of("dragonminez:blaster_cannon", "dragonminez:laser_merus");
    private static final String TAG = "xw_dmz_gun_shot";
    /** player -> game time they last used one of the weapons */
    private static final Map<UUID, Long> FIRED = new HashMap<>();
    /** (Tests) shots marked. */
    public static int marked;

    public static boolean isWeapon(net.minecraft.world.item.ItemStack s) {
        return !s.isEmpty() && WEAPONS.contains(String.valueOf(ForgeRegistries.ITEMS.getKey(s.getItem())));
    }

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickItem e) {
        if (e.getEntity() instanceof ServerPlayer p && isWeapon(e.getItemStack())) FIRED.put(p.getUUID(), p.level().getGameTime());
    }

    @SubscribeEvent
    public static void onShot(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof Projectile pr) || !(pr.getOwner() instanceof ServerPlayer p)) return;
        Long at = FIRED.get(p.getUUID());
        if (at == null || e.getLevel().getGameTime() - at > 1) return;
        if (!pr.getClass().getName().startsWith("com.dragonminez")) return;
        pr.getPersistentData().putBoolean(TAG, true);
        marked++;
    }

    static boolean gunShot(Entity direct) { return direct != null && direct.getPersistentData().getBoolean(TAG); }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent e) {
        if (!gunShot(e.getSource().getDirectEntity()) || !(e.getSource().getEntity() instanceof ServerPlayer p) || p == e.getEntity()) return;
        e.setAmount(e.getAmount() * GunLevel.multiplier(p));
        GunLevel.addXp(p, Math.min(e.getAmount(), e.getEntity().getHealth() + 1F));
    }

    @SubscribeEvent
    public static void onKill(LivingDeathEvent e) {
        if (gunShot(e.getSource().getDirectEntity()) && e.getSource().getEntity() instanceof ServerPlayer p) GunLevel.addXp(p, GunLevel.KILL_XP);
    }

    /** (Tests) marks a projectile as fired from one of the weapons. */
    public static void markForTest(Entity shot) { shot.getPersistentData().putBoolean(TAG, true); }
}
