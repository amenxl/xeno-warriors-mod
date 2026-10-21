package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: this animal is (or no longer is) possessed by a corrupted soul. */
public record CorruptionPacket(int entityId, boolean on) {
    public static void encode(CorruptionPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.entityId);
        buf.writeBoolean(m.on);
    }

    public static CorruptionPacket decode(FriendlyByteBuf buf) {
        return new CorruptionPacket(buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(CorruptionPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.corrupted.CorruptionClient.set(m.entityId, m.on));
    }
}
