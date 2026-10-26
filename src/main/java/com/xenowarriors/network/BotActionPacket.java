package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Player -> server: a choice from a bot's menu or the party menu (see PartyBots.ACT_*). */
public record BotActionPacket(int botId, int action, int arg) {
    public static void encode(BotActionPacket m, FriendlyByteBuf b) { b.writeVarInt(m.botId); b.writeVarInt(m.action); b.writeVarInt(m.arg); }
    public static BotActionPacket decode(FriendlyByteBuf b) { return new BotActionPacket(b.readVarInt(), b.readVarInt(), b.readVarInt()); }
    public static void handle(BotActionPacket m, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) com.xenowarriors.bot.PartyBots.action(p, m.botId, m.action, m.arg);
        ctx.get().setPacketHandled(true);
    }
}
