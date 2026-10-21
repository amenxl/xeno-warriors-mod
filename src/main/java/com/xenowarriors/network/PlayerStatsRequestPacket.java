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
        var d = sender.getCapability(ModCapabilities.PLAYER_CHARACTER).resolve().orElse(null);
        float[] xp = new float[3];
        if (d != null) {
            for (com.xenowarriors.stats.Stat s : com.xenowarriors.stats.Stat.values()) {
                int i = s.ordinal();
                xp[i] = d.statLevel[i] >= com.xenowarriors.stats.Stat.MAX_LEVEL ? 1F : Math.min(1F, d.statXp[i] / s.needed(d.statLevel[i]));
            }
        }
        int uses = com.xenowarriors.karma.KarmaSensing.uses(sender);
        int pets = d == null ? 0 : d.pets.size() + d.wingPets.size();
        PlayerStatsPacket.Me me = new PlayerStatsPacket.Me(xp[0], xp[1], xp[2],
                (int) Math.round(com.xenowarriors.karma.KarmaSensing.radius(uses)), com.xenowarriors.karma.KarmaSensing.duration(uses) / 20,
                uses, d == null ? 0 : d.sealed.size(), com.xenowarriors.mikiko.HekubladeRealm.inRealm(sender),
                com.xenowarriors.mikiko.HekubladeRealm.isPrisoner(sender), pets, d == null ? 0 : d.flightTicks / 1200,
                com.xenowarriors.stats.Mining.level(sender), com.xenowarriors.stats.Mining.progress(sender),
                d == null ? 1 : d.bladeLevel, com.xenowarriors.mikiko.BladeLevel.progress(sender),
                d == null ? 0 : Math.max(0, d.realmTicks < 0 ? com.xenowarriors.mikiko.BladeLevel.maxTicks(d.bladeLevel) : d.realmTicks),
                d == null ? 0 : com.xenowarriors.mikiko.BladeLevel.maxTicks(d.bladeLevel),
                d == null ? 0 : (int) Math.max(0, d.realmCooldownUntil - sender.server.overworld().getGameTime()),
                com.xenowarriors.stats.Crafting.craftLevel(sender), com.xenowarriors.stats.Crafting.craftProgress(sender),
                com.xenowarriors.stats.Crafting.smeltLevel(sender), com.xenowarriors.stats.Crafting.smeltProgress(sender));
        ModNetwork.sendToPlayer(new PlayerStatsPacket(list, me), sender);
    }
}
