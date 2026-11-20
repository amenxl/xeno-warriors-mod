package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> clients in a level: your own characters (players and bots) close enough to Garry and Stevie (bond) to dance
 * when they beatbox ({@code fans}), and the ones grooving at a party's beatbox right now ({@code grooving}) - GarryBeatbox.
 */
public record BeatboxFansPacket(int[] fans, int[] grooving) {
    public static void encode(BeatboxFansPacket m, FriendlyByteBuf b) { b.writeVarIntArray(m.fans); b.writeVarIntArray(m.grooving); }
    public static BeatboxFansPacket decode(FriendlyByteBuf b) { return new BeatboxFansPacket(b.readVarIntArray(256), b.readVarIntArray(256)); }
    public static void handle(BeatboxFansPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.GarryBeatbox.fans(m.fans(), m.grooving()));
        ctx.get().setPacketHandled(true);
    }
}
