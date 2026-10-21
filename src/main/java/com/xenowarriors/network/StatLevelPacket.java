package com.xenowarriors.network;

import com.xenowarriors.client.StatToasts;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: one of your stats levelled up (shown top right). */
public record StatLevelPacket(int stat, int level) {
    public static void encode(StatLevelPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.stat);
        buf.writeVarInt(msg.level);
    }

    public static StatLevelPacket decode(FriendlyByteBuf buf) {
        return new StatLevelPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(StatLevelPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> StatToasts.add(msg.stat, msg.level));
    }
}
