package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: Mikiko's third jump (after his double jump) - ride the Hekublade if he's able. */
public record BladeRideJumpPacket() {
    public static void encode(BladeRideJumpPacket msg, FriendlyByteBuf buf) {}

    public static BladeRideJumpPacket decode(FriendlyByteBuf buf) { return new BladeRideJumpPacket(); }

    public static void handle(BladeRideJumpPacket msg, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) com.xenowarriors.mikiko.BladeRideJump.ride(p);
    }
}
