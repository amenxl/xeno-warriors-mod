package com.xenowarriors.anim;

import com.xenowarriors.network.EntityPosePacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Body poses for anyone (players and NPCs): short "action" poses (throw, chop, KA cast...) and lasting "state" poses
 * (clashing, bound, heavy, sliding). Sent to everyone who can see them; drawn by client/EntityPoses. NPCs that Epic
 * Fight animates get the closest Epic Fight animation instead (compat/epicfight/EfPoses).
 */
public final class Poses {
    // lasting states
    public static final int CLEAR = 0, CLASH = 1, BOUND = 2, HEAVY = 3, SLIDING = 4;
    // actions
    public static final int EL_THROW = 10, EL_THRUST = 11, EL_STOMP = 12, EL_RAISE = 13, EL_CHARGE = 14, KA_CAST = 15, KA_BURNOUT = 16,
            HOLD_JOLT = 17, CLASH_WIN = 18, CLASH_LOSE = 19, MIRROR = 20, KA_BREAK = 21, CHOP = 22, MINE = 23, PLACE = 24, WIPE = 25,
            PLANT = 26, SHAPE = 27, SKY_MINE = 28, XENO_MIKIKO = 29, XENO_RHALIX = 30, STUNNED = 31;

    private Poses() {}

    public static boolean isState(int pose) { return pose < 10; }

    public static void play(LivingEntity e, int pose, int ticks) {
        if (e.level().isClientSide) return;
        ModNetwork.sendToTrackingEntityAndSelf(new EntityPosePacket(e.getId(), pose, ticks), e);
        if (!(e instanceof Player) && net.minecraftforge.fml.ModList.get().isLoaded("epicfight"))
            com.xenowarriors.compat.epicfight.EfPoses.play(e, pose);
    }

    public static void clearState(LivingEntity e) { play(e, CLEAR, 0); }
}
