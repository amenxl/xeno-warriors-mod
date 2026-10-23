package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: Sky Ore blocks your Karma Sensing picked up, and how long they glow. */
public record SkyOreSensedPacket(long[] positions, int duration) {
    public static void encode(SkyOreSensedPacket m, FriendlyByteBuf buf) { buf.writeLongArray(m.positions); buf.writeVarInt(m.duration); }

    public static SkyOreSensedPacket decode(FriendlyByteBuf buf) { return new SkyOreSensedPacket(buf.readLongArray(), buf.readVarInt()); }

    public static void handle(SkyOreSensedPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.SkyOreGlow.set(m.positions, m.duration));
    }
}
