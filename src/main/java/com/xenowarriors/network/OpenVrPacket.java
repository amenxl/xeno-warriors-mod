package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> player: open the VR console (which challenges you've cleared, whether you're in VR now). */
public record OpenVrPacket(boolean[] done, boolean inSession) {
    public static void encode(OpenVrPacket m, FriendlyByteBuf b) { b.writeVarInt(m.done.length); for (boolean d : m.done) b.writeBoolean(d); b.writeBoolean(m.inSession); }
    public static OpenVrPacket decode(FriendlyByteBuf b) {
        boolean[] d = new boolean[Math.min(64, b.readVarInt())]; for (int i = 0; i < d.length; i++) d[i] = b.readBoolean();
        return new OpenVrPacket(d, b.readBoolean());
    }
    public static void handle(OpenVrPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.PacketScreens.openVr(m));
        ctx.get().setPacketHandled(true);
    }
}
