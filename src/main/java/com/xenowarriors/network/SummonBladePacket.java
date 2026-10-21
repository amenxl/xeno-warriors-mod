package com.xenowarriors.network;

import com.xenowarriors.mikiko.Hekublade;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: Mikiko pressed the summon key. */
public record SummonBladePacket() {
    public static void encode(SummonBladePacket msg, FriendlyByteBuf buf) {}

    public static SummonBladePacket decode(FriendlyByteBuf buf) {
        return new SummonBladePacket();
    }

    public static void handle(SummonBladePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) Hekublade.toggle(player);
    }
}
