package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Your Karma won't regenerate for this many ticks (after a Xeno Blast) - the HUD shows it as drained. */
public record KarmaLockPacket(int ticks) {
    public static void encode(KarmaLockPacket m, FriendlyByteBuf b) { b.writeVarInt(m.ticks); }
    public static KarmaLockPacket decode(FriendlyByteBuf b) { return new KarmaLockPacket(b.readVarInt()); }
    public static void handle(KarmaLockPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.ClientKarmaLock.set(m.ticks));
    }
}
