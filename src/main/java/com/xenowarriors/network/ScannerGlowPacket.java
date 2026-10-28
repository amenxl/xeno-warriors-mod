package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> the Scanner Eye's user: these entities glow through walls for you (for a few seconds). */
public record ScannerGlowPacket(int[] ids) {
    public static void encode(ScannerGlowPacket m, FriendlyByteBuf b) { b.writeVarIntArray(m.ids); }
    public static ScannerGlowPacket decode(FriendlyByteBuf b) { return new ScannerGlowPacket(b.readVarIntArray(512)); }
    public static void handle(ScannerGlowPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.ScannerGlow.set(m.ids));
        ctx.get().setPacketHandled(true);
    }
}
