package com.xenowarriors.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: the whole Journey state as seen by this player (empty = not on a journey), and a screen to open. */
public record JourneyStatePacket(CompoundTag state, int open) {
    public static void encode(JourneyStatePacket m, FriendlyByteBuf b) { b.writeNbt(m.state); b.writeVarInt(m.open); }
    public static JourneyStatePacket decode(FriendlyByteBuf b) { CompoundTag t = b.readNbt(); return new JourneyStatePacket(t == null ? new CompoundTag() : t, b.readVarInt()); }
    public static void handle(JourneyStatePacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.journey.JourneyClient.receive(m.state, m.open));
    }
}
