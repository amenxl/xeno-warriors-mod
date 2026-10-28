package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** A Xeno Blast clash: who's clashing, where the blasts meet (0 = on the caster, 1 = on the rival); state 0 start, 1 update, 2 over. */
public record XenoClashPacket(int caster, int rival, boolean rivalRhalix, float point, int state) {
    public static void encode(XenoClashPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.caster); b.writeVarInt(m.rival); b.writeBoolean(m.rivalRhalix); b.writeFloat(m.point); b.writeByte(m.state);
    }
    public static XenoClashPacket decode(FriendlyByteBuf b) {
        return new XenoClashPacket(b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readFloat(), b.readByte());
    }
    public static void handle(XenoClashPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.XenoCutscene.clash(m));
    }
}
