package com.xenowarriors.network;

import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.karma.Karma;
import com.xenowarriors.party.Parties;
import com.xenowarriors.stats.Stats;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Client -> server: "show me everyone's levels and stats" (the M menu). */
public record PlayerStatsRequestPacket() {
    public static void encode(PlayerStatsRequestPacket m, FriendlyByteBuf buf) {}

    public static PlayerStatsRequestPacket decode(FriendlyByteBuf buf) {
        return new PlayerStatsRequestPacket();
    }

    public static void handle(PlayerStatsRequestPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        if (sender == null) return;
        List<PlayerStatsPacket.Entry> list = new ArrayList<>();
        for (ServerPlayer p : sender.server.getPlayerList().getPlayers()) {
            int[] lv = p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.statLevel.clone()).orElse(new int[3]);
            list.add(new PlayerStatsPacket.Entry(p.getGameProfile().getName(), ModCapabilities.getType(p).getId(),
                    Stats.characterLevel(p), lv[0], lv[1], lv[2], Karma.get(p), p.getHealth(), p.getMaxHealth(),
                    p == sender, Parties.sameParty(p, sender) && p != sender));
        }
        // you first, then your party, then everyone else by level
        list.sort((a, b) -> a.self() != b.self() ? (a.self() ? -1 : 1)
                : a.party() != b.party() ? (a.party() ? -1 : 1) : Integer.compare(b.level(), a.level()));
        ModNetwork.sendToPlayer(new PlayerStatsPacket(list), sender);
    }
}
