package com.xenowarriors.network;

import com.xenowarriors.karma.KarmaSensing;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: pressed (start) / let go of the Karma Sensing key. */
public record KarmaSensePacket(boolean friendly, boolean start, int view) {
    public static void encode(KarmaSensePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.friendly);
        buf.writeBoolean(msg.start);
        buf.writeVarInt(msg.view);
    }

    public static KarmaSensePacket decode(FriendlyByteBuf buf) {
        return new KarmaSensePacket(buf.readBoolean(), buf.readBoolean(), buf.readVarInt());
    }

    public static void handle(KarmaSensePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) return;
        if (msg.view > 0) KarmaSensing.CLIENT_VIEW.put(player.getUUID(), msg.view);
        if (msg.start) KarmaSensing.use(player, msg.friendly);
        else KarmaSensing.release(player);
    }
}
