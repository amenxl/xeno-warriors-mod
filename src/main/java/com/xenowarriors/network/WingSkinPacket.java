package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: which skin a winged companion is wearing. */
public record WingSkinPacket(int entityId, int skin) {
    public static void encode(WingSkinPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.entityId);
        buf.writeVarInt(m.skin);
    }

    public static WingSkinPacket decode(FriendlyByteBuf buf) {
        return new WingSkinPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(WingSkinPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.wing.WingSkinLayer.set(m.entityId, m.skin));
    }
}
