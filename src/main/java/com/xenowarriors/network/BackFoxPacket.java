package com.xenowarriors.network;

import com.xenowarriors.client.fox.FoxClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Server -> clients: the fox this player carries on his back (-1 none, 0 red, 1 snow). */
public record BackFoxPacket(UUID player, int variant) {
    public static void encode(BackFoxPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.player);
        buf.writeVarInt(msg.variant + 1);
    }

    public static BackFoxPacket decode(FriendlyByteBuf buf) {
        return new BackFoxPacket(buf.readUUID(), buf.readVarInt() - 1);
    }

    public static void handle(BackFoxPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> FoxClient.setBack(msg.player, msg.variant));
    }
}
