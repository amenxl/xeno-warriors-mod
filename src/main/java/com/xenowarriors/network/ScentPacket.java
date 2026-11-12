package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server -> Kaeru: the scent picker (what she can smell nearby: kind 0 enemy, 1 animal / other, 2 a person), or - one
 * entry of kind -1 - start showing that one's scent trail for {@code distance} ticks. See toggle.Passives.
 */
public record ScentPacket(List<Entry> entries) {
    public record Entry(int id, String name, int kind, int distance) {}

    public static void encode(ScentPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.entries.size());
        for (Entry e : m.entries) { b.writeVarInt(e.id); b.writeUtf(e.name, 64); b.writeVarInt(e.kind + 1); b.writeVarInt(e.distance); }
    }

    public static ScentPacket decode(FriendlyByteBuf b) {
        int n = Math.min(64, b.readVarInt());
        List<Entry> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(new Entry(b.readVarInt(), b.readUtf(64), b.readVarInt() - 1, b.readVarInt()));
        return new ScentPacket(l);
    }

    public static void handle(ScentPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.kaeru.ScentClient.received(m));
        ctx.get().setPacketHandled(true);
    }
}
