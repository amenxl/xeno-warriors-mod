package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** You're Grounded for this many ticks: no sprinting or flying (your game holds it too). */
public record GroundedPacket(int ticks) {
    public static void encode(GroundedPacket m, FriendlyByteBuf b) { b.writeVarInt(m.ticks); }
    public static GroundedPacket decode(FriendlyByteBuf b) { return new GroundedPacket(b.readVarInt()); }
    public static void handle(GroundedPacket m, Supplier<NetworkEvent.Context> ctx) {
        com.xenowarriors.combat.Grounded.clientUntil = System.currentTimeMillis() + m.ticks * 50L;
    }
}
