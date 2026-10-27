package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> nearby players: play an effect (only drawn if the player has Lodestone). */
public record FxPacket(int type, double x, double y, double z, int color, float scale) {
    public static final int SKILL = 0, ULTIMATE = 1, KA = 2, EYE = 3, SHOCK = 4;

    public static void encode(FxPacket m, FriendlyByteBuf b) { b.writeByte(m.type); b.writeDouble(m.x); b.writeDouble(m.y); b.writeDouble(m.z); b.writeInt(m.color); b.writeFloat(m.scale); }
    public static FxPacket decode(FriendlyByteBuf b) { return new FxPacket(b.readByte(), b.readDouble(), b.readDouble(), b.readDouble(), b.readInt(), b.readFloat()); }
    public static void handle(FxPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.XwFxClient.play(m));
        ctx.get().setPacketHandled(true);
    }
}
