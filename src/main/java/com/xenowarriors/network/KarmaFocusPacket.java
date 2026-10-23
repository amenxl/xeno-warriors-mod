package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: this player is (no longer) focusing on Karma Sensing (holding the key). */
public record KarmaFocusPacket(int entityId, boolean on) {
    public static void encode(KarmaFocusPacket m, FriendlyByteBuf buf) { buf.writeVarInt(m.entityId); buf.writeBoolean(m.on); }

    public static KarmaFocusPacket decode(FriendlyByteBuf buf) { return new KarmaFocusPacket(buf.readVarInt(), buf.readBoolean()); }

    public static void handle(KarmaFocusPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.KarmaFocusClient.set(m.entityId, m.on));
    }
}
