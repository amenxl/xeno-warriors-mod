package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: a Mikiko entered or left Fox King mode. */
public record FoxKingPacket(int entityId, boolean on) {
    public static void encode(FoxKingPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.entityId);
        buf.writeBoolean(m.on);
    }

    public static FoxKingPacket decode(FriendlyByteBuf buf) {
        return new FoxKingPacket(buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(FoxKingPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.mikiko.FoxKingClient.set(m.entityId, m.on));
    }
}
