package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: this Mikiko is (no longer) channelling the Hekublade. */
public record BladeChannelStatePacket(int entityId, boolean on) {
    public static void encode(BladeChannelStatePacket m, FriendlyByteBuf buf) { buf.writeVarInt(m.entityId); buf.writeBoolean(m.on); }

    public static BladeChannelStatePacket decode(FriendlyByteBuf buf) { return new BladeChannelStatePacket(buf.readVarInt(), buf.readBoolean()); }

    public static void handle(BladeChannelStatePacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.mikiko.BladeChannelClient.set(m.entityId, m.on));
    }
}
