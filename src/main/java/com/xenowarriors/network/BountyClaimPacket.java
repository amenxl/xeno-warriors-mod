package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Player -> server: turn in bounty #index. */
public record BountyClaimPacket(int index) {
    public static void encode(BountyClaimPacket m, FriendlyByteBuf b) { b.writeVarInt(m.index); }
    public static BountyClaimPacket decode(FriendlyByteBuf b) { return new BountyClaimPacket(b.readVarInt()); }
    public static void handle(BountyClaimPacket m, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) com.xenowarriors.bounty.Bounties.claim(p, m.index);
        ctx.get().setPacketHandled(true);
    }
}
