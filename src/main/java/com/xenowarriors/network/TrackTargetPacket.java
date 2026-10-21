package com.xenowarriors.network;

import com.xenowarriors.client.KarmaSense;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> Mikiko: show this entity through walls (Fire Blitz is stuck on it). */
public record TrackTargetPacket(int entityId, int ticks) {
    public static void encode(TrackTargetPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeVarInt(msg.ticks);
    }

    public static TrackTargetPacket decode(FriendlyByteBuf buf) {
        return new TrackTargetPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(TrackTargetPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> KarmaSense.track(msg.entityId, msg.ticks));
    }
}
