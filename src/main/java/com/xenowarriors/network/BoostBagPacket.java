package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The Boost Bag (statboost.BoostBag). Server -> client: OPEN, with how many of each boost it holds. Client -> server:
 * TAKE (n of a kind out), USE (one, or all when n &gt; 1), DEPOSIT (every loose boost in your inventory in).
 */
public record BoostBagPacket(int action, int kind, int n, int[] counts) {
    public static final int OPEN = 0, TAKE = 1, USE = 2, DEPOSIT = 3;

    public static void encode(BoostBagPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.action); b.writeVarInt(m.kind + 1); b.writeVarInt(m.n);
        b.writeVarIntArray(m.counts);
    }
    public static BoostBagPacket decode(FriendlyByteBuf b) {
        int a = b.readVarInt(), k = b.readVarInt() - 1, n = b.readVarInt();
        return new BoostBagPacket(a, k, n, b.readVarIntArray(256));
    }
    public static void handle(BoostBagPacket m, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) com.xenowarriors.statboost.BoostBag.act(p, m.action, m.kind, m.n);
        else DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.BoostBagScreen.show(m.counts));
        ctx.get().setPacketHandled(true);
    }
}
