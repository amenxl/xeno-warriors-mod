package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: one push in a clash (a mashed attack click). */
public record ClashPushPacket() {
    public static void encode(ClashPushPacket m, FriendlyByteBuf b) {}

    public static ClashPushPacket decode(FriendlyByteBuf b) { return new ClashPushPacket(); }

    public static void handle(ClashPushPacket m, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) com.xenowarriors.ka.Clashes.push(p);
        ctx.get().setPacketHandled(true);
    }
}
