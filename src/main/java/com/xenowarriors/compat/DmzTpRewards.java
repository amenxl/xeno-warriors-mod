package com.xenowarriors.compat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;

/**
 * DragonMineZ TPs (training points) from this mod's modes: Journey (halfway along the path and on clearing it) and
 * the Tower (every boss floor, more at each 50th floor). Nothing without DragonMineZ or a DragonMineZ character.
 */
public final class DmzTpRewards {
    private DmzTpRewards() {}

    /** (For the tests) TPs handed out. */
    public static float given;

    /** Journey: halfway along the path / the stage cleared (difficulty 0-4, stage 0-2). */
    public static int journeyHalfway(int diff, int stage) { return 150 * (diff + 1) * (stage + 1); }
    public static int journeyCleared(int diff, int stage) { return 500 * (diff + 1) * (stage + 1); }

    /** Tower: a boss floor cleared (triple on every 50th floor). */
    public static int towerBoss(int floor) { return 20 * floor * (floor % 50 == 0 ? 3 : 1); }

    /** Adds TPs to the player's DragonMineZ character and says so. False when there's no DragonMineZ character. */
    public static boolean give(ServerPlayer p, int tp, String why) {
        if (tp <= 0 || p == null || p instanceof FakePlayer) return false;
        Object sd = DmzPerCharacter.live(p);
        if (sd == null) return false;
        try {
            Object res = sd.getClass().getMethod("getResources").invoke(sd);
            res.getClass().getMethod("addTrainingPoints", float.class).invoke(res, (float) tp);
            DmzPerCharacter.syncStats(p);
            given += tp;
            p.sendSystemMessage(Component.literal("+" + String.format("%,d", tp) + " TPs (" + why + ")").withStyle(ChatFormatting.YELLOW));
            return true;
        } catch (Throwable t) {
            com.xenowarriors.XenoWarriors.LOGGER.warn("Couldn't give DragonMineZ TPs", t);
            return false;
        }
    }
}
