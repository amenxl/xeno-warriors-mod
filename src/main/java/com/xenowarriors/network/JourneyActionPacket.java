package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server: a Journey button. 0 open, 1 pick (a = character), 2 difficulty (a), 3 ready, 4 start, 5 leave lobby,
 * 6 choose for battle (a = character), 7 battle ready, 8 buy (a = item, b = character), 9 retreat, 10 retry battle.
 */
public record JourneyActionPacket(int action, int a, int b) {
    public static void encode(JourneyActionPacket m, FriendlyByteBuf buf) { buf.writeVarInt(m.action); buf.writeVarInt(m.a); buf.writeVarInt(m.b); }
    public static JourneyActionPacket decode(FriendlyByteBuf buf) { return new JourneyActionPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()); }
    public static void handle(JourneyActionPacket m, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) com.xenowarriors.journey.Journey.action(p, m.action, m.a, m.b);
    }
}
