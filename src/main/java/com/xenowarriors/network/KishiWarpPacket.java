package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: Rhalix held attack while running - send the Kishi along the ground (toward this enemy, or -1: straight ahead). */
public record KishiWarpPacket(int targetHint) {
    public static void encode(KishiWarpPacket m, FriendlyByteBuf b) { b.writeVarInt(m.targetHint); }
    public static KishiWarpPacket decode(FriendlyByteBuf b) { return new KishiWarpPacket(b.readVarInt()); }
    public static void handle(KishiWarpPacket m, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) com.xenowarriors.combat.RhalixKishiWarp.fromPlayer(p, m.targetHint);
    }
}
