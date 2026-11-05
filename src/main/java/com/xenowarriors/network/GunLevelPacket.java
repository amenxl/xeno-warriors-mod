package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: your gun level (TACZ installed only) and progress to the next. */
public record GunLevelPacket(int level, float progress) {
    /** The local player's gun level (0 = unknown / TACZ not installed). */
    public static int clientLevel;
    public static float clientProgress;

    public static void encode(GunLevelPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.level);
        buf.writeFloat(msg.progress);
    }

    public static GunLevelPacket decode(FriendlyByteBuf buf) { return new GunLevelPacket(buf.readVarInt(), buf.readFloat()); }

    public static void handle(GunLevelPacket msg, Supplier<NetworkEvent.Context> ctx) {
        clientLevel = msg.level;
        clientProgress = msg.progress;
    }
}
