package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: their guard is up / down (Epic Fight's guard, which only their client knows) - for weaving out of gunfire (BulletDodge). */
public record GuardStatePacket(boolean on) {
    public static void encode(GuardStatePacket m, FriendlyByteBuf b) { b.writeBoolean(m.on); }
    public static GuardStatePacket decode(FriendlyByteBuf b) { return new GuardStatePacket(b.readBoolean()); }
    public static void handle(GuardStatePacket m, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) com.xenowarriors.combat.BulletDodge.clientGuard(p, m.on());
        ctx.get().setPacketHandled(true);
    }
}
