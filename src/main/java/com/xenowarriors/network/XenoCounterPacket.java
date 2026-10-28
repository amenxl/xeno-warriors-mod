package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Caught in a Xeno Blast: fight back with the skill in this slot. */
public record XenoCounterPacket(int caster, int slot) {
    public static void encode(XenoCounterPacket m, FriendlyByteBuf b) { b.writeVarInt(m.caster); b.writeVarInt(m.slot); }
    public static XenoCounterPacket decode(FriendlyByteBuf b) { return new XenoCounterPacket(b.readVarInt(), b.readVarInt()); }
    public static void handle(XenoCounterPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null) com.xenowarriors.skill.XenoBlast.counter(p, m.caster, m.slot);
    }
}
