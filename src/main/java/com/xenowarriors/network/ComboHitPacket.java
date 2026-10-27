package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: my combo swing reached its strike frame - it hits this entity. */
public record ComboHitPacket(int target, int hit, boolean heavy) {
    public static void encode(ComboHitPacket m, FriendlyByteBuf b) { b.writeVarInt(m.target); b.writeVarInt(m.hit); b.writeBoolean(m.heavy); }
    public static ComboHitPacket decode(FriendlyByteBuf b) { return new ComboHitPacket(b.readVarInt(), b.readVarInt(), b.readBoolean()); }
    public static void handle(ComboHitPacket m, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) com.xenowarriors.combat.ComboServer.strike(p, m.target, m.hit, m.heavy);
    }
}
