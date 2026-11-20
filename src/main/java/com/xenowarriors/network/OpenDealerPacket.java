package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> player: open the vehicle dealer (with how many emeralds you have). */
public record OpenDealerPacket(int emeralds, long money) {
    public static void encode(OpenDealerPacket m, FriendlyByteBuf b) { b.writeVarInt(m.emeralds); b.writeVarLong(m.money); }
    public static OpenDealerPacket decode(FriendlyByteBuf b) { return new OpenDealerPacket(b.readVarInt(), b.readVarLong()); }
    public static void handle(OpenDealerPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.vehicle.DealerScreen.open(m.emeralds(), m.money()));
        ctx.get().setPacketHandled(true);
    }
}
