package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> nearby players: draw a ki effect with DragonMineZ's own particles (compat/DmzFx; only sent when DragonMineZ is installed). */
public record DmzFxPacket(int kind, double ax, double ay, double az, double bx, double by, double bz, int rgb, float size) {
    /** BALL: a ki ball at a, flying along b (a direction). BEAM: a beam from a to b. BURST: an explosion of ki at a. */
    public static final int BALL = 0, BEAM = 1, BURST = 2;

    public static void encode(DmzFxPacket m, FriendlyByteBuf b) {
        b.writeByte(m.kind);
        b.writeDouble(m.ax); b.writeDouble(m.ay); b.writeDouble(m.az);
        b.writeDouble(m.bx); b.writeDouble(m.by); b.writeDouble(m.bz);
        b.writeInt(m.rgb); b.writeFloat(m.size);
    }

    public static DmzFxPacket decode(FriendlyByteBuf b) {
        return new DmzFxPacket(b.readByte(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readInt(), b.readFloat());
    }

    public static void handle(DmzFxPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.compat.DmzKiParticles.play(m));
        ctx.get().setPacketHandled(true);
    }
}
