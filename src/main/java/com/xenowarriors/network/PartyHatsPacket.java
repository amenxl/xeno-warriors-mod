package com.xenowarriors.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: a bot party is on - who wears party hats, for how much longer, and where the confetti falls. */
public record PartyHatsPacket(int[] ids, int ticks, BlockPos center) {
    public static void encode(PartyHatsPacket m, FriendlyByteBuf b) { b.writeVarIntArray(m.ids); b.writeVarInt(m.ticks); b.writeBlockPos(m.center); }

    public static PartyHatsPacket decode(FriendlyByteBuf b) { return new PartyHatsPacket(b.readVarIntArray(), b.readVarInt(), b.readBlockPos()); }

    public static void handle(PartyHatsPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.PartyHats.set(m.ids, m.ticks, m.center));
        ctx.get().setPacketHandled(true);
    }
}
