package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> player: a party game with a bot starts (bot.BotPartyGames) - kind 1 arm wrestling ({@code f}: how hard it
 * pushes each tick, {@code data}: its character id), 2 Memory ({@code f}: how well it remembers, {@code data}: the
 * deck's faces and a shuffle seed); -1 closes the screen.
 */
public record PartyGamePacket(int kind, int botId, String name, float f, String data) {
    public static void encode(PartyGamePacket m, FriendlyByteBuf b) { b.writeVarInt(m.kind + 1); b.writeVarInt(m.botId + 1); b.writeUtf(m.name, 64); b.writeFloat(m.f); b.writeUtf(m.data, 512); }
    public static PartyGamePacket decode(FriendlyByteBuf b) { return new PartyGamePacket(b.readVarInt() - 1, b.readVarInt() - 1, b.readUtf(64), b.readFloat(), b.readUtf(512)); }
    public static void handle(PartyGamePacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.party.PartyGameScreens.received(m));
        ctx.get().setPacketHandled(true);
    }
}
