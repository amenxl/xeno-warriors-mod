package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The Gov terminal's C2 Procedures page (you.YouPaths). Server -> client: the paths and why each can't be taken yet
 * (mode -1 opens / refreshes, -2 closes). Client -> server: take path `mode` (a Skylander's form in `form`), or -3 to
 * ask for the page.
 */
public record C2Packet(List<Row> rows, long money, String who, int mode, int form) {
    public record Row(int id, String title, String desc, int level, String need, long price, boolean card, String why) {}

    public static void encode(C2Packet m, FriendlyByteBuf b) {
        b.writeVarInt(m.rows.size());
        for (Row r : m.rows) {
            b.writeVarInt(r.id); b.writeUtf(r.title, 64); b.writeUtf(r.desc, 512); b.writeVarInt(r.level); b.writeUtf(r.need, 32);
            b.writeVarLong(r.price); b.writeBoolean(r.card); b.writeUtf(r.why, 128);
        }
        b.writeVarLong(m.money); b.writeUtf(m.who, 160); b.writeVarInt(m.mode + 4); b.writeVarInt(m.form);
    }

    public static C2Packet decode(FriendlyByteBuf b) {
        int n = Math.min(8, b.readVarInt());
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < n; i++) rows.add(new Row(b.readVarInt(), b.readUtf(64), b.readUtf(512), b.readVarInt(), b.readUtf(32), b.readVarLong(), b.readBoolean(), b.readUtf(128)));
        return new C2Packet(rows, b.readVarLong(), b.readUtf(160), b.readVarInt() - 4, b.readVarInt());
    }

    public static void handle(C2Packet m, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) {
            if (m.mode == -3) { if (com.xenowarriors.gov.GovShop.atTerminalForC2(p)) com.xenowarriors.you.YouPaths.open(p); }
            else if (m.mode > 0) com.xenowarriors.you.YouPaths.request(p, m.mode, m.form);
        } else DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.screen.C2Screen.received(m));
        ctx.get().setPacketHandled(true);
    }
}
