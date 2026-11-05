package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Server -> a party member (with DragonMineZ only): each party member's ki (players and bots), for the party HUD. */
public record PartyKiPacket(List<Entry> entries) {
    public record Entry(UUID id, float ki, float maxKi) {}

    public static void encode(PartyKiPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.entries.size());
        for (Entry e : m.entries) { buf.writeUUID(e.id); buf.writeFloat(e.ki); buf.writeFloat(e.maxKi); }
    }

    public static PartyKiPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(64, buf.readVarInt());
        List<Entry> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) l.add(new Entry(buf.readUUID(), buf.readFloat(), buf.readFloat()));
        return new PartyKiPacket(l);
    }

    public static void handle(PartyKiPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.party.PartyKiClient.set(m.entries));
        ctx.get().setPacketHandled(true);
    }
}
