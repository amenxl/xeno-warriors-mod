package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients near: this one (a player or bot) weaves out of a bullet's way (kind 0) or knocks it back (1), leaning to {@code side} (DodgeClient). */
public record DodgeAnimPacket(int entity, int side, int kind) {
    public static void encode(DodgeAnimPacket m, FriendlyByteBuf b) { b.writeVarInt(m.entity); b.writeByte(m.side); b.writeByte(m.kind); }
    public static DodgeAnimPacket decode(FriendlyByteBuf b) { return new DodgeAnimPacket(b.readVarInt(), b.readByte(), b.readByte()); }
    public static void handle(DodgeAnimPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.DodgeClient.set(m.entity(), m.side(), m.kind()));
        ctx.get().setPacketHandled(true);
    }
}
