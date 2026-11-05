package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * A bot's DragonMineZ menu. Server -> client: OPEN (the bot's DMZ character is in yours now: open DragonMineZ's menu)
 * or CLOSED (yours is back: close it). Client -> server: CLOSE (DragonMineZ's menu was closed: put yours back).
 */
public record BotDmzPacket(int action, String name) {
    public static final int OPEN = 0, CLOSED = 1, CLOSE = 2;

    public static void encode(BotDmzPacket m, FriendlyByteBuf b) { b.writeVarInt(m.action); b.writeUtf(m.name, 64); }
    public static BotDmzPacket decode(FriendlyByteBuf b) { return new BotDmzPacket(b.readVarInt(), b.readUtf(64)); }

    public static void handle(BotDmzPacket m, Supplier<NetworkEvent.Context> ctx) {
        var sender = ctx.get().getSender();
        if (sender != null) { if (m.action == CLOSE) com.xenowarriors.compat.DmzPerCharacter.endView(sender, "menu closed"); }
        else DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.compat.DmzBotView.handle(m.action, m.name));
        ctx.get().setPacketHandled(true);
    }
}
