package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: held right-click while locked on to a downed teammate - warp to them (30 Karma). */
public record ReviveWarpPacket(int targetId) {
    public static void encode(ReviveWarpPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.targetId);
    }

    public static ReviveWarpPacket decode(FriendlyByteBuf buf) {
        return new ReviveWarpPacket(buf.readVarInt());
    }

    public static void handle(ReviveWarpPacket m, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p == null) return;
        if (p.level().getEntity(m.targetId) instanceof LivingEntity t) com.xenowarriors.party.ReviveWarp.warp(p, t);
    }
}
