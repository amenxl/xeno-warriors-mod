package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> player: a bot's rock, paper, scissors challenge (outcome -1: pick now), or how a round went (picks 0 rock,
 * 1 paper, 2 scissors; outcome 0 draw - go again, 1 you won, 2 the bot won, 3 called off). See bot.BotRps.
 */
public record RpsPacket(int botId, String name, int round, int botPick, int myPick, int outcome) {
    public static void encode(RpsPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.botId); b.writeUtf(m.name, 64); b.writeVarInt(m.round); b.writeVarInt(m.botPick + 1); b.writeVarInt(m.myPick + 1); b.writeVarInt(m.outcome + 1);
    }
    public static RpsPacket decode(FriendlyByteBuf b) {
        return new RpsPacket(b.readVarInt(), b.readUtf(64), b.readVarInt(), b.readVarInt() - 1, b.readVarInt() - 1, b.readVarInt() - 1);
    }
    public static void handle(RpsPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.party.RpsScreen.received(m));
        ctx.get().setPacketHandled(true);
    }
}
