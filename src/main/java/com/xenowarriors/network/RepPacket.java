package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Both ways: the client asks (empty); the server answers your standing with each faction (by Faction ordinal). */
public record RepPacket(int[] values) {
    public static int[] CLIENT = new int[0];

    public static void encode(RepPacket m, FriendlyByteBuf b) { b.writeVarIntArray(m.values); }
    public static RepPacket decode(FriendlyByteBuf b) { return new RepPacket(b.readVarIntArray(16)); }
    public static void handle(RepPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null) ModNetwork.sendToPlayer(new RepPacket(com.xenowarriors.faction.Reputation.all(p, true)), p);
        else CLIENT = m.values;
        ctx.get().setPacketHandled(true);
    }
}
