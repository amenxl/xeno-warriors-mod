package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: your Tower Level and progress to the next (for the stats menu). */
public record TowerLevelPacket(int level, float progress) {
    /** The local player's Tower Level (0 = not known yet). */
    public static int clientLevel;
    public static float clientProgress;

    public static void encode(TowerLevelPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.level);
        buf.writeFloat(msg.progress);
    }

    public static TowerLevelPacket decode(FriendlyByteBuf buf) { return new TowerLevelPacket(buf.readVarInt(), buf.readFloat()); }

    public static void handle(TowerLevelPacket msg, Supplier<NetworkEvent.Context> ctx) {
        clientLevel = msg.level;
        clientProgress = msg.progress;
    }
}
