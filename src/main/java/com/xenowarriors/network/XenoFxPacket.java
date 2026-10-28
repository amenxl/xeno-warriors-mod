package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The solid Xeno Blast visuals (DragonMine Z style glowing beams and balls), drawn on the client.
 * kind: see client.KiFx (CHARGE, BEAM, ORB, BURST, CLASH, SHOT, STOP).
 */
public record XenoFxPacket(int caster, int kind, int other, boolean rhalix, float x, float y, float z,
                           float dx, float dy, float dz, float size, int life) {
    public static void encode(XenoFxPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.caster); b.writeByte(m.kind); b.writeVarInt(m.other); b.writeBoolean(m.rhalix);
        b.writeFloat(m.x); b.writeFloat(m.y); b.writeFloat(m.z);
        b.writeFloat(m.dx); b.writeFloat(m.dy); b.writeFloat(m.dz);
        b.writeFloat(m.size); b.writeVarInt(m.life);
    }
    public static XenoFxPacket decode(FriendlyByteBuf b) {
        return new XenoFxPacket(b.readVarInt(), b.readByte(), b.readVarInt(), b.readBoolean(), b.readFloat(), b.readFloat(), b.readFloat(),
                b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readVarInt());
    }
    public static void handle(XenoFxPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.KiFx.receive(m));
    }
}
