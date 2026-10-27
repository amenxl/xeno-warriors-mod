package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Player -> server: start VR training (0, environment), a challenge (1, index), or leave VR (2). */
public record VrActionPacket(int kind, int arg) {
    public static void encode(VrActionPacket m, FriendlyByteBuf b) { b.writeVarInt(m.kind); b.writeVarInt(m.arg); }
    public static VrActionPacket decode(FriendlyByteBuf b) { return new VrActionPacket(b.readVarInt(), b.readVarInt()); }
    public static void handle(VrActionPacket m, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) com.xenowarriors.vr.Vr.action(p, m.kind, m.arg);
        ctx.get().setPacketHandled(true);
    }
}
