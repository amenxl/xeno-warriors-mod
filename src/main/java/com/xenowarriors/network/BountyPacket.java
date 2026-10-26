package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> player: today's bounties (and seconds until they refresh). */
public record BountyPacket(String[] ids, int[] need, int[] have, int[] tier, boolean[] claimed, int refreshIn) {
    public static void encode(BountyPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.ids.length);
        for (int i = 0; i < m.ids.length; i++) { b.writeUtf(m.ids[i]); b.writeVarInt(m.need[i]); b.writeVarInt(m.have[i]); b.writeVarInt(m.tier[i]); b.writeBoolean(m.claimed[i]); }
        b.writeVarInt(m.refreshIn);
    }
    public static BountyPacket decode(FriendlyByteBuf b) {
        int n = Math.min(8, b.readVarInt());
        String[] ids = new String[n]; int[] need = new int[n], have = new int[n], tier = new int[n]; boolean[] c = new boolean[n];
        for (int i = 0; i < n; i++) { ids[i] = b.readUtf(); need[i] = b.readVarInt(); have[i] = b.readVarInt(); tier[i] = b.readVarInt(); c[i] = b.readBoolean(); }
        return new BountyPacket(ids, need, have, tier, c, b.readVarInt());
    }
    public static void handle(BountyPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.PacketScreens.openBounties(m));
        ctx.get().setPacketHandled(true);
    }
}
