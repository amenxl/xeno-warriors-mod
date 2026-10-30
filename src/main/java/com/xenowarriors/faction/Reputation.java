package com.xenowarriors.faction;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.TradeWithVillagerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Faction reputation: your standing with the Government, the Sky Rebellion, the Sky Patrol & Skylanders and the
 * Townsfolk, from -1000 to 1000, kept separately for each character you play. A character starts out liked by their
 * own teams (Government / Sky Rebellion / Sky Patrol tags) - Freedom Fighters start a little on the Government's bad
 * side - and what you do moves it: who you fight, bounties, defending villages and islands, getting caught, trading.
 *
 * What it changes: Gov Supply Terminal prices (and whether it serves you at all), villager prices, whether a group's
 * people attack you on sight or leave you alone, and bounty pay / which bounties you're offered.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Reputation {
    public static final int MIN = -1000, MAX = 1000;
    private static final String KEY = "xw_rep";

    /** Standing tiers. */
    public enum Tier {
        HATED("Hated", -600, 0xB01010), HOSTILE("Hostile", -150, 0xE04040), UNFRIENDLY("Unfriendly", -50, 0xE09040),
        NEUTRAL("Neutral", 50, 0xC8C8C8), FRIENDLY("Friendly", 400, 0x7FE07F), HONORED("Honored", 750, 0x40E0C0), REVERED("Revered", Integer.MAX_VALUE, 0xFFD040);
        public final String title; public final int below, color;
        Tier(String title, int below, int color) { this.title = title; this.below = below; this.color = color; }
        public static Tier of(int v) { for (Tier t : values()) if (v < t.below) return t; return REVERED; }
        public boolean atLeast(Tier t) { return ordinal() >= t.ordinal(); }
    }

    private Reputation() {}

    // ---------------------------------------------------------------- storage (per character)

    private static CompoundTag all(Player p) {
        CompoundTag root = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!root.contains(KEY)) { root.put(KEY, new CompoundTag()); p.getPersistentData().put(Player.PERSISTED_NBT_TAG, root); }
        return root.getCompound(KEY);
    }

    /** A character is gone for good (a deleted You): its standing goes with it. */
    public static void forget(Player p, String key) { all(p).remove(key); }

    private static int[] values(Player p) {
        CharacterType t = ModCapabilities.getType(p);
        CompoundTag all = all(p);
        String id = p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.key()).orElse(t.getId());
        int[] v = all.getIntArray(id);
        if (v.length != Faction.values().length) {          // first time as this character: starting standing from their teams
            v = start(t);
            all.putIntArray(id, v);
        }
        return v;
    }

    /** Where a character starts: liked by their own teams; Freedom Fighters are on the Government's bad side. */
    public static int[] start(CharacterType t) {
        int[] v = new int[Faction.values().length];
        switch (t) {
            case ULTIMA, YURI -> { v[Faction.GOV.ordinal()] = 450 - 150; }                          // Government, but Freedom Fighters now
            case TECHNICA, MAX -> { v[Faction.GOV.ordinal()] = 450; v[Faction.SKY.ordinal()] = 450; }
            case LEXY -> { v[Faction.SR.ordinal()] = 450; v[Faction.SKY.ordinal()] = 450; }
            case RHALIX, KYRIE, SAM -> v[Faction.SKY.ordinal()] = 450;
            case LOGAN, KILL, KEITA -> { v[Faction.SR.ordinal()] = 450; v[Faction.SKY.ordinal()] = -100; }
            case MIKIKO, KAERU, ZANE, STEVIE, GARRY -> v[Faction.GOV.ordinal()] = -100;            // Freedom Fighters
            case AIONI -> v[Faction.TOWN.ordinal()] = -100;                                          // a demon - people are wary
            default -> {}
        }
        return v;
    }

    public static int get(Player p, Faction f) { return values(p)[f.ordinal()]; }

    public static Tier tier(Player p, Faction f) { return Tier.of(get(p, f)); }

    public static void set(ServerPlayer p, Faction f, int value) {
        int[] v = values(p);
        Tier before = Tier.of(v[f.ordinal()]);
        v[f.ordinal()] = Math.max(MIN, Math.min(MAX, value));
        all(p).putIntArray(p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.key()).orElse(ModCapabilities.getType(p).getId()), v);
        Tier after = Tier.of(v[f.ordinal()]);
        if (before != after) {
            boolean up = after.ordinal() > before.ordinal();
            com.xenowarriors.network.PopupPacket.send(p, up ? com.xenowarriors.network.PopupPacket.GOOD : com.xenowarriors.network.PopupPacket.DANGER,
                    p.getUUID(), CharacterType.NONE.ordinal(), -1, f.title + ": " + after.title,
                    up ? "Your standing with the " + f.title + " went up." : "Your standing with the " + f.title + " dropped.", "");
            p.displayClientMessage(Component.literal(f.title + " now see you as " + after.title).withStyle(up ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        }
    }

    public static void add(ServerPlayer p, Faction f, int delta) {
        if (delta == 0) return;
        set(p, f, get(p, f) + delta);
    }

    // ---------------------------------------------------------------- what it changes

    /** Price change for a group's shop, in percent (negative = cheaper); null = they won't serve you. */
    public static Integer priceChange(Player p, Faction f) {
        return switch (tier(p, f)) {
            case HATED, HOSTILE -> null;
            case UNFRIENDLY -> 15;
            case NEUTRAL -> 0;
            case FRIENDLY -> -10;
            case HONORED -> -20;
            case REVERED -> -30;
        };
    }

    /** This group's people attack you on sight. */
    public static boolean hostile(Player p, Faction f) {
        return p != null && !p.isCreative() && !p.isSpectator() && !tier(p, f).atLeast(Tier.UNFRIENDLY);
    }

    /** This group's people leave you alone (even ones that attack strangers, like the Sky Rebellion). */
    public static boolean friendly(Player p, Faction f) {
        return p != null && tier(p, f).atLeast(Tier.FRIENDLY);
    }

    // ---------------------------------------------------------------- what moves it

    @SubscribeEvent
    public static void onKill(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (dead.level().isClientSide || com.xenowarriors.vr.Vr.inVr(dead) || com.xenowarriors.tower.Tower.is(dead.level())) return;
        ServerPlayer p = com.xenowarriors.collection.Collection.credit(e.getSource().getEntity());
        if (p == null) return;
        if (dead instanceof com.xenowarriors.gov.GovAgentEntity) { add(p, Faction.GOV, -25); add(p, Faction.SR, 5); }
        else if (dead instanceof com.xenowarriors.sr.SrMemberEntity) { add(p, Faction.SR, -25); add(p, Faction.GOV, 5); add(p, Faction.SKY, 5); }
        else if (dead instanceof com.xenowarriors.npc.SkylanderEntity s) add(p, Faction.SKY, s.guardian() ? -60 : -40);
        else if (dead instanceof com.xenowarriors.npc.HumanNpcEntity h) {
            if (h.raider() || (h.skyHater() && h.getTarget() instanceof com.xenowarriors.npc.SkylanderEntity)) { add(p, Faction.TOWN, 8); add(p, Faction.SKY, 5); }
            else if (h.carriesSoul()) add(p, Faction.TOWN, 5);
            else add(p, Faction.TOWN, -50);
        }
        else if (dead instanceof Villager) add(p, Faction.TOWN, -40);
    }

    @SubscribeEvent
    public static void onTrade(TradeWithVillagerEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.getRandom().nextInt(3) != 0) return;
        CompoundTag root = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        long day = p.level().getDayTime() / 24000L;
        if (root.getLong("xw_rep_trade_day") != day) { root.putLong("xw_rep_trade_day", day); root.putInt("xw_rep_trade_n", 0); }
        if (root.getInt("xw_rep_trade_n") >= 10) return;            // (at most +10 a day from trading)
        root.putInt("xw_rep_trade_n", root.getInt("xw_rep_trade_n") + 1);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, root);
        add(p, Faction.TOWN, 1);
    }

    /** Villagers price their trades from your Townsfolk standing (through their own gossip), and won't trade if you're hated. */
    @SubscribeEvent
    public static void onTalk(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getTarget() instanceof Villager v) || !(e.getEntity() instanceof ServerPlayer p)) return;
        int rep = get(p, Faction.TOWN);
        if (hostile(p, Faction.TOWN)) {
            p.displayClientMessage(Component.literal("The villager won't trade with you.").withStyle(ChatFormatting.RED), true);
            v.setUnhappyCounter(40);
            e.setCanceled(true);
            return;
        }
        var g = v.getGossips();
        // (our share goes in its own kinds of gossip - vanilla's cure discount and hit penalties are left as they are)
        g.remove(p.getUUID(), net.minecraft.world.entity.ai.gossip.GossipType.MINOR_POSITIVE);
        g.remove(p.getUUID(), net.minecraft.world.entity.ai.gossip.GossipType.MAJOR_NEGATIVE);
        if (rep > 0) g.add(p.getUUID(), net.minecraft.world.entity.ai.gossip.GossipType.MINOR_POSITIVE, Math.min(200, rep / 5));
        else if (rep < 0) g.add(p.getUUID(), net.minecraft.world.entity.ai.gossip.GossipType.MAJOR_NEGATIVE, Math.min(100, -rep / 10));
    }

    /** For the Factions screen: this character's standing with each group. */
    public static int[] all(ServerPlayer p, boolean copy) { return values(p).clone(); }
}
