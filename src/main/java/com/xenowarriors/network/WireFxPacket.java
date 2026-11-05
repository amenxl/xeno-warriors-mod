package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> clients: weapon-form effects on a player - a wire whip swing (extra = swing side), a bind on / off
 * (target, extra = mash progress 0-100), a wire summon, or a gun's recoil.
 */
public record WireFxPacket(int entity, int kind, int target, int extra) {
    public static final int WHIP = 0, BIND = 1, UNBIND = 2, SUMMON = 3, RECOIL = 4;

    public static void encode(WireFxPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.entity); b.writeVarInt(m.kind); b.writeVarInt(m.target + 1); b.writeVarInt(m.extra);
    }

    public static WireFxPacket decode(FriendlyByteBuf b) {
        return new WireFxPacket(b.readVarInt(), b.readVarInt(), b.readVarInt() - 1, b.readVarInt());
    }

    public static void handle(WireFxPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.forms.FormsFx.fx(m));
    }
}
