package com.xenowarriors.vehicle;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: Technica's bike guns (0 the Twin Blasters, 1 a rocket) fired by its driver. */
public record BikeFirePacket(int mode) {
    public static void encode(BikeFirePacket m, FriendlyByteBuf buf) { buf.writeVarInt(m.mode); }

    public static BikeFirePacket decode(FriendlyByteBuf buf) { return new BikeFirePacket(buf.readVarInt()); }

    public static void handle(BikeFirePacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null) TechnicaBike.fire(p, m.mode == 1 ? 1 : 0, null);
        ctx.get().setPacketHandled(true);
    }
}
