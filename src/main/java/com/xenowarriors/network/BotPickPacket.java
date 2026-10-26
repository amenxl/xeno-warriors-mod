package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Player -> server: the characters picked to join as bot teammates. */
public record BotPickPacket(String[] ids) {
    public static void encode(BotPickPacket m, FriendlyByteBuf b) { b.writeVarInt(m.ids.length); for (String s : m.ids) b.writeUtf(s, 32); }
    public static BotPickPacket decode(FriendlyByteBuf b) {
        String[] ids = new String[Math.min(2, b.readVarInt())];
        for (int i = 0; i < ids.length; i++) ids[i] = b.readUtf(32);
        return new BotPickPacket(ids);
    }
    public static void handle(BotPickPacket m, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) com.xenowarriors.bot.PartyBots.add(p, m.ids);
        ctx.get().setPacketHandled(true);
    }
}
