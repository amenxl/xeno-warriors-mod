package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: a Mikiko threw his blade (0) or lashed it out on the rope (1). */
public record RopeFxPacket(int entityId, int type) {
    public static void encode(RopeFxPacket m, FriendlyByteBuf buf) { buf.writeVarInt(m.entityId); buf.writeVarInt(m.type); }

    public static RopeFxPacket decode(FriendlyByteBuf buf) { return new RopeFxPacket(buf.readVarInt(), buf.readVarInt()); }

    public static void handle(RopeFxPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.mikiko.RopeBladeClient.fx(m.entityId, m.type));
    }
}
