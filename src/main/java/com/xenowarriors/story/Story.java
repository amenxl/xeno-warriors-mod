package com.xenowarriors.story;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * Story Mode (Season 1): /xeno story start locks every character for that player except their own (You) characters and
 * gives them the storylines - Rhalix's or Mikiko's first, then the other, then the Sky Rebellion's and the Government's,
 * then the finale against Ultima. Characters you meet (friend or foe) can come along as bots; they become playable with
 * enough bond (40, or 75 for Rhalix, Mikiko, Senju and Kyle) or by finishing their storyline. Finishing everything
 * unlocks every character. Kept per player, in their save.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Story {
    private Story() {}

    public static final String KEY = "xw_story";
    public static final int BOND_NEED = 40, BIG_BOND_NEED = 75;
    public static final Set<CharacterType> BIG = EnumSet.of(CharacterType.RHALIX, CharacterType.MIKIKO, CharacterType.SENJU, CharacterType.KYLE);

    // ------------------------------------------------------------------ the saved state

    static CompoundTag root(Player p) {
        CompoundTag pers = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag r = pers.getCompound(KEY);
        pers.put(KEY, r);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, pers);
        return r;
    }

    private static Set<String> set(Player p, String k) {
        Set<String> s = new LinkedHashSet<>();
        ListTag l = root(p).getList(k, Tag.TAG_STRING);
        for (int i = 0; i < l.size(); i++) s.add(l.getString(i));
        return s;
    }

    private static void add(Player p, String k, String v) {
        Set<String> s = set(p, k);
        if (!s.add(v)) return;
        ListTag l = new ListTag();
        for (String x : s) l.add(StringTag.valueOf(x));
        root(p).put(k, l);
    }

    private static void remove(Player p, String k, String v) {
        Set<String> s = set(p, k);
        if (!s.remove(v)) return;
        ListTag l = new ListTag();
        for (String x : s) l.add(StringTag.valueOf(x));
        root(p).put(k, l);
    }

    public static boolean active(Player p) { return root(p).getBoolean("active"); }
    public static boolean finished(Player p) { return root(p).getBoolean("all"); }

    // ------------------------------------------------------------------ difficulty (Season 1 beaten: Normal, then Hard, Very Hard, Xeno)

    public static final String[] DIFFS = {"Story", "Normal", "Hard", "Very Hard", "Xeno"};
    /** Enemy health and damage (times) and extra enemies of each kind, by difficulty. Xeno is a big jump. */
    public static final double[] DIFF_HP = {1.0, 1.6, 2.4, 3.5, 7.0}, DIFF_DMG = {1.0, 1.3, 1.7, 2.2, 4.0};
    public static final int[] DIFF_EXTRA = {0, 0, 1, 1, 3};
    /** Money from missions (times), by difficulty. */
    public static final double[] DIFF_PAY = {1.0, 1.5, 2.0, 3.0, 5.0};

    /** The difficulty this player's story runs on now (0 = Story). */
    public static int difficulty(Player p) { return Math.max(0, Math.min(DIFFS.length - 1, root(p).getInt("diff"))); }
    /** The highest difficulty they've unlocked (beating the Season 1 story on one unlocks the next). */
    public static int unlockedDifficulty(Player p) { return Math.max(0, Math.min(DIFFS.length - 1, root(p).getInt("diffMax"))); }

    /** (Tests) as if they'd beaten the story up to this difficulty. */
    public static void unlockForTest(ServerPlayer p, int d) { root(p).putInt("diffMax", d); sync(p); }

    /** Plays the story again on this difficulty (unlocked ones only): the storylines start over; who you've met, bonds and unlocked characters stay. */
    public static boolean setDifficulty(ServerPlayer p, int d) {
        if (d < 0 || d >= DIFFS.length || d > unlockedDifficulty(p)) return false;
        if (StoryRun.inRun(p)) StoryRun.quit(p, "Changing the story's difficulty");
        CompoundTag r = root(p);
        r.putInt("diff", d);
        r.remove("done"); r.remove("cur"); r.remove("mi");
        p.sendSystemMessage(Component.literal("Story mode: " + DIFFS[d] + " - the storylines start over (who you've met, your bonds and your characters stay).")
                .withStyle(d == DIFFS.length - 1 ? ChatFormatting.DARK_PURPLE : ChatFormatting.GOLD, ChatFormatting.BOLD));
        sync(p);
        return true;
    }
    public static Set<String> done(Player p) { return set(p, "done"); }
    public static boolean pathDone(Player p, String path) { return done(p).contains(path); }
    public static Set<String> met(Player p) { return set(p, "met"); }
    public static boolean met(Player p, String charId) { return met(p).contains(charId); }
    public static Set<String> unlocked(Player p) { return set(p, "unlocked"); }
    public static Set<String> flags(Player p) { return set(p, "flags"); }
    public static boolean flag(Player p, String f) { return flags(p).contains(f); }
    public static void setFlag(Player p, String f) { add(p, "flags", f); }
    public static void clearFlag(Player p, String f) { remove(p, "flags", f); }
    /** The storyline being played (null: none picked). */
    public static String current(Player p) { String c = root(p).getString("cur"); return c.isEmpty() ? null : c; }
    /** How many missions of the current storyline are done. */
    public static int missionIndex(Player p) { return root(p).getInt("mi"); }

    /** Meets a character (they can come along as bots now); a Skylander's first meeting with someone can start at a higher bond. */
    public static void meet(ServerPlayer p, String charId) {
        if (CharacterType.byId(charId) == CharacterType.NONE) return;
        if (!met(p, charId)) {
            add(p, "met", charId);
            if (active(p)) p.displayClientMessage(Component.literal("Met " + CharacterType.byId(charId).getDisplayName().getString() + " - they can join you as a bot now").withStyle(ChatFormatting.AQUA), false);
        }
    }

    public static void start(ServerPlayer p) {
        CompoundTag r = root(p);
        boolean again = r.contains("done");
        r.putBoolean("active", true);
        p.sendSystemMessage(Component.literal(again ? "STORY MODE - back on. Your progress was kept." : "STORY MODE - Season 1").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        p.sendSystemMessage(Component.literal("Every character is locked except your own. Meet people to call them as bots; grow your bond (or finish their story) to play them. Pick Rhalix's or Mikiko's storyline to begin.").withStyle(ChatFormatting.GRAY));
        if (ModCapabilities.getType(p) != CharacterType.YOU && ModCapabilities.getType(p) != CharacterType.NONE && !playable(p, ModCapabilities.getType(p))) {
            com.xenowarriors.character.CharacterManager.setCharacter(p, CharacterType.NONE);
            p.sendSystemMessage(Component.literal("Make or pick your own character (Your Own) to play the story.").withStyle(ChatFormatting.YELLOW));
        }
        for (var b : new ArrayList<>(com.xenowarriors.bot.PartyBots.bots(p)))
            if (b.character() != CharacterType.YOU && !met(p, b.character().getId())) b.discard();
        sync(p);
        StoryRun.openMenu(p);
    }

    public static void stop(ServerPlayer p) {
        root(p).putBoolean("active", false);
        StoryRun.quit(p, "Story mode turned off");
        p.sendSystemMessage(Component.literal("Story mode off - every character is unlocked again. /xeno story start picks up where you left off.").withStyle(ChatFormatting.GRAY));
        sync(p);
    }

    public static void reset(ServerPlayer p) {
        StoryRun.quit(p, "Story reset");
        CompoundTag pers = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        pers.remove(KEY);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, pers);
        sync(p);
    }

    /** The storylines that can be started now. */
    public static List<String> available(Player p) {
        Set<String> d = done(p);
        List<String> l = new ArrayList<>();
        if (!d.contains("rhalix") || !d.contains("mikiko")) { for (String s : new String[]{"rhalix", "mikiko"}) if (!d.contains(s)) l.add(s); }
        else if (!d.contains("sr") || !d.contains("gov")) { for (String s : new String[]{"sr", "gov"}) if (!d.contains(s)) l.add(s); }
        else if (!d.contains("finale")) l.add("finale");
        return l;
    }

    public static boolean pick(ServerPlayer p, String path) {
        if (!active(p) || !available(p).contains(path) || StoryDefs.path(path) == null) return false;
        if (current(p) != null && !pathDone(p, current(p)) && missionIndex(p) > 0 && !current(p).equals(path)) {
            p.displayClientMessage(Component.literal("Finish " + StoryDefs.path(current(p)).title() + " first").withStyle(ChatFormatting.RED), true);
            return false;
        }
        root(p).putString("cur", path);
        root(p).putInt("mi", 0);
        p.displayClientMessage(Component.literal(StoryDefs.path(path).title() + " - begun").withStyle(ChatFormatting.GOLD), false);
        sync(p);
        return true;
    }

    /** The mission this player is on now (null: none). */
    public static StoryDefs.Mission mission(Player p) {
        StoryDefs.Path path = StoryDefs.path(current(p));
        if (path == null || pathDone(p, path.id())) return null;
        int i = missionIndex(p);
        return i >= 0 && i < path.missions().size() ? path.missions().get(i) : null;
    }

    /** A mission finished: the next one, or the storyline done (its main character unlocked; everyone after the finale). */
    static void completeMission(ServerPlayer p, String path, String missionId) {
        if (!path.equals(current(p))) return;
        StoryDefs.Path pd = StoryDefs.path(path);
        StoryDefs.Mission m = mission(p);
        if (pd == null || m == null || !m.id().equals(missionId)) return;
        int next = missionIndex(p) + 1;
        root(p).putInt("mi", next);
        if (next >= pd.missions().size()) {
            add(p, "done", path);
            root(p).remove("cur");
            if ("all".equals(pd.unlock())) {
                boolean first = !finished(p);
                root(p).putBoolean("all", true);
                int d = difficulty(p);
                if (first) p.sendSystemMessage(Component.literal("SEASON 1 STORY COMPLETE - every character is yours to play!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                else p.sendSystemMessage(Component.literal("SEASON 1 BEATEN ON " + DIFFS[d].toUpperCase(java.util.Locale.ROOT) + "!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                if (d >= unlockedDifficulty(p) && d < DIFFS.length - 1) {          // the next difficulty
                    root(p).putInt("diffMax", d + 1);
                    p.sendSystemMessage(Component.literal(DIFFS[d + 1].toUpperCase(java.util.Locale.ROOT) + " MODE UNLOCKED - pick it in the Story menu to play the story again, harder"
                            + (d + 1 == DIFFS.length - 1 ? " (much harder)." : ".")).withStyle(d + 1 == DIFFS.length - 1 ? ChatFormatting.DARK_PURPLE : ChatFormatting.AQUA, ChatFormatting.BOLD));
                } else if (d == DIFFS.length - 1) p.sendSystemMessage(Component.literal("You beat Xeno mode. There's nothing harder.").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
            } else if (pd.unlock() != null) {
                add(p, "unlocked", pd.unlock());
                p.sendSystemMessage(Component.literal(pd.title() + " complete - " + CharacterType.byId(pd.unlock()).getDisplayName().getString() + " is now playable!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            }
            List<String> av = available(p);
            if (!av.isEmpty()) p.sendSystemMessage(Component.literal("Next: " + String.join(" or ", av.stream().map(s -> StoryDefs.path(s) == null ? s : StoryDefs.path(s).title()).toList()) + " (Main Menu > Story)").withStyle(ChatFormatting.AQUA));
        }
        sync(p);
    }

    /** (Operators, tests) the current mission counts as done (its meetings too). */
    public static void skip(ServerPlayer p) {
        StoryDefs.Mission m = mission(p);
        if (m == null) return;
        for (StoryDefs.Step st : m.steps()) for (String c : st.meet()) meet(p, c);
        completeMission(p, current(p), m.id());
    }

    // ------------------------------------------------------------------ what's locked

    /** The bond a character needs to be playable in story mode. */
    public static int need(CharacterType t) { return BIG.contains(t) ? BIG_BOND_NEED : BOND_NEED; }

    /** Can this player pick this character right now (story mode only; seasons are checked on their own)? */
    public static boolean playable(Player p, CharacterType t) {
        if (!active(p) || finished(p) || t == CharacterType.YOU || t == CharacterType.NONE) return true;
        if (unlocked(p).contains(t.getId())) return true;
        return met(p, t.getId()) && bond(p, t) >= need(t);
    }

    /** Your bond with them, as your own character (the one the story is played with). */
    public static int bond(Player p, CharacterType t) {
        return com.xenowarriors.party.Bonds.level(p, t.getId());
    }

    /** Why this character can't be picked (null: they can). */
    public static String lockReason(Player p, CharacterType t) {
        if (playable(p, t)) return null;
        if (!met(p, t.getId())) return "Story mode: you haven't met " + t.getDisplayName().getString() + " yet";
        return "Story mode: bond " + bond(p, t) + "/" + need(t) + " with " + t.getDisplayName().getString() + " to play them" + (BIG.contains(t) ? " (or finish their storyline)" : "");
    }

    /** Can they come along as a bot? (story: only once you've met them) */
    public static boolean botAllowed(Player p, CharacterType t) {
        if (!active(p) || finished(p) || t == CharacterType.YOU) return true;
        return met(p, t.getId()) || unlocked(p).contains(t.getId());
    }

    // ------------------------------------------------------------------ to the client

    public static void sync(ServerPlayer p) {
        if (p instanceof net.minecraftforge.common.util.FakePlayer) return;
        com.xenowarriors.network.ModNetwork.sendToPlayer(new com.xenowarriors.network.StoryStatePacket(state(p)), p);
    }

    static CompoundTag state(ServerPlayer p) {
        CompoundTag t = new CompoundTag();
        t.putBoolean("active", active(p));
        t.putBoolean("all", finished(p));
        t.putInt("diff", difficulty(p));
        t.putInt("diffMax", unlockedDifficulty(p));
        String cur = current(p);
        t.putString("cur", cur == null ? "" : cur);
        t.putInt("mi", missionIndex(p));
        t.putLong("karma", com.xenowarriors.karma.KarmaLevel.of(p));
        ListTag paths = new ListTag();
        List<String> av = available(p);
        for (StoryDefs.Path pd : StoryDefs.PATHS.values()) {
            CompoundTag c = new CompoundTag();
            c.putString("id", pd.id()); c.putString("title", pd.title());
            int status = pathDone(p, pd.id()) ? 3 : pd.id().equals(cur) ? 2 : av.contains(pd.id()) ? 1 : 0;
            c.putInt("status", status);
            ListTag ms = new ListTag();
            for (int i = 0; i < pd.missions().size(); i++) {
                StoryDefs.Mission m = pd.missions().get(i);
                CompoundTag mt = new CompoundTag();
                mt.putString("title", m.title()); mt.putString("desc", m.desc() == null ? "" : m.desc());
                mt.putInt("karma", m.karma()); mt.putBoolean("extra", m.extra());
                mt.putBoolean("done", status == 3 || (status == 2 && i < missionIndex(p)));
                ms.add(mt);
            }
            c.put("missions", ms);
            paths.add(c);
        }
        t.put("paths", paths);
        ListTag chars = new ListTag();
        for (CharacterType ct : CharacterType.PLAYABLE) {
            CompoundTag c = new CompoundTag();
            c.putString("id", ct.getId());
            c.putBoolean("met", met(p, ct.getId()));
            c.putBoolean("play", playable(p, ct));
            c.putBoolean("unlocked", unlocked(p).contains(ct.getId()));
            c.putInt("bond", bond(p, ct));
            c.putInt("need", need(ct));
            StoryDefs.Side s = StoryDefs.SIDES.get(ct.getId());
            if (s != null) c.putString("side", s.title());
            c.putLong("sideReady", StorySides.readyIn(p, ct.getId()));
            chars.add(c);
        }
        t.put("chars", chars);
        StoryRun.Run r = StoryRun.runOf(p);
        if (r != null) { t.putString("obj", r.objective()); t.putBoolean("host", r.host.equals(p.getUUID())); t.putBoolean("inHub", r.hub); }
        String side = StorySides.objective(p);
        if (side != null) t.putString("sideObj", side);
        return t;
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            StoryRun.onLogin(p);
            sync(p);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p instanceof net.minecraftforge.common.util.FakePlayer) return;
        if (p.tickCount % 100 == 37 && active(p)) sync(p);
        if (p.tickCount % 200 == 11) StoryRun.rhalixDropIn(p);
    }
}
