package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: Kaeru's Change form (0 none, 1 silhouette, 2 creature, 3 block) and what she's becoming. */
public record ChangeFormPacket(int entity, int mode, String form) {
    public static void encode(ChangeFormPacket m, FriendlyByteBuf b) { b.writeVarInt(m.entity); b.writeVarInt(m.mode); b.writeUtf(m.form); }
    public static ChangeFormPacket decode(FriendlyByteBuf b) { return new ChangeFormPacket(b.readVarInt(), b.readVarInt(), b.readUtf()); }
    public static void handle(ChangeFormPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.kaeru.ChangeClient.set(m.entity(), m.mode(), m.form()));
        ctx.get().setPacketHandled(true);
    }
}
