package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: Mikiko's final combo hit with the Metal Pole just played (Epic Fight). */
public record PoleGustPacket() {
    public static void encode(PoleGustPacket m, FriendlyByteBuf buf) {}

    public static PoleGustPacket decode(FriendlyByteBuf buf) { return new PoleGustPacket(); }

    public static void handle(PoleGustPacket m, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) com.xenowarriors.mikiko.PoleGust.fire(p);
    }
}
