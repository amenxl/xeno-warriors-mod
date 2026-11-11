package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server -> player: open "pick people to join your party" (who's taken, who's still recovering, how many can join, the
 * party size limit, and what each off-duty character is up to - bot.OffDuty).
 */
public record OpenBotPickPacket(String[] taken, String[] cooling, int free, int limit, List<Act> acts) {
    /** One character's off-duty activity (OffDuty.Act ordinal, 0 = free), its label, your bond, whether it's your job for it. */
    public record Act(String id, int act, String label, int bond, boolean ordered) {}

    public OpenBotPickPacket(String[] taken, String[] cooling, int free) { this(taken, cooling, free, 4, List.of()); }

    public static void encode(OpenBotPickPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.taken.length); for (String s : m.taken) b.writeUtf(s);
        b.writeVarInt(m.cooling.length); for (String s : m.cooling) b.writeUtf(s);
        b.writeVarInt(m.free);
        b.writeVarInt(m.limit);
        b.writeVarInt(m.acts.size());
        for (Act a : m.acts) { b.writeUtf(a.id, 32); b.writeVarInt(a.act); b.writeUtf(a.label, 200); b.writeVarInt(a.bond); b.writeBoolean(a.ordered); }
    }
    public static OpenBotPickPacket decode(FriendlyByteBuf b) {
        String[] t = new String[Math.min(64, b.readVarInt())]; for (int i = 0; i < t.length; i++) t[i] = b.readUtf();
        String[] c = new String[Math.min(64, b.readVarInt())]; for (int i = 0; i < c.length; i++) c[i] = b.readUtf();
        int free = b.readVarInt(), limit = b.readVarInt();
        int n = Math.min(64, b.readVarInt());
        List<Act> acts = new ArrayList<>();
        for (int i = 0; i < n; i++) acts.add(new Act(b.readUtf(32), b.readVarInt(), b.readUtf(200), b.readVarInt(), b.readBoolean()));
        return new OpenBotPickPacket(t, c, free, limit, acts);
    }
    public static void handle(OpenBotPickPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.PacketScreens.openBotPick(m));
        ctx.get().setPacketHandled(true);
    }
}
