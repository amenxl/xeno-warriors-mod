package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: an Ultima's output step (0-3 = 25/45/70/100%) and whether he's in Overload. */
public record UltimaOutputPacket(int entityId, int tier, boolean overload) {
    public static void encode(UltimaOutputPacket m, FriendlyByteBuf b) { b.writeVarInt(m.entityId); b.writeVarInt(m.tier); b.writeBoolean(m.overload); }

    public static UltimaOutputPacket decode(FriendlyByteBuf b) { return new UltimaOutputPacket(b.readVarInt(), b.readVarInt(), b.readBoolean()); }

    public static void handle(UltimaOutputPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.UltimaClient.set(m.entityId, m.tier, m.overload));
        ctx.get().setPacketHandled(true);
    }
}
