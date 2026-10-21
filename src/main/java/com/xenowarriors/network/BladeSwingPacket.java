package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: Mikiko swung the Hekublade at the air. */
public record BladeSwingPacket() {
    public static void encode(BladeSwingPacket m, FriendlyByteBuf buf) {}

    public static BladeSwingPacket decode(FriendlyByteBuf buf) {
        return new BladeSwingPacket();
    }

    public static void handle(BladeSwingPacket m, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) com.xenowarriors.mikiko.HekubladeSwing.swingAtAir(p);
    }
}
