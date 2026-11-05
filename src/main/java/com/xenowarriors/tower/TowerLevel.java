package com.xenowarriors.tower;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.collection.Collection;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tower Level: one per player (not per character - it's the player who climbs), kept in the player's save. It goes up
 * from playing the Tower and every 10 levels it doubles how much each of your kills counts in your Collection -
 * everywhere, in the Tower or not: x1 below level 10, x2 at 10, x4 at 20, x8 at 30, x16 at 40 ... In the Tower it
 * multiplies with the floor's kill multiplier (Tower Lv 10 (x2) on floor 25 (x3) = x6). That's all it multiplies:
 * the same thing the floor multiplier does (Collection kill counts - and so the every-100 Collection stat boosts);
 * not Money, bounties or drops. Your bots' and pets' kills count for you, as they always do in the Collection.
 * <p>Tower XP (each player in the run gets their own): {@link #XP_KILL} per kill credited to you in a run (your bots'
 * and pets' too), {@link #XP_WAVE} per wave cleared, 10 + the floor for each floor cleared, and 25 + 2x the floor on
 * top for beating a boss floor. XP to go from level n to n+1: 100 + 40 (n - 1). That's 2,340 XP to level 10 (a solo run
 * to floor 10 gives about 345 XP, one to floor 20 about 985: so about 7 runs to floor 10, or 2-3 to floor 20), 8,740 to level 20, 19,140 to level 30, 33,540 to level 40.
 * <p>While you're in a run a second bar under the Tower bar shows your level, progress and what your kills count.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TowerLevel {
    public static final String KEY = "xw_tower_level";
    public static final int MAX_LEVEL = 1000, STEP = 10;
    public static final int XP_KILL = 1, XP_WAVE = 3;
    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();

    private TowerLevel() {}

    // ---------------------------------------------------------------- numbers

    /** XP to go from this level to the next. */
    public static long needed(int level) { return 100L + 40L * Math.max(0, level - 1); }

    /** Total XP from level 1 to reach this level (for the guide / tests). */
    public static long totalTo(int level) { long t = 0; for (int l = 1; l < level; l++) t += needed(l); return t; }

    /** XP for clearing this floor (on top of its waves and kills). */
    public static int floorXp(int floor) { return 10 + Math.max(0, floor); }

    /** Extra XP for beating a boss floor. */
    public static int bossXp(int floor) { return 25 + 2 * Math.max(0, floor); }

    /** The kill multiplier at a Tower level: doubles every 10 levels (1, 2, 4, 8 ...), saturating instead of overflowing. */
    public static long mult(int level) {
        int steps = Math.max(0, level) / STEP;
        return steps >= 62 ? Long.MAX_VALUE : 1L << steps;
    }

    /** a x b without overflow (both >= 1). */
    public static long times(long a, long b) {
        if (a <= 0 || b <= 0) return 0;
        return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
    }

    /** "x2", "x1,024", "x1.2e18". */
    public static String fmt(long m) {
        return m >= 1_000_000_000_000L ? "x" + String.format(java.util.Locale.ROOT, "%.1e", (double) m) : "x" + String.format(java.util.Locale.ROOT, "%,d", m);
    }

    /**
     * How many Collection kills one kill counts for right now: the Tower level multiplier, times the floor's when
     * you're in a run. Clamped to what one enemy's Collection can ever hold (more could never count anyway).
     */
    public static int killValue(ServerPlayer p, int floorMult) {
        return (int) Math.min(Collection.MAX, times(mult(level(p)), Math.max(1, floorMult)));
    }

    // ---------------------------------------------------------------- storage (per player, in the player's save)

    private static CompoundTag tag(Player p) { return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(KEY); }

    public static int level(Player p) { return Math.max(1, Math.min(MAX_LEVEL, tag(p).getInt("lv"))); }

    public static long xp(Player p) { return Math.max(0L, tag(p).getLong("xp")); }

    public static float progress(Player p) {
        int lv = level(p);
        return lv >= MAX_LEVEL ? 1F : Math.min(1F, xp(p) / (float) needed(lv));
    }

    public static void set(ServerPlayer p, int level, long xp) {
        CompoundTag root = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag t = root.getCompound(KEY);
        t.putInt("lv", Math.max(1, Math.min(MAX_LEVEL, level)));
        t.putLong("xp", Math.max(0L, xp));
        root.put(KEY, t);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, root);
        sync(p);
    }

    /** Tower XP: levels up (with a message, and a popup when the multiplier goes up). */
    public static void addXp(ServerPlayer p, long amount, String why) {
        if (amount <= 0 || p instanceof net.minecraftforge.common.util.FakePlayer) return;
        int lv = level(p), before = lv;
        if (lv >= MAX_LEVEL) return;
        long xp = xp(p) + amount;
        while (lv < MAX_LEVEL && xp >= needed(lv)) { xp -= needed(lv); lv++; }
        if (lv >= MAX_LEVEL) xp = 0;
        set(p, lv, xp);
        if (lv > before) {
            boolean up = mult(lv) > mult(before);
            p.sendSystemMessage(Component.literal("Tower Level " + lv + "!" + (up ? " Every kill now counts " + fmt(mult(lv)) + " in your Collection - in the Tower or not!" : ""))
                    .withStyle(up ? ChatFormatting.GOLD : ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
            if (up) com.xenowarriors.network.PopupPacket.send(p, com.xenowarriors.network.PopupPacket.GOOD, p.getUUID(), com.xenowarriors.character.CharacterType.NONE.ordinal(), -1,
                    "Tower Level " + lv, "Kills count " + fmt(mult(lv)) + " everywhere", "");
            p.serverLevel().playSound(null, p.blockPosition(), up ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, up ? 1.0F : 1.4F);
            XenoWarriors.LOGGER.info("Tower level: {} reached {} (x{}) from {}", p.getGameProfile().getName(), lv, mult(lv), why);
        }
    }

    // ---------------------------------------------------------------- client sync and the HUD bar

    public static void sync(ServerPlayer p) {
        if (p.connection == null) return;
        com.xenowarriors.network.ModNetwork.sendToPlayer(new com.xenowarriors.network.TowerLevelPacket(level(p), progress(p)), p);
        updateBar(p);
    }

    /** Shows the Tower Level bar (under the Tower bar) while in a run. */
    static void showBar(ServerPlayer p) {
        ServerBossEvent b = BARS.computeIfAbsent(p.getUUID(), k -> new ServerBossEvent(Component.literal("Tower Level"), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10));
        if (!b.getPlayers().contains(p)) b.addPlayer(p);
        updateBar(p);
    }

    static void hideBar(ServerPlayer p) {
        ServerBossEvent b = BARS.remove(p.getUUID());
        if (b != null) b.removeAllPlayers();
    }

    /** "Tower Lv 12  •  45%  •  Kills x2 (here x6)". */
    static void updateBar(ServerPlayer p) {
        ServerBossEvent b = BARS.get(p.getUUID());
        if (b == null) return;
        int lv = level(p), floor = Tower.floorOf(p);
        long m = mult(lv), here = floor > 0 ? times(m, Tower.mult(floor)) : m;
        String pct = lv >= MAX_LEVEL ? "MAX" : Math.round(progress(p) * 100) + "% to Lv " + (lv + 1);
        b.setName(Component.literal("Tower Lv " + lv + "  •  " + pct + "  •  Kills " + fmt(m) + (here != m ? " (here " + fmt(here) + ")" : "")).withStyle(ChatFormatting.LIGHT_PURPLE));
        b.setProgress(progress(p));
    }

    /** (tests) the Tower Level bar's text for this player ("none" when it isn't up). */
    public static String testBar(ServerPlayer p) {
        ServerBossEvent b = BARS.get(p.getUUID());
        return b == null ? "none" : b.getName().getString() + " (progress " + Math.round(b.getProgress() * 100) + "%, shown=" + b.getPlayers().contains(p) + ")";
    }

    /** One line for chat / commands. */
    public static Component describe(ServerPlayer p) {
        int lv = level(p);
        return Component.literal("Tower Level " + lv + (lv >= MAX_LEVEL ? " (max)" : " - " + xp(p) + " / " + needed(lv) + " XP to " + (lv + 1))
                + " - your kills count " + fmt(mult(lv)) + " in the Collection" + (lv < MAX_LEVEL ? " (" + fmt(mult((lv / STEP + 1) * STEP)) + " at Lv " + (lv / STEP + 1) * STEP + ")" : "")).withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sync(p);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) hideBar(p);
    }

    /** (the player object is replaced on respawn / coming back from the End: keep the client's numbers and bar right) */
    @SubscribeEvent
    public static void onClone(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            ServerBossEvent b = BARS.get(p.getUUID());
            if (b != null) { b.removeAllPlayers(); b.addPlayer(p); }
            sync(p);
        }
    }
}
