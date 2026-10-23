package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: throw the Season 3 Hekublade like a boomerang (at the lock-on target, or -1). */
public record RopeThrowPacket(int targetId) {
    public static void encode(RopeThrowPacket m, FriendlyByteBuf buf) { buf.writeVarInt(m.targetId); }

    public static RopeThrowPacket decode(FriendlyByteBuf buf) { return new RopeThrowPacket(buf.readVarInt()); }

    public static void handle(RopeThrowPacket m, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) com.xenowarriors.mikiko.RopeBlade.throwBlade(p, m.targetId);
    }
}
