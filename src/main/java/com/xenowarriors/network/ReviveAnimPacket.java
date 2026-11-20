package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients near: this one (a player or bot) is reviving that one right now - hand out to them (ReviveClient). */
public record ReviveAnimPacket(int helper, int target) {
    public static void encode(ReviveAnimPacket m, FriendlyByteBuf b) { b.writeVarInt(m.helper); b.writeVarInt(m.target); }
    public static ReviveAnimPacket decode(FriendlyByteBuf b) { return new ReviveAnimPacket(b.readVarInt(), b.readVarInt()); }
    public static void handle(ReviveAnimPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.party.ReviveClient.set(m.helper(), m.target()));
        ctx.get().setPacketHandled(true);
    }
}
