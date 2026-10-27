package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: I started combo swing {@code hit} (or a heavy). */
public record ComboStartPacket(int hit, boolean heavy) {
    public static void encode(ComboStartPacket m, FriendlyByteBuf b) { b.writeVarInt(m.hit); b.writeBoolean(m.heavy); }
    public static ComboStartPacket decode(FriendlyByteBuf b) { return new ComboStartPacket(b.readVarInt(), b.readBoolean()); }
    public static void handle(ComboStartPacket m, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) com.xenowarriors.combat.ComboServer.start(p, m.hit, m.heavy);
    }
}
