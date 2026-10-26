package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> Ultima: what Data Sense read (life signs, ore + kinds), or that it crashed. */
public record DataSensedPacket(int[] ids, long[] ores, int[] kinds, int duration, boolean error) {
    public static void encode(DataSensedPacket m, FriendlyByteBuf b) { b.writeVarIntArray(m.ids); b.writeLongArray(m.ores); b.writeVarIntArray(m.kinds); b.writeVarInt(m.duration); b.writeBoolean(m.error); }
    public static DataSensedPacket decode(FriendlyByteBuf b) { return new DataSensedPacket(b.readVarIntArray(), b.readLongArray(), b.readVarIntArray(), b.readVarInt(), b.readBoolean()); }
    public static void handle(DataSensedPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.DataSenseClient.set(m.ids, m.ores, m.kinds, m.duration, m.error));
        ctx.get().setPacketHandled(true);
    }
}
