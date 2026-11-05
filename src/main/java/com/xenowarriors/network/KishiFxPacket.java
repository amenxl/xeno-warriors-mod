package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> clients: Rhalix's Kishi Warp. phase: 0 START (he crouches, a = where the Kishi sets off), 1 HEAD (a = the
 * front of the wave), 2 WARP (a = from, b = to, target = who it reached), 3 FIZZLE (a = where it died out).
 */
public record KishiFxPacket(int entity, int phase, float ax, float ay, float az, float bx, float by, float bz, int target) {
    public static final int START = 0, HEAD = 1, WARP = 2, FIZZLE = 3;

    public static void encode(KishiFxPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.entity); b.writeByte(m.phase);
        b.writeFloat(m.ax); b.writeFloat(m.ay); b.writeFloat(m.az);
        b.writeFloat(m.bx); b.writeFloat(m.by); b.writeFloat(m.bz);
        b.writeVarInt(m.target);
    }
    public static KishiFxPacket decode(FriendlyByteBuf b) {
        return new KishiFxPacket(b.readVarInt(), b.readByte(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readVarInt());
    }
    public static void handle(KishiFxPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.KishiWarpClient.receive(m));
    }
}
