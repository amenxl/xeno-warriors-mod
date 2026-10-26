package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> player: how full the big stomach is (Mikiko 200, Rhalix 60). */
public record BigHungerPacket(int total, int max) {
    public static void encode(BigHungerPacket m, FriendlyByteBuf b) { b.writeVarInt(m.total); b.writeVarInt(m.max); }
    public static BigHungerPacket decode(FriendlyByteBuf b) { return new BigHungerPacket(b.readVarInt(), b.readVarInt()); }
    public static void handle(BigHungerPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.BigHungerHud.set(m.total, m.max));
        ctx.get().setPacketHandled(true);
    }
}
