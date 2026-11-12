package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Server -> client: who's busy off duty (for the character select screen), and whether picking them switches or queues. */
public record BusyCharsPacket(List<Entry> entries) {
    /** minutes -1: a job you gave them. canSwitch: your bond is strong enough (not a Dragon Ball hunt). queued: you're waiting for them. */
    public record Entry(String id, int act, String label, int minutes, boolean canSwitch, boolean queued) {}

    public static final Map<String, Entry> CLIENT = new HashMap<>();

    public static void encode(BusyCharsPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.entries.size());
        for (Entry e : m.entries) { b.writeUtf(e.id, 32); b.writeVarInt(e.act); b.writeUtf(e.label, 200); b.writeInt(e.minutes); b.writeBoolean(e.canSwitch); b.writeBoolean(e.queued); }
    }

    public static BusyCharsPacket decode(FriendlyByteBuf b) {
        int n = Math.min(64, b.readVarInt());
        List<Entry> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(new Entry(b.readUtf(32), b.readVarInt(), b.readUtf(200), b.readInt(), b.readBoolean(), b.readBoolean()));
        return new BusyCharsPacket(l);
    }

    public static void handle(BusyCharsPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> { CLIENT.clear(); for (Entry e : m.entries) CLIENT.put(e.id, e); });
        ctx.get().setPacketHandled(true);
    }

    /** Client: this character's busy entry, or null when they're free. */
    public static Entry of(com.xenowarriors.character.CharacterType t) { return t == null ? null : CLIENT.get(t.getId()); }
}
