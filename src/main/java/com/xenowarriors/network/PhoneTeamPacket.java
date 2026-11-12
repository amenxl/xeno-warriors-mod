package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server -> player: the phone's Teammates page (busy off-duty characters, when they'll be done, whether you've called). */
public record PhoneTeamPacket(List<Entry> entries) {
    /** minutes/clock: when they're done (-1 / "" for a job you gave them, which lasts until you call them back). */
    public record Entry(String id, int act, String label, int minutes, String clock, boolean called, boolean ordered) {}

    public static void encode(PhoneTeamPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.entries.size());
        for (Entry e : m.entries) {
            b.writeUtf(e.id, 32); b.writeVarInt(e.act); b.writeUtf(e.label, 200); b.writeInt(e.minutes); b.writeUtf(e.clock, 16);
            b.writeBoolean(e.called); b.writeBoolean(e.ordered);
        }
    }

    public static PhoneTeamPacket decode(FriendlyByteBuf b) {
        int n = Math.min(64, b.readVarInt());
        List<Entry> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(new Entry(b.readUtf(32), b.readVarInt(), b.readUtf(200), b.readInt(), b.readUtf(16), b.readBoolean(), b.readBoolean()));
        return new PhoneTeamPacket(l);
    }

    public static void handle(PhoneTeamPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.PhoneScreen.showTeam(m));
        ctx.get().setPacketHandled(true);
    }
}
