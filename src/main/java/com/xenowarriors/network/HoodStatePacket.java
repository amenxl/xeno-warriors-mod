package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: this player put their hood up / down by hand. */
public record HoodStatePacket(int entityId, boolean up) {
    public static void encode(HoodStatePacket m, FriendlyByteBuf buf) { buf.writeVarInt(m.entityId); buf.writeBoolean(m.up); }

    public static HoodStatePacket decode(FriendlyByteBuf buf) { return new HoodStatePacket(buf.readVarInt(), buf.readBoolean()); }

    public static void handle(HoodStatePacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.mikiko.HoodClient.setManual(m.entityId, m.up));
    }
}
