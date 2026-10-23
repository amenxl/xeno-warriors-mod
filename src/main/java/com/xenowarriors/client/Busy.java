package com.xenowarriors.client;

import net.minecraft.world.entity.player.Player;

/**
 * Doing something that isn't "standing around": charging ki, blocking, a DragonMineZ technique, surfing, an Epic
 * Fight action... Idle animations (Mikiko's stretches and sitting, Rhalix's stick, sword inspecting, dances, the
 * hungry stomach rub) wait while any of this is going on, so they don't start over the top of it.
 */
public final class Busy {
    private Busy() {}

    private static final java.util.Map<Integer, Object[]> HELD = new java.util.HashMap<>();   // id -> {item, tick it changed}

    /** Just switched what's in hand (a weapon, another weapon, bare fists): idles start over from the beginning. */
    public static boolean justSwitched(Player p) {
        Object[] h = HELD.computeIfAbsent(p.getId(), k -> new Object[]{p.getMainHandItem().getItem(), -100});
        if (h[0] != p.getMainHandItem().getItem()) { h[0] = p.getMainHandItem().getItem(); h[1] = p.tickCount; }
        if (HELD.size() > 64) HELD.clear();
        return Math.abs(p.tickCount - (Integer) h[1]) < 3;
    }

    public static boolean busy(Player p) {
        return justSwitched(p) || com.xenowarriors.client.compat.DmzCompat.charging(p)
                || com.xenowarriors.client.compat.DmzCompat.blocking(p)
                || ChargePose.weight(p) > 0.02F
                || SurfPose.surfing(p)
                || ClientSkillState.currentAnim(p) != null
                || AttackHooks.externalBusy.test(p)
                || com.xenowarriors.client.mikiko.CombatStance.wanted(p) != com.xenowarriors.client.mikiko.CombatStance.NONE   // on guard
                || (efWeapon(p) && !DiscTracker.anyNear(p));
    }

    /** An Epic Fight weapon in hand: it has its own idle stance, so ours wait (dancing to music still happens). */
    public static boolean efWeapon(Player p) {
        return net.minecraftforge.fml.ModList.get().isLoaded("epicfight")
                && !com.xenowarriors.client.compat.epicfight.EpicFightCompat.ownWeapon(p);
    }
}
