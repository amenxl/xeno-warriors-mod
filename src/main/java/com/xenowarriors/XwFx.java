package com.xenowarriors;

import com.xenowarriors.network.FxPacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Sends an effect to everyone nearby (drawn by those with Lodestone installed). */
public final class XwFx {
    private XwFx() {}

    public static void send(ServerLevel l, int type, double x, double y, double z, int color, float scale) {
        FxPacket m = new FxPacket(type, x, y, z, color, scale);
        for (ServerPlayer p : l.players()) if (p.distanceToSqr(x, y, z) < 64 * 64) ModNetwork.sendToPlayer(m, p);
    }
}
