package com.xenowarriors.network;

import com.xenowarriors.client.party.PartyClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Server -> clients: this player is knocked out for this many ticks (0 = back up). */
public record KnockoutPacket(UUID player, int ticks) {
    public static void encode(KnockoutPacket m, FriendlyByteBuf buf) {
        buf.writeUUID(m.player);
        buf.writeVarInt(m.ticks);
    }

    public static KnockoutPacket decode(FriendlyByteBuf buf) {
        return new KnockoutPacket(buf.readUUID(), buf.readVarInt());
    }

    public static void handle(KnockoutPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PartyClient.setKnockedOut(m.player, m.ticks));
    }
}
