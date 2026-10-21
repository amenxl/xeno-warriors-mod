package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> player: your mining level (so breaking blocks feels right on your side too). */
public record MiningLevelPacket(int level, float progress) {
    public static void encode(MiningLevelPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.level);
        buf.writeFloat(m.progress);
    }

    public static MiningLevelPacket decode(FriendlyByteBuf buf) {
        return new MiningLevelPacket(buf.readVarInt(), buf.readFloat());
    }

    public static void handle(MiningLevelPacket m, Supplier<NetworkEvent.Context> ctx) {
        com.xenowarriors.stats.Mining.setClient(m.level, m.progress);
    }
}
