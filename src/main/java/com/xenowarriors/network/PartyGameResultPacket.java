package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Player -> server: how a party game with a bot ended (arm wrestling 1/0 you won, 0/1 it did; Memory: pairs each). */
public record PartyGameResultPacket(int botId, int mine, int theirs) {
    public static void encode(PartyGameResultPacket m, FriendlyByteBuf b) { b.writeVarInt(m.botId); b.writeVarInt(m.mine); b.writeVarInt(m.theirs); }
    public static PartyGameResultPacket decode(FriendlyByteBuf b) { return new PartyGameResultPacket(b.readVarInt(), b.readVarInt(), b.readVarInt()); }
    public static void handle(PartyGameResultPacket m, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) com.xenowarriors.bot.BotPartyGames.result(p, m.botId(), Math.max(0, Math.min(8, m.mine())), Math.max(0, Math.min(8, m.theirs())));
        ctx.get().setPacketHandled(true);
    }
}
