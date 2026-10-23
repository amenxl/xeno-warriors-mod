package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Client -> server: put my hood up / take it down (the hood key). Passed on to everyone who can see them. */
public record HoodTogglePacket(boolean up) {
    public static void encode(HoodTogglePacket m, FriendlyByteBuf buf) { buf.writeBoolean(m.up); }

    public static HoodTogglePacket decode(FriendlyByteBuf buf) { return new HoodTogglePacket(buf.readBoolean()); }

    public static void handle(HoodTogglePacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null) ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new HoodStatePacket(p.getId(), m.up));
    }
}
