package com.xenowarriors.client;

import net.minecraft.world.entity.player.Player;

/**
 * Doing something that isn't "standing around": charging ki, blocking, a DragonMineZ technique, surfing, an Epic
 * Fight action... Idle animations (Mikiko's stretches and sitting, Rhalix's stick, sword inspecting, dances, the
 * hungry stomach rub) wait while any of this is going on, so they don't start over the top of it.
 */
public final class Busy {
    private Busy() {}

    public static boolean busy(Player p) {
        return com.xenowarriors.client.compat.DmzCompat.charging(p)
                || com.xenowarriors.client.compat.DmzCompat.blocking(p)
                || ChargePose.weight(p) > 0.02F
                || SurfPose.surfing(p)
                || ClientSkillState.currentAnim(p) != null
                || AttackHooks.externalBusy.test(p);
    }
}
