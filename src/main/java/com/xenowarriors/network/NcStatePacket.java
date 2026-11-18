package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: the new characters' states (skill/nc/NcSkills): 1 = in a form (Ava / Persephone's demon look), 2 = Hidden Will (no lock-on). */
public record NcStatePacket(int entityId, int flags) {
    public static void encode(NcStatePacket m, FriendlyByteBuf b) { b.writeVarInt(m.entityId); b.writeVarInt(m.flags); }
    public static NcStatePacket decode(FriendlyByteBuf b) { return new NcStatePacket(b.readVarInt(), b.readVarInt()); }
    public static void handle(NcStatePacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.NcClient.set(m.entityId, m.flags));
        ctx.get().setPacketHandled(true);
    }
}
