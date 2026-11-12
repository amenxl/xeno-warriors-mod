package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Player -> server: their pick in a bot's rock, paper, scissors challenge (0 rock, 1 paper, 2 scissors). */
public record RpsPickPacket(int botId, int pick) {
    public static void encode(RpsPickPacket m, FriendlyByteBuf b) { b.writeVarInt(m.botId); b.writeVarInt(m.pick); }
    public static RpsPickPacket decode(FriendlyByteBuf b) { return new RpsPickPacket(b.readVarInt(), b.readVarInt()); }
    public static void handle(RpsPickPacket m, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) com.xenowarriors.bot.BotRps.picked(p, m.botId, m.pick);
        ctx.get().setPacketHandled(true);
    }
}
