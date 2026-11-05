package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> Mikiko: his Fox King mastery (0-100) and, while he's in the form, the ticks left of it / its full length. */
public record FoxMasteryPacket(float mastery, int left, int total) {
    public static void encode(FoxMasteryPacket m, FriendlyByteBuf buf) {
        buf.writeFloat(m.mastery);
        buf.writeVarInt(m.left);
        buf.writeVarInt(m.total);
    }

    public static FoxMasteryPacket decode(FriendlyByteBuf buf) {
        return new FoxMasteryPacket(buf.readFloat(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(FoxMasteryPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.mikiko.FoxKingClient.mastery(m.mastery, m.left, m.total));
    }
}
