package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: a KA that works on your controls (Grip, Flip, Sight) for some ticks; ka = -1 clears them all. */
public record KaEffectPacket(int ka, int ticks, int sourceId) {
    public static void encode(KaEffectPacket m, FriendlyByteBuf buf) { buf.writeVarInt(m.ka); buf.writeVarInt(m.ticks); buf.writeVarInt(m.sourceId); }

    public static KaEffectPacket decode(FriendlyByteBuf buf) { return new KaEffectPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()); }

    public static void handle(KaEffectPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.KaClient.set(m.ka, m.ticks, m.sourceId));
        ctx.get().setPacketHandled(true);
    }
}
