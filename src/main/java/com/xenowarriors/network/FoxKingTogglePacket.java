package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: Mikiko pressed the Fox King key. */
public record FoxKingTogglePacket() {
    public static void encode(FoxKingTogglePacket m, FriendlyByteBuf buf) {}

    public static FoxKingTogglePacket decode(FriendlyByteBuf buf) {
        return new FoxKingTogglePacket();
    }

    public static void handle(FoxKingTogglePacket m, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) { com.xenowarriors.mikiko.FoxKing.toggle(p); com.xenowarriors.rhalix.DemonsRemnants.activate(p); }   // each only works for its own character
    }
}
