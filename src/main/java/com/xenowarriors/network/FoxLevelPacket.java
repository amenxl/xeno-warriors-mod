package com.xenowarriors.network;

import com.xenowarriors.client.StatToasts;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> owner: one of your foxes levelled up (shown top right). */
public record FoxLevelPacket(String name, int level) {
    public static void encode(FoxLevelPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.name);
        buf.writeVarInt(msg.level);
    }

    public static FoxLevelPacket decode(FriendlyByteBuf buf) {
        return new FoxLevelPacket(buf.readUtf(), buf.readVarInt());
    }

    public static void handle(FoxLevelPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> StatToasts.addFox(msg.name, msg.level));
    }
}
