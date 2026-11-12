package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.util.*;

/**
 * With DragonMineZ: bot teammates play DragonMineZ's training minigames (Rhythm, Control, Memory, Precision, Gravity)
 * for TP when they aren't fighting.
 * <ul>
 *   <li>About every {@link #GAP} ticks, out of a fight, a bot picks one of the games it can play - ones its own
 *       character knows, ones you know (you show it), or ones DragonMineZ unlocks for everyone - and plays it for a
 *       while (a few levels; better at it the higher its DragonMineZ level). Little sparks over its head while it plays.</li>
 *   <li>It's paid exactly as DragonMineZ pays a player for the levels cleared (TrainingRewardC2S: TP per level from
 *       {@code computeTpsPerLevel} for its total stats, capped by the game's TP limit), into its own character.</li>
 *   <li>A fight, going down or a Journey stops the game: no TP for an unfinished game.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DmzBotMinigames {
    private DmzBotMinigames() {}

    public static final String[] GAMES = {"rhythm", "control", "memory", "precision", "gravity"};
    /** Ticks between two games a bot starts by itself (3 minutes); ticks a cleared level takes it. */
    public static final int GAP = 20 * 60 * 3, TICKS_PER_LEVEL = 20 * 6;
    private static final String LAST = "xw_dmz_minigame_at";

    private static Boolean loaded;
    private static boolean broken;
    private static Method trainingConfig, getSettings, perLevel, limit, multiplier, byDefault;

    private record Game(String id, int levels, long ends) {}
    private static final Map<UUID, Game> PLAYING = new HashMap<>();

    /** (Tests) what happened; games finished; TP earned. */
    public static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());
    public static int played;
    public static double earned;

    private static void note(String s) {
        if (LOG.size() > 60) LOG.remove(0);
        LOG.add(s);
        XenoWarriors.LOGGER.info("XW dmz bot minigames: {}", s);
    }

    private static Object m(Object o, String name, Object... a) throws Exception { return DmzBotQuests.m(o, name, a); }

    static boolean ready() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("dragonminez");
        if (!loaded || broken) return false;
        if (getSettings != null) return true;
        try {
            trainingConfig = Class.forName("com.dragonminez.common.config.ConfigManager").getMethod("getTrainingConfig");
            Class<?> tc = trainingConfig.getReturnType();
            getSettings = tc.getMethod("getSettings", String.class);
            Class<?> ms = getSettings.getReturnType();
            perLevel = tc.getMethod("computeTpsPerLevel", int.class, ms);
            limit = ms.getMethod("getTpsLimitPerGame");
            multiplier = ms.getMethod("getRewardMultiplier");
            byDefault = ms.getMethod("isUnlockedByDefault");
            return true;
        } catch (Throwable t) {
            broken = true; getSettings = null;
            XenoWarriors.LOGGER.warn("Bots won't play DragonMineZ's minigames (its API changed?)", t);
            return false;
        }
    }

    private static String pretty(String g) { return Character.toUpperCase(g.charAt(0)) + g.substring(1); }

    /** The games this bot can play: its own known ones, its owner's, and those DragonMineZ unlocks for everyone. */
    @SuppressWarnings("unchecked")
    static List<String> playable(DmzBotQuests.Data d) {
        List<String> out = new ArrayList<>();
        try {
            Object cfg = trainingConfig.invoke(null);
            Set<String> known = new HashSet<>();
            try { known.addAll((Set<String>) m(m(d.sd, "getCharacter"), "getKnownMinigames")); } catch (Throwable ignored) {}
            try { Object own = DmzPerCharacter.live(d.owner); if (own != null) known.addAll((Set<String>) m(m(own, "getCharacter"), "getKnownMinigames")); } catch (Throwable ignored) {}
            for (String g : GAMES) {
                Object s = getSettings.invoke(cfg, g);
                if (s != null && (known.contains(g) || (Boolean) byDefault.invoke(s))) out.add(g);
            }
        } catch (Throwable ignored) {}
        return out;
    }

    /** Starts a game if it can. Returns what happened (for the tests). */
    public static String start(PartyBotEntity b, boolean force) {
        if (!ready()) return "off";
        if (PLAYING.containsKey(b.getUUID())) return "already playing";
        long now = b.level().getGameTime(), last = b.getPersistentData().getLong(LAST);
        if (!force && last > 0 && now >= last && now - last < GAP) return "too soon";
        DmzBotQuests.Data d = DmzBotQuests.open(b, false);
        if (d == null || d.live) return "no data";
        List<String> games = playable(d);
        if (games.isEmpty()) return "knows no games";
        String g = games.get(b.getRandom().nextInt(games.size()));
        int lv;
        try { lv = DmzBotTraining.level(d.sd); } catch (Throwable t) { lv = 1; }
        int levels = 2 + b.getRandom().nextInt(3) + Math.min(6, lv / 15);       // (better at it as it grows)
        PLAYING.put(b.getUUID(), new Game(g, levels, now + (long) levels * TICKS_PER_LEVEL));
        b.getPersistentData().putLong(LAST, now);
        note(d.key + " started " + g + " (" + levels + " levels)");
        return "playing " + g + " for " + levels + " levels";
    }

    /** The game's over: pays it DragonMineZ's reward for the levels cleared. */
    private static String finish(PartyBotEntity b, Game game) {
        DmzBotQuests.Data d = DmzBotQuests.open(b, false);
        if (d == null || d.live) return "no data";
        try {
            Object s = getSettings.invoke(trainingConfig.invoke(null), game.id);
            int total = ((Number) m(m(d.sd, "getStats"), "getTotalStats")).intValue();
            int cost = ((Number) m(d.sd, "getSingleStatCost", total)).intValue();
            float per = ((Number) perLevel.invoke(trainingConfig.invoke(null), cost, s)).floatValue();
            float tp = (float) (per * game.levels * DmzBotWeights.multiplier(b));   // (training weights it wears)
            float cap = ((Number) limit.invoke(s)).floatValue();
            if (cap > 0 && tp > cap) tp = cap;
            if (!(tp > 0)) return "no reward";
            m(m(d.sd, "getResources"), "addTrainingPoints", tp);
            Collections.addAll(d.touched, "Resources");
            d.commit();
            if (d.tag != null) DmzPerCharacter.store(d.owner, d.key, d.tag.copy());
            played++;
            earned += tp;
            note(d.key + " cleared " + game.levels + " levels of " + game.id + " for " + Math.round(tp) + " TP (" + Math.round(per) + " a level)");
            DmzNews.bot(d.owner, b, "Minigame", Component.empty().append(b.character().getDisplayName()).append(" cleared " + game.levels + " levels of ")
                    .append(Component.literal(pretty(game.id) + " training").withStyle(ChatFormatting.GOLD))
                    .append(" (+" + String.format(Locale.US, "%,d TP", Math.round(tp)) + ")").withStyle(ChatFormatting.AQUA));
            return "earned " + Math.round(tp);
        } catch (Throwable t) {
            XenoWarriors.LOGGER.warn("A bot couldn't finish a DragonMineZ minigame", t);
            return "error " + t;
        }
    }

    private static boolean busy(PartyBotEntity b) { return b.getTarget() != null || b.downed() || b.journey() || b.sparring(); }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p instanceof FakePlayer || p.tickCount % 20 != 9 || !ready()) return;
        if (DmzPerCharacter.viewing(p) != null) return;                             // (a bot's menu open: its data is yours to click)
        long now = p.level().getGameTime();
        for (PartyBotEntity b : PartyBots.bots(p)) {
            Game g = PLAYING.get(b.getUUID());
            if (g != null) {
                if (busy(b)) { PLAYING.remove(b.getUUID()); note(DmzPerCharacter.botKey(b) + " stopped " + g.id + " (a fight)"); continue; }
                ((ServerLevel) b.level()).sendParticles(ParticleTypes.ENCHANT, b.getX(), b.getY() + b.getBbHeight() + 0.4, b.getZ(), 4, 0.25, 0.15, 0.25, 0.4);
                if (now >= g.ends) { PLAYING.remove(b.getUUID()); finish(b, g); }
                continue;
            }
            if (p.tickCount % 200 == 9 && !busy(b)) start(b, false);
        }
    }

    /** DragonMineZ's training-minigame TP for one level, for this player's or bot's character (0 without one). */
    public static float perLevelTp(net.minecraft.world.entity.LivingEntity e) {
        if (!ready()) return 0;
        try {
            Object sd = e instanceof PartyBotEntity b ? DmzBotKi.statsFor(b) : e instanceof ServerPlayer p ? DmzPerCharacter.live(p) : null;
            if (sd == null) return 0;
            Object s = getSettings.invoke(trainingConfig.invoke(null), "control");
            int total = ((Number) m(m(sd, "getStats"), "getTotalStats")).intValue();
            int cost = ((Number) m(sd, "getSingleStatCost", total)).intValue();
            return ((Number) perLevel.invoke(trainingConfig.invoke(null), cost, s)).floatValue();
        } catch (Throwable t) { return 0; }
    }

    /** (Tests) finish its game now. */
    public static String finishForTest(PartyBotEntity b) {
        Game g = PLAYING.remove(b.getUUID());
        return g == null ? "not playing" : finish(b, g);
    }

    /** (Tests) teaches its saved character every game. */
    public static void learnAllForTest(PartyBotEntity b) {
        DmzBotQuests.Data d = DmzBotQuests.open(b, false);
        if (d == null) return;
        try {
            for (String g : GAMES) m(m(d.sd, "getCharacter"), "addKnownMinigame", g);
            Collections.addAll(d.touched, "Character");
            d.commit();
            if (d.tag != null) DmzPerCharacter.store(d.owner, d.key, d.tag.copy());
        } catch (Throwable t) { XenoWarriors.LOGGER.warn("learnAllForTest", t); }
    }

    public static float tpForTest(PartyBotEntity b) {
        DmzBotQuests.Data d = DmzBotQuests.open(b, false);
        try { return d == null ? -1 : ((Number) m(m(d.sd, "getResources"), "getTrainingPoints")).floatValue(); } catch (Throwable t) { return -1; }
    }
}
