package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * With DragonMineZ: a bot teammate spends its own TP on its DragonMineZ stats when its next mission needs a higher
 * level than it has - only then, and only what that takes.
 * <ul>
 *   <li>When you start a mission and a bot can't start it (or its own next saga step) only because its DragonMineZ
 *       level is too low, it trains first: it buys stats with its TP until it reaches that level, then starts the
 *       mission ({@link DmzBotQuests} calls {@link #trainFor} / {@link #trainForNext}).</li>
 *   <li>Every 5 s (right after it earns its passive TP) each bot looks at the next mission - your next saga step, then
 *       its own next saga step - and trains towards that mission's level with what it has, so it's ready next time.
 *       It never starts a mission by itself.</li>
 *   <li>It spends only what reaching that level costs, and keeps the rest of its TP (you can still spend it in its
 *       Dragon Ball menu). Nothing is spent when anything other than the level blocks the mission (a step not done
 *       yet, alignment, race, a skill...), so TP is never wasted on a mission it couldn't take anyway.</li>
 *   <li>Stats are bought exactly as DragonMineZ's own "+" button does for a player (its IncreaseStatC2S): its free
 *       attribute points first, then TP at DragonMineZ's own price ({@code calculateStatIncrease} /
 *       {@code calculateRecursiveCost}: the price of each point grows with the character's total stats, times the
 *       server's and the class's TP-cost multipliers), within DragonMineZ's per-stat and total caps. Raising
 *       Resistance / Energy fills the new stamina / energy like DragonMineZ does; its health is refreshed
 *       (DmzBotStats) and the new health is given to it.</li>
 *   <li>Which stats - one point at a time, each to the stat furthest below its share for the bot's DragonMineZ class
 *       ({@link #SPLIT}; STR/SKP/RES/VIT/PWR/ENE). Every class keeps Vitality and Resistance at 15-35% so the bot
 *       survives the mission it trained for.</li>
 * </ul>
 * Only the bot's own saved DragonMineZ character changes - never yours (the one you're playing), and never a bot whose
 * Dragon Ball menu is open (those points are yours to click). All by reflection; nothing happens without DragonMineZ.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DmzBotTraining {
    private DmzBotTraining() {}

    /** DragonMineZ's stat names, in the order of the weights below. */
    static final String[] STATS = {"STR", "SKP", "RES", "VIT", "PWR", "ENE"};

    /**
     * The share of each stat (STR, SKP, RES, VIT, PWR, ENE; out of 100) a bot of each DragonMineZ class trains towards:
     * its class's main stats (what DragonMineZ's class base stats and scalings favour) with Vitality and Resistance
     * kept up for every class. Any other class (a modpack's own): even, with VIT/RES a little higher.
     */
    static final Map<String, int[]> SPLIT = Map.of(
            "warrior",       new int[]{30, 10, 20, 25,  5, 10},   // Strength fighter
            "berserker",     new int[]{35,  5, 15, 30,  5, 10},   // Strength + lots of health
            "martialartist", new int[]{10, 35, 15, 25,  5, 10},   // Strike Power
            "spiritualist",  new int[]{ 5,  5, 15, 20, 35, 20},   // Ki Power + Energy
            "cleric",        new int[]{ 5,  5, 20, 20, 20, 30},   // Energy + Ki Power, sturdy
            "tank",          new int[]{15,  5, 35, 35,  0, 10},   // Resistance + Vitality
            "paladin",       new int[]{20,  5, 25, 25, 15, 10});  // all-round, sturdy
    static final int[] DEFAULT_SPLIT = {15, 15, 20, 20, 15, 15};

    static int[] split(String cls) {
        int[] w = cls == null ? null : SPLIT.get(cls.toLowerCase(Locale.ROOT).replace("_", "").replace(" ", ""));
        return w != null ? w : DEFAULT_SPLIT;
    }

    /** (For the tests) what happened, newest last. */
    public static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());
    /** (For the tests) trainings that spent anything, and all the TP they spent. */
    public static int trainings;
    public static long tpSpent;

    private static void note(String s) {
        if (LOG.size() > 80) LOG.remove(0);
        LOG.add(s);
        XenoWarriors.LOGGER.info("XW dmz bot training: {}", s);
    }

    private static Object m(Object o, String name, Object... a) throws Exception { return DmzBotQuests.m(o, name, a); }

    static int level(Object sd) throws Exception { return ((Number) m(sd, "getLevel")).intValue(); }

    private static float tp(Object res) throws Exception { return ((Number) m(res, "getTrainingPoints")).floatValue(); }

    private static int ap(Object res) throws Exception { return ((Number) m(res, "getPendingAttributePoints")).intValue(); }

    static String cls(Object sd) {
        try { return String.valueOf(m(m(sd, "getCharacter"), "getCharacterClass")); } catch (Throwable t) { return "?"; }
    }

    // ------------------------------------------------------------------ the mission's level

    /** The highest DragonMineZ level any of this mission's requirements (to see it, and to start it) asks for; 0 = none. */
    static int requiredLevel(Object q) {
        if (q == null) return 0;
        int need = 0;
        try { need = Math.max(need, levelIn(m(q, "getPrerequisites"), 0)); } catch (Throwable ignored) {}
        try { need = Math.max(need, levelIn(m(q, "getStartRequirements"), 0)); } catch (Throwable ignored) {}
        return need;
    }

    private static int levelIn(Object prereqs, int depth) throws Exception {
        if (prereqs == null || depth > 8) return 0;
        int need = 0;
        for (Object c : (List<?>) m(prereqs, "conditions")) {
            if (c == null) continue;
            if ((Boolean) m(c, "isNestedGroup")) { need = Math.max(need, levelIn(m(c, "getNested"), depth + 1)); continue; }
            if ("LEVEL".equals(String.valueOf(m(c, "getType")))) {
                Object min = m(c, "getMinLevel");
                if (min instanceof Number n) need = Math.max(need, n.intValue());
            }
        }
        return need;
    }

    // ------------------------------------------------------------------ buying stats, DragonMineZ's way

    /** What a training did. */
    public record Result(int levelBefore, int levelAfter, int target, int points, int freePoints, int tpSpent, float tpLeft, int[] perStat, String stop) {
        boolean reached() { return levelAfter >= target; }
        String split() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < STATS.length; i++) if (perStat[i] > 0) sb.append(sb.length() == 0 ? "" : " ").append(STATS[i]).append('+').append(perStat[i]);
            return sb.toString();
        }
    }

    /**
     * Raises this character's stats one point at a time - free attribute points first, then TP, at DragonMineZ's own
     * price and caps - until it's at {@code target} level or it can't buy the next point. Changes {@code sd} only.
     */
    static Result train(Object sd, int target) throws Exception {
        Object res = m(sd, "getResources");
        int max = ((Number) m(sd, "getConfiguredMaxValue")).intValue();
        int[] w = split(cls(sd));
        int wsum = 0;
        for (int x : w) wsum += x;
        int lv0 = level(sd), points = 0, free = 0, spent = 0;
        int[] given = new int[STATS.length];
        boolean[] capped = new boolean[STATS.length];
        String stop = null;
        for (int guard = 0; level(sd) < target; guard++) {
            if (guard > 500_000) { stop = "too many points"; break; }
            int pick = -1;
            double best = -Double.MAX_VALUE;
            for (int i = 0; i < STATS.length; i++) {
                if (capped[i]) continue;
                double behind = w[i] * (points + 1.0) / wsum - given[i];
                if (behind > best) { best = behind; pick = i; }
            }
            if (pick < 0) { stop = "every stat at DragonMineZ's cap"; break; }
            if (((Number) m(sd, "getMaxAllowedIncreaseForStat", STATS[pick], 1)).intValue() <= 0) { capped[pick] = true; continue; }
            if (ap(res) > 0) {                                                       // (like DragonMineZ: free points first)
                raise(sd, res, STATS[pick]);
                m(res, "removePendingAttributePoints", 1);
                free++;
            } else {
                float have = tp(res);
                int n = have <= 0 ? 0 : ((Number) m(sd, "calculateStatIncrease", 1, have, max)).intValue();
                int cost = n <= 0 ? Integer.MAX_VALUE : ((Number) m(sd, "calculateRecursiveCost", n, max)).intValue();
                if (n <= 0 || cost > have) { stop = "out of TP"; break; }
                raise(sd, res, STATS[pick]);
                m(res, "removeTrainingPoints", (float) cost);
                spent += cost;
            }
            given[pick]++;
            points++;
        }
        return new Result(lv0, level(sd), target, points, free, spent, tp(res), given, stop);
    }

    /** DragonMineZ's IncreaseStatC2S.increaseStat for one point, minus what it does to a player entity (the bot's health is refreshed after). */
    private static void raise(Object sd, Object res, String stat) throws Exception {
        Object st = m(sd, "getStats");
        switch (stat) {
            case "STR" -> m(st, "addStrength", 1);
            case "SKP" -> m(st, "addStrikePower", 1);
            case "RES" -> {
                float before = ((Number) m(sd, "getMaxStamina")).floatValue();
                m(st, "addResistance", 1);
                float after = ((Number) m(sd, "getMaxStamina")).floatValue();
                if (after > before) m(res, "addStamina", after - before);
            }
            case "VIT" -> m(st, "addVitality", 1);
            case "PWR" -> m(st, "addKiPower", 1);
            case "ENE" -> {
                float before = ((Number) m(sd, "getMaxEnergy")).floatValue();
                m(st, "addEnergy", 1);
                float after = ((Number) m(sd, "getMaxEnergy")).floatValue();
                if (after > before) m(res, "addEnergy", after - before);
            }
            default -> {}
        }
    }

    /** A copy of the bot's data to try things on (never saved). */
    private static DmzBotQuests.Data scratch(DmzBotQuests.Data d) throws Exception {
        CompoundTag snap = DmzPerCharacter.saveData(d.sd);
        Player stand = PartyBots.standIn(d.bot);
        Object sd = stand == null || snap == null ? null : DmzPerCharacter.blank(stand);
        if (sd == null || !DmzPerCharacter.loadData(sd, snap)) return null;
        return new DmzBotQuests.Data(d.bot, d.owner, d.key, false, sd, m(sd, "getPlayerQuestData"), null);
    }

    /**
     * Would it start this mission at that level (all else as it is)? Tried on a copy with unlimited TP. DragonMineZ
     * 2.1 keeps the six stats as attributes of the player entity - the bot's stand-in, shared by every copy of its
     * data - so the bot's real stats are put back on the stand-in afterwards.
     */
    static boolean levelOnly(DmzBotQuests.Data d, String key, int target) {
        String why = DmzBotQuests.why;                                             // (the caller's reason stays what it was)
        CompoundTag mine = null;
        try {
            CompoundTag all = DmzPerCharacter.saveData(d.sd);
            mine = all == null ? null : all.getCompound("Stats").copy();
            if (mine == null) return false;
            DmzBotQuests.Data s = scratch(d);
            if (s == null) return false;
            Object res = m(s.sd, "getResources");
            m(res, "setTrainingPoints", 1.0E9F);
            Result r = train(s.sd, target);
            if (!r.reached()) { note(d.key + ": level " + target + " for " + key + " can't be reached at all (" + r.stop + ")"); return false; }
            return DmzBotQuests.eligible(s, key);
        } catch (Throwable t) {
            XenoWarriors.LOGGER.warn("Couldn't check a bot's DragonMineZ mission level", t);
            return false;
        } finally {
            DmzBotQuests.why = why;
            if (mine != null) try { m(m(d.sd, "getStats"), "load", mine); } catch (Throwable t) { XenoWarriors.LOGGER.warn("Couldn't put a bot's DragonMineZ stats back", t); }
        }
    }

    /**
     * The TP this bot must keep for its next mission's level: what reaching it would cost (DragonMineZ's price, tried
     * on a copy), for the first next step that only its level blocks - the same one {@link #ready} trains for. 0 = none.
     * Its new ki attacks and techniques from masters ({@link DmzBotTechniques}) are only bought with TP above this.
     */
    static float reserve(DmzBotQuests.Data d) {
        if (d == null) return 0;
        String why = DmzBotQuests.why;
        try {
            Object own = DmzPerCharacter.live(d.owner);
            Object ownQ = own == null || d.live ? null : m(own, "getPlayerQuestData");
            List<String> next = new ArrayList<>();
            if (ownQ != null) next.addAll(nextSteps(ownQ));
            for (String k : nextSteps(d.pqd)) if (!next.contains(k)) next.add(k);
            int lv = level(d.sd);
            for (String k : next) {
                if ((Boolean) m(d.pqd, "isQuestCompleted", k) || "ACCEPTED".equals(String.valueOf(m(d.pqd, "getQuestStatus", k)))) continue;
                if (DmzBotQuests.eligible(d, k)) continue;
                int need = requiredLevel(DmzBotQuests.quest(k));
                if (need <= lv || !levelOnly(d, k, need)) continue;
                CompoundTag mine = DmzPerCharacter.saveData(d.sd).getCompound("Stats").copy();
                try {
                    DmzBotQuests.Data s = scratch(d);
                    if (s == null) return 0;
                    m(m(s.sd, "getResources"), "setTrainingPoints", 1.0E9F);
                    Result r = train(s.sd, need);
                    return r.reached() ? r.tpSpent : Float.MAX_VALUE;              // (a level it can never buy: keep it all)
                } finally {
                    try { m(m(d.sd, "getStats"), "load", mine); } catch (Throwable t) { XenoWarriors.LOGGER.warn("Couldn't put a bot's DragonMineZ stats back", t); }
                }
            }
        } catch (Throwable t) {
            XenoWarriors.LOGGER.warn("Couldn't work out a bot's DragonMineZ training reserve", t);
            return Float.MAX_VALUE;                                                 // (unsure: spend nothing)
        } finally { DmzBotQuests.why = why; }
        return 0;
    }

    // ------------------------------------------------------------------ when the bot starts a mission with you

    /** The Data whose mission start found a level-only block in this pick (so trainForNext doesn't spend twice). */
    private static java.lang.ref.WeakReference<DmzBotQuests.Data> blockedIn = new java.lang.ref.WeakReference<>(null);

    /**
     * DmzBotQuests couldn't start this mission for the bot: if only its DragonMineZ level is in the way, it trains up
     * (its own TP) and true = it can start it now. Partial training (not enough TP) is kept and false is returned.
     */
    static boolean trainFor(DmzBotQuests.Data d, String key) {
        if (d == null || d.live) return false;
        try {
            Object q = DmzBotQuests.quest(key);
            int need = requiredLevel(q), lv = level(d.sd);
            if (need <= lv || !levelOnly(d, key, need)) return false;
            blockedIn = new java.lang.ref.WeakReference<>(d);
            Result r = spend(d, key, need, true);
            return r != null && r.reached() && DmzBotQuests.eligible(d, key);
        } catch (Throwable t) {
            XenoWarriors.LOGGER.warn("A bot couldn't train for its DragonMineZ mission", t);
            return false;
        }
    }

    /** None of these could be started as they are: trains for the first one only its level blocks (in order) and returns it if it can start now. */
    static String trainForNext(DmzBotQuests.Data d, String skip, List<String> keys) {
        if (d == null || d.live || blockedIn.get() == d) return null;
        try {
            int lv = level(d.sd);
            for (String k : keys) {
                if (k.equals(skip)) continue;
                int need = requiredLevel(DmzBotQuests.quest(k));
                if (need <= lv || !levelOnly(d, k, need)) continue;
                blockedIn = new java.lang.ref.WeakReference<>(d);
                Result r = spend(d, k, need, true);
                return r != null && r.reached() && DmzBotQuests.eligible(d, k) ? k : null;
            }
        } catch (Throwable t) { XenoWarriors.LOGGER.warn("A bot couldn't train for its next DragonMineZ mission", t); }
        return null;
    }

    /** Trains for real on the bot's data, saves it, refreshes the bot and says so. Null = nothing could be bought. */
    private static Result spend(DmzBotQuests.Data d, String key, int need, boolean tellShort) throws Exception {
        Result r = train(d.sd, need);
        String what = d.key + " (" + cls(d.sd) + ") for " + key + " (needs level " + need + "): level " + r.levelBefore + " -> " + r.levelAfter + ", "
                + r.points + " points (" + r.split() + ") " + (r.freePoints > 0 ? r.freePoints + " free + " : "") + r.tpSpent + " TP, " + r.tpLeft + " TP left"
                + (r.reached() ? "" : " - stopped: " + r.stop);
        if (r.points == 0) {
            note("nothing bought: " + what);
            if (tellShort) d.owner.displayClientMessage(Component.empty().append(d.bot.character().getDisplayName())
                    .append(" needs DragonMineZ level " + need + " for ").append(DmzBotQuests.title(key))
                    .append(" (level " + r.levelBefore + ", not enough TP yet)").withStyle(ChatFormatting.GRAY), false);
            return null;
        }
        d.touched.add("Stats"); d.touched.add("Resources");
        d.commit();
        if (d.tag != null) DmzPerCharacter.store(d.owner, d.key, d.tag.copy());       // (a new tag: the bot's fighting copy re-reads it at once)
        trainings++;
        tpSpent += r.tpSpent;
        note("trained " + what);
        float before = d.bot.getMaxHealth();
        DmzBotStats.apply(d.bot);
        float gained = d.bot.getMaxHealth() - before;
        if (gained > 0 && d.bot.isAlive() && !d.bot.downed()) d.bot.heal(gained);       // (like DragonMineZ gives a player the new health)
        String cost = String.format(Locale.US, "%,d TP", r.tpSpent) + (r.freePoints > 0 ? " + " + r.freePoints + " free points" : "");
        Component line = r.reached()
                ? Component.empty().append(d.bot.character().getDisplayName()).append(" trained up to level " + r.levelAfter + " (spent " + cost + ")").withStyle(ChatFormatting.AQUA)
                : Component.empty().append(d.bot.character().getDisplayName()).append(" trained to level " + r.levelAfter + " (spent " + cost + ") - needs level " + need + " for ")
                        .append(DmzBotQuests.title(key)).append(", not enough TP yet").withStyle(ChatFormatting.GRAY);
        if (r.reached() || tellShort) d.owner.displayClientMessage(line, false);
        return r;
    }

    // ------------------------------------------------------------------ every 5 s: get ready for the next one

    private record Memo(int sig, boolean nothing, float retryAt) {}
    private static final Map<PartyBotEntity, Memo> MEMO = new WeakHashMap<>();

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p instanceof FakePlayer || p.tickCount % 100 != 61 || !DmzBotQuests.ready()) return;
        if (DmzPerCharacter.viewing(p) != null) return;                                // (a bot's menu is open: its points are yours to click)
        for (PartyBotEntity b : PartyBots.bots(p)) {
            if (b.journey()) continue;
            try { ready(b, p, false); } catch (Throwable t) { XenoWarriors.LOGGER.warn("A bot couldn't get ready for its next DragonMineZ mission", t); }
        }
    }

    /**
     * Gets the bot ready for its next mission: the next step of each saga you're on, then of each saga it's on. Trains
     * for the first of those only its level blocks (one per look). Returns what it did (for the logs and tests).
     */
    public static String ready(PartyBotEntity b, ServerPlayer o, boolean force) throws Exception {
        String bkey = DmzPerCharacter.botKey(b);
        if (DmzPerCharacter.isLive(o, bkey) || bkey.equals(DmzPerCharacter.viewing(o))) return "live";
        CompoundTag tag = DmzPerCharacter.saved(o, bkey);
        if (tag == null || !tag.getCompound("Status").getBoolean("HasCreatedChar")) return "no character";
        CompoundTag res = tag.getCompound("Resources");
        float tp = res.getFloat("TrainingPointsF");
        if (tp < 1 && res.getInt("PendingAttributePoints") <= 0) return "no TP";
        Object own = DmzPerCharacter.live(o);
        Object ownQ = own == null ? null : m(own, "getPlayerQuestData");
        int sig = Objects.hash(ownQ == null ? 0 : m(ownQ, "serializeNBT").hashCode(),          // (yours or its missions, or its stats changed: look again)
                tag.getCompound("PlayerQuestData").hashCode(), tag.getCompound("Stats").hashCode());
        Memo memo = MEMO.get(b);
        if (!force && memo != null && memo.sig == sig && (memo.nothing || tp < memo.retryAt)) return "same as before";
        DmzBotQuests.Data d = DmzBotQuests.open(b, false);
        if (d == null || d.live) return "no data";
        List<String> next = new ArrayList<>();
        if (ownQ != null) next.addAll(nextSteps(ownQ));
        for (String k : nextSteps(d.pqd)) if (!next.contains(k)) next.add(k);
        int lv = level(d.sd);
        String out = "nothing to train for (level " + lv + ", next " + next + ")";
        boolean nothing = true;
        float retry = 0;
        for (String k : next) {
            if ((Boolean) m(d.pqd, "isQuestCompleted", k) || "ACCEPTED".equals(String.valueOf(m(d.pqd, "getQuestStatus", k)))) continue;
            if (DmzBotQuests.eligible(d, k)) { out = "ready for " + k + " (level " + lv + ")"; continue; }
            int need = requiredLevel(DmzBotQuests.quest(k));
            if (need <= lv || !levelOnly(d, k, need)) continue;
            Result r = spend(d, k, need, false);
            if (r == null) { out = "needs level " + need + " for " + k + ", can't buy a point yet (" + tp + " TP)"; nothing = false; retry = tp + 1; break; }
            out = (r.reached() ? "trained to " : "trained towards ") + "level " + need + " for " + k + ": " + r.levelBefore + " -> " + r.levelAfter + " for " + r.tpSpent + " TP";
            nothing = false;                                                           // (its stats changed, so the next look runs anyway)
            retry = r.reached() ? 0 : r.tpLeft + 1;
            break;
        }
        MEMO.put(b, new Memo(sig, nothing, retry));
        return out;
    }

    /** The next step of each saga this mission data has begun: the first one not done, or the one after it if it's on it. */
    @SuppressWarnings("unchecked")
    static List<String> nextSteps(Object pqd) throws Exception {
        List<String> out = new ArrayList<>();
        Map<String, Object> sagas = (Map<String, Object>) DmzBotQuests.registry.getMethod("getAllSagas").invoke(null);
        if (sagas == null) return out;
        java.lang.reflect.Method key = DmzBotQuests.pqdClass.getMethod("sagaQuestKey", String.class, int.class);
        for (Object s : new TreeMap<>(sagas).values()) {
            List<Object> qs = (List<Object>) m(s, "getQuests");
            String id = (String) m(s, "getId");
            boolean begun = false;
            for (int i = 0; i < qs.size(); i++) {
                String k = (String) key.invoke(null, id, m(qs.get(i), "getId"));
                if ((Boolean) m(pqd, "isQuestCompleted", k)) { begun = true; continue; }
                if (!begun && !"ACCEPTED".equals(String.valueOf(m(pqd, "getQuestStatus", k)))) break;   // (a saga not begun: not "next")
                if ("ACCEPTED".equals(String.valueOf(m(pqd, "getQuestStatus", k)))) {
                    if (i + 1 < qs.size()) out.add((String) key.invoke(null, id, m(qs.get(i + 1), "getId")));
                } else out.add(k);
                break;
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ for the tests

    /** {level, total stats, TP, free points} of the bot's saved DMZ character; null without one. */
    public static float[] snapshot(PartyBotEntity b) {
        DmzBotQuests.Data d = DmzBotQuests.open(b, false);
        if (d == null) return null;
        try {
            Object res = m(d.sd, "getResources");
            return new float[]{level(d.sd), ((Number) m(m(d.sd, "getStats"), "getTotalStats")).floatValue(), tp(res), ap(res)};
        } catch (Throwable t) { return null; }
    }

    /** DragonMineZ's own price of raising the bot's stats from its current total by n points (calculateRecursiveCost). */
    public static int costForTest(PartyBotEntity b, int n) {
        DmzBotQuests.Data d = DmzBotQuests.open(b, false);
        if (d == null) return -1;
        try { return ((Number) m(d.sd, "calculateRecursiveCost", n, m(d.sd, "getConfiguredMaxValue"))).intValue(); } catch (Throwable t) { return -1; }
    }

    /** The bot's class and the split it trains with. */
    public static String splitForTest(PartyBotEntity b) {
        DmzBotQuests.Data d = DmzBotQuests.open(b, false);
        return d == null ? "-" : cls(d.sd) + " " + Arrays.toString(split(cls(d.sd)));
    }

    /** Each stat of the bot's saved character, as "STR=.. SKP=.." */
    public static String statsForTest(PartyBotEntity b) {
        DmzBotQuests.Data d = DmzBotQuests.open(b, false);
        if (d == null) return "-";
        StringBuilder sb = new StringBuilder();
        for (String s : STATS) try { sb.append(s).append('=').append(m(d.sd, "getCurrentStatValue", s)).append(' '); } catch (Throwable t) { sb.append(s).append("=? "); }
        return sb.toString().trim();
    }

    /** The level a mission asks for. */
    public static int requiredLevelForTest(String key) { return DmzBotQuests.ready() ? requiredLevel(DmzBotQuests.quest(key)) : -1; }

    /**
     * Test setup on the bot's saved character: its stats back to {@code stats} (null: keep), TP set, free points 0,
     * missions wiped and these marked done.
     */
    public static boolean setupForTest(PartyBotEntity b, CompoundTag stats, float tp, String... done) {
        DmzBotQuests.Data d = DmzBotQuests.open(b, false);
        if (d == null || d.live) return false;
        try {
            m(d.pqd, "resetAll");
            for (String k : done) { m(d.pqd, "acceptQuest", k); m(d.pqd, "completeQuest", k); }
            CompoundTag out = DmzPerCharacter.saveData(d.sd);
            CompoundTag tag = d.tag.copy();
            tag.put("PlayerQuestData", out.getCompound("PlayerQuestData").copy());
            if (stats != null) tag.put("Stats", stats.copy());
            CompoundTag res = tag.getCompound("Resources");
            res.putFloat("TrainingPointsF", tp);
            res.putInt("PendingAttributePoints", 0);
            tag.put("Resources", res);
            MEMO.remove(b);
            return DmzPerCharacter.store(d.owner, d.key, tag);
        } catch (Throwable t) { XenoWarriors.LOGGER.warn("setupForTest", t); return false; }
    }
}
