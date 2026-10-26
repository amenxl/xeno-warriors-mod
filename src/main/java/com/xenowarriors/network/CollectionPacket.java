package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> player: their Collection (every hostile enemy + defeats), whether it's unlocked in the menu, Tower floor / best. */
public record CollectionPacket(String[] ids, int[] counts, int total, boolean show, boolean unlocked, int floor, int best) {
    public static void encode(CollectionPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.ids.length);
        for (int i = 0; i < m.ids.length; i++) { b.writeUtf(m.ids[i]); b.writeVarInt(m.counts[i]); }
        b.writeVarInt(m.total); b.writeBoolean(m.show); b.writeBoolean(m.unlocked); b.writeVarInt(m.floor); b.writeVarInt(m.best);
    }
    public static CollectionPacket decode(FriendlyByteBuf b) {
        int n = Math.min(4096, b.readVarInt()); String[] ids = new String[n]; int[] c = new int[n];
        for (int i = 0; i < n; i++) { ids[i] = b.readUtf(); c[i] = b.readVarInt(); }
        return new CollectionPacket(ids, c, b.readVarInt(), b.readBoolean(), b.readBoolean(), b.readVarInt(), b.readVarInt());
    }
    public static void handle(CollectionPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.PacketScreens.openCollection(m));
        ctx.get().setPacketHandled(true);
    }
}
