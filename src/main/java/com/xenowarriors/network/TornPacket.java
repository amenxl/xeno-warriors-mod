package com.xenowarriors.network;

import com.xenowarriors.client.render.CharacterDetailLayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Server -> clients: how torn this player's clothes are (0-3). */
public record TornPacket(UUID player, int torn) {
    public static void encode(TornPacket m, FriendlyByteBuf buf) {
        buf.writeUUID(m.player);
        buf.writeVarInt(m.torn);
    }

    public static TornPacket decode(FriendlyByteBuf buf) {
        return new TornPacket(buf.readUUID(), buf.readVarInt());
    }

    public static void handle(TornPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CharacterDetailLayer.setTorn(m.player, m.torn));
    }
}
