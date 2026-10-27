package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Player -> server: buy this vehicle in this colour at the dealer. */
public record DealerBuyPacket(int kind, int variant) {
    public static void encode(DealerBuyPacket m, FriendlyByteBuf b) { b.writeVarInt(m.kind); b.writeVarInt(m.variant); }
    public static DealerBuyPacket decode(FriendlyByteBuf b) { return new DealerBuyPacket(b.readVarInt(), b.readVarInt()); }
    public static void handle(DealerBuyPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> { var p = ctx.get().getSender(); if (p != null) com.xenowarriors.vehicle.Dealer.buy(p, m.kind(), m.variant()); });
        ctx.get().setPacketHandled(true);
    }
}
