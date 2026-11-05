package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: Rhalix's Karma shield dome (revive warp) went up at a spot for {@code ticks}, or down (0). */
public record KarmaShieldPacket(int ownerId, double x, double y, double z, float radius, int ticks) {
    public static void encode(KarmaShieldPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.ownerId);
        buf.writeDouble(m.x);
        buf.writeDouble(m.y);
        buf.writeDouble(m.z);
        buf.writeFloat(m.radius);
        buf.writeVarInt(m.ticks);
    }

    public static KarmaShieldPacket decode(FriendlyByteBuf buf) {
        return new KarmaShieldPacket(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readVarInt());
    }

    public static void handle(KarmaShieldPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.party.ReviveWarpClient.shield(m.ownerId, m.x, m.y, m.z, m.radius, m.ticks));
    }
}
