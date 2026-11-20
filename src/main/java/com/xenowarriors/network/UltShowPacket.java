package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> the caster and everyone near: an ultimate's show (UltShow); {@code cam}: the caster gets the cutscene. */
public record UltShowPacket(int entity, int style, int color, String name, float yaw, boolean cam) {
    public static void encode(UltShowPacket m, FriendlyByteBuf b) { b.writeVarInt(m.entity); b.writeByte(m.style); b.writeInt(m.color); b.writeUtf(m.name, 64); b.writeFloat(m.yaw); b.writeBoolean(m.cam); }
    public static UltShowPacket decode(FriendlyByteBuf b) { return new UltShowPacket(b.readVarInt(), b.readByte(), b.readInt(), b.readUtf(64), b.readFloat(), b.readBoolean()); }
    public static void handle(UltShowPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.UltShowClient.start(m));
        ctx.get().setPacketHandled(true);
    }
}
