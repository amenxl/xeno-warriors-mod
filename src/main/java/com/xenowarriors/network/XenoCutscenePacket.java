package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Play the Xeno Blast cutscene: who's casting it, which one (Rhalix's orb or Mikiko's beam), where and facing which way. */
public record XenoCutscenePacket(int caster, boolean rhalix, int ticks, int release, double x, double y, double z, float yaw) {
    public static void encode(XenoCutscenePacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.caster); b.writeBoolean(m.rhalix); b.writeVarInt(m.ticks); b.writeVarInt(m.release);
        b.writeDouble(m.x); b.writeDouble(m.y); b.writeDouble(m.z); b.writeFloat(m.yaw);
    }

    public static XenoCutscenePacket decode(FriendlyByteBuf b) {
        return new XenoCutscenePacket(b.readVarInt(), b.readBoolean(), b.readVarInt(), b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat());
    }

    public static void handle(XenoCutscenePacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.XenoCutscene.start(m));
    }
}
