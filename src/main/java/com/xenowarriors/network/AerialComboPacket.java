package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: a hit of Mikiko's aerial Hekublade combo on this target (hit = also deal the blow: big reach). */
public record AerialComboPacket(int targetId, int step, boolean last, boolean hit) {
    public static void encode(AerialComboPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.targetId); buf.writeVarInt(m.step); buf.writeBoolean(m.last); buf.writeBoolean(m.hit);
    }

    public static AerialComboPacket decode(FriendlyByteBuf buf) {
        return new AerialComboPacket(buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(AerialComboPacket m, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) com.xenowarriors.mikiko.AerialComboServer.hit(p, m.targetId, m.step, m.last, m.hit);
    }
}
