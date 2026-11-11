package com.xenowarriors.faction;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Every way your standing with a faction moves, in one table: the code that changes reputation uses these numbers,
 * and the Factions screen lists them ("How to raise / How to lower"), so the two can't drift apart.
 * A rule with a daily cap stops counting after that much in one Minecraft day.
 * When you add a new way to gain or lose standing, add it here (and to the guide's Factions page).
 */
public enum RepRules {
    // ---- Government
    GOV_KILL_AGENT(Faction.GOV, -25, 0, "Fighting / killing Government agents"),
    GOV_CAUGHT(Faction.GOV, -30, 0, "Getting caught in a Gov facility (breaking things, opening containers, a face scan)"),
    GOV_BUY(Faction.GOV, 3, 0, "Buying at a Gov Supply Terminal (half the time)"),
    GOV_KILL_SR(Faction.GOV, 5, 0, "Defeating Sky Rebellion members"),
    GOV_KILL_ORDER(Faction.GOV, 8, 0, "Defeating Order assassins"),
    GOV_HELP_AGENT(Faction.GOV, 4, 40, "Defending Gov agents: kill whatever is attacking one"),
    GOV_DONATE(Faction.GOV, 1, 40, "Donating at a Gov terminal: sneak + right-click it holding emeralds (+1 each) or data shards (+5 each)"),
    GOV_RETURN(Faction.GOV, 12, 60, "Returning Gov equipment (keycards, human mods, Karma Scanners): sneak + right-click a Gov terminal with it - you get some Money back"),
    GOV_BOUNTY(Faction.GOV, 15, 0, "Completing bounties on the Sky Rebellion, corrupted villagers or the Order"),
    GOV_CORRUPTION(Faction.GOV, 2, 30, "Clearing corruption: defeating corrupted creatures"),
    GOV_RAID(Faction.GOV, 40, 0, "Fighting off every wave of a Government raid sent after you (they back off - and you get some Money)"),
    // ---- Sky Rebellion
    SR_KILL_MEMBER(Faction.SR, -25, 0, "Fighting / killing Sky Rebellion members"),
    SR_KILL_AGENT(Faction.SR, 5, 0, "Defeating Government agents"),
    SR_RAID(Faction.SR, 40, 0, "Fighting off every wave of a Sky Rebellion raid sent after you"),
    // ---- Sky Patrol & Skylanders
    SKY_KILL_SKYLANDER(Faction.SKY, -40, 0, "Hurting or killing Skylanders (-60 for a guardian)"),
    SKY_KILL_SR(Faction.SKY, 5, 0, "Defeating Sky Rebellion members"),
    SKY_SIEGE(Faction.SKY, 30, 0, "Helping stop a Sky Hater siege tower (Recarnet sky islands)"),
    SKY_RAIDER(Faction.SKY, 5, 0, "Defeating raiders and Sky Haters attacking Skylanders"),
    // ---- Townsfolk
    TOWN_KILL_VILLAGER(Faction.TOWN, -40, 0, "Killing villagers"),
    TOWN_KILL_PERSON(Faction.TOWN, -50, 0, "Killing peaceful townsfolk"),
    TOWN_RAIDER(Faction.TOWN, 8, 0, "Defeating raiders attacking a village"),
    TOWN_SOUL(Faction.TOWN, 5, 0, "Defeating someone possessed by (or hiding) a corrupted soul"),
    TOWN_RAID(Faction.TOWN, 40, 0, "Defending a village from a Sky Hater raid"),
    TOWN_BOUNTY(Faction.TOWN, 20, 0, "Completing bounties (bounty board)"),
    TOWN_TRADE(Faction.TOWN, 1, 10, "Trading with villagers (now and then)"),
    TOWN_CORRUPTION(Faction.TOWN, 2, 30, "Clearing corruption: defeating corrupted creatures"),
    TOWN_RAID_HELD(Faction.TOWN, 40, 0, "Fighting off every wave of an angry Townsfolk raid sent after you");

    public final Faction faction;
    public final int delta, dailyCap;
    public final String how;

    RepRules(Faction faction, int delta, int dailyCap, String how) {
        this.faction = faction; this.delta = delta; this.dailyCap = dailyCap; this.how = how;
    }

    /** Apply this rule (its own number of times {@code times}, e.g. 3 emeralds), respecting the daily cap. Returns what was added. */
    public int apply(ServerPlayer p, int times) {
        int d = delta * Math.max(1, times);
        if (dailyCap > 0) {
            CompoundTag root = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            long day = p.level().getDayTime() / 24000L;
            String k = "xw_rep_cap_" + name().toLowerCase(java.util.Locale.ROOT);
            if (root.getLong(k + "_day") != day) { root.putLong(k + "_day", day); root.putInt(k, 0); }
            int used = root.getInt(k), room = dailyCap - used;
            if (room <= 0) return 0;
            if (Math.abs(d) > room) d = Integer.signum(d) * room;
            root.putInt(k, used + Math.abs(d));
            p.getPersistentData().put(Player.PERSISTED_NBT_TAG, root);
        }
        Reputation.add(p, faction, d);
        return d;
    }

    public int apply(ServerPlayer p) { return apply(p, 1); }

    /** "How to raise" (positive) or "How to lower" (negative) lines for a faction, for the Factions screen. */
    public static List<String> lines(Faction f, boolean raise) {
        List<String> out = new ArrayList<>();
        for (RepRules r : values()) {
            if (r.faction != f || (r.delta > 0) != raise) continue;
            out.add((r.delta > 0 ? "+" : "") + r.delta + (r == GOV_DONATE ? "+" : "") + "  " + r.how + (r.dailyCap > 0 ? "  (up to " + r.dailyCap + " a day)" : ""));
        }
        return out;
    }
}
