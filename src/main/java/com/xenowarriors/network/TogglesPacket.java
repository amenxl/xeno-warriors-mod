package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server -> client: the character's transformations / buffs / passives (toggle wheel, Character Customization >
 * Passives) and its right+left click skills (Character Customization > R+L Click). See toggle.Toggles.
 */
public record TogglesPacket(List<Row> rows, List<Rl> rl, String rlSelected) {
    /** kind: Toggles.Kind ordinal. locked: why it can't be had yet ("" = it can). level / mastery (0-100): as the screens show them. */
    public record Row(String id, String title, int kind, int color, String locked, boolean enabled, boolean active, int level, float mastery) {}
    /** A right+left click skill: cooldown in ticks (already shortened), Karma cost. */
    public record Rl(String id, String name, String locked, int cooldown, int cost) {}

    public static volatile TogglesPacket CLIENT = new TogglesPacket(List.of(), List.of(), "");

    public static void encode(TogglesPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.rows.size());
        for (Row r : m.rows) {
            b.writeUtf(r.id, 32); b.writeUtf(r.title, 64); b.writeVarInt(r.kind); b.writeInt(r.color); b.writeUtf(r.locked, 64);
            b.writeBoolean(r.enabled); b.writeBoolean(r.active); b.writeVarInt(r.level); b.writeFloat(r.mastery);
        }
        b.writeVarInt(m.rl.size());
        for (Rl r : m.rl) { b.writeUtf(r.id, 32); b.writeUtf(r.name, 64); b.writeUtf(r.locked, 64); b.writeVarInt(r.cooldown); b.writeVarInt(r.cost); }
        b.writeUtf(m.rlSelected, 32);
    }

    public static TogglesPacket decode(FriendlyByteBuf b) {
        int n = Math.min(32, b.readVarInt());
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < n; i++) rows.add(new Row(b.readUtf(32), b.readUtf(64), b.readVarInt(), b.readInt(), b.readUtf(64), b.readBoolean(), b.readBoolean(), b.readVarInt(), b.readFloat()));
        int k = Math.min(32, b.readVarInt());
        List<Rl> rl = new ArrayList<>();
        for (int i = 0; i < k; i++) rl.add(new Rl(b.readUtf(32), b.readUtf(64), b.readUtf(64), b.readVarInt(), b.readVarInt()));
        return new TogglesPacket(rows, rl, b.readUtf(32));
    }

    public static void handle(TogglesPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> CLIENT = m);
        ctx.get().setPacketHandled(true);
    }
}
