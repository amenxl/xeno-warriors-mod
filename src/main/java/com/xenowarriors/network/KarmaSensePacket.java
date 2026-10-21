package com.xenowarriors.network;

import com.xenowarriors.karma.KarmaSensing;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: pressed the Karma Sensing key. */
public record KarmaSensePacket(boolean friendly) {
    public static void encode(KarmaSensePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.friendly);
    }

    public static KarmaSensePacket decode(FriendlyByteBuf buf) {
        return new KarmaSensePacket(buf.readBoolean());
    }

    public static void handle(KarmaSensePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) KarmaSensing.use(player, msg.friendly);
    }
}
