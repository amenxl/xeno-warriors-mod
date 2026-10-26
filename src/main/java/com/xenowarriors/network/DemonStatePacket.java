package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: Rhalix's Demon's Remnants on/off (red glowing eyes). */
public record DemonStatePacket(int entityId, boolean on) {
    public static void encode(DemonStatePacket m, FriendlyByteBuf b) { b.writeVarInt(m.entityId); b.writeBoolean(m.on); }
    public static DemonStatePacket decode(FriendlyByteBuf b) { return new DemonStatePacket(b.readVarInt(), b.readBoolean()); }
    public static void handle(DemonStatePacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.DemonClient.set(m.entityId, m.on));
        ctx.get().setPacketHandled(true);
    }
}
