package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: the rope-lash finisher of a Season 3 Hekublade combo just played (Epic Fight). */
public record RopeLashPacket() {
    public static void encode(RopeLashPacket m, FriendlyByteBuf buf) {}

    public static RopeLashPacket decode(FriendlyByteBuf buf) { return new RopeLashPacket(); }

    public static void handle(RopeLashPacket m, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) com.xenowarriors.mikiko.RopeBlade.lash(p);
    }
}
