package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: Mikiko started / stopped holding the summon key. */
public record BladeChannelPacket(boolean on) {
    public static void encode(BladeChannelPacket m, FriendlyByteBuf buf) { buf.writeBoolean(m.on); }

    public static BladeChannelPacket decode(FriendlyByteBuf buf) { return new BladeChannelPacket(buf.readBoolean()); }

    public static void handle(BladeChannelPacket m, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) com.xenowarriors.mikiko.BladeChannel.set(p, m.on);
    }
}
