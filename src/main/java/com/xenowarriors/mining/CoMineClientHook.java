package com.xenowarriors.mining;

import net.minecraft.core.BlockPos;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Client side of mining together: the helpers the server last told us about (CoMinePacket). */
public final class CoMineClientHook {
    private CoMineClientHook() {}

    private static BlockPos pos = BlockPos.ZERO;
    private static int helpers;

    public static void set(BlockPos p, int n) { pos = p; helpers = n; }

    public static int helpers() { return helpers; }

    static void apply(PlayerEvent.BreakSpeed e) {
        if (helpers <= 0 || e.getPosition().isEmpty() || !pos.equals(e.getPosition().get())) return;
        e.setNewSpeed(e.getNewSpeed() * CoMining.multiplier(helpers));
    }
}
