package com.xenowarriors.client.test;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.compat.DmzBotQuests;
import com.xenowarriors.compat.DmzBotTraining;
import com.xenowarriors.compat.DmzBots;
import com.xenowarriors.compat.DmzPerCharacter;
import com.xenowarriors.compat.DmzQuests;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.*;
import java.util.function.Consumer;

/**
 * UI test (focus "dmzbottp", and "packbottp" in the user's real mod pack): a bot too low a DragonMineZ level for the
 * next mission spends its own TP on its stats (DmzBotTraining) - the same price DragonMineZ charges a player - and then
 * starts it; not enough TP: it spends what it can and doesn't start; nothing is spent when something else blocks the
 * mission; your own stats and TP never change. Logged as "XW-TEST bottp ..." with OK / FAIL.
 */
public final class BotTrainingTests {
    private BotTrainingTests() {}

    public static boolean handles(String focus) { return focus != null && (focus.startsWith("dmzbottp") || focus.startsWith("packbottp")); }

    private static Consumer<UiTest.Step> L;
    private static int ok, fail;
    private static void log(String s) { UiTest.log("bottp " + s); }
    private static void check(String what, boolean good, String detail) {
        if (good) ok++; else fail++;
        log(what + ": " + detail + (good ? " OK" : " FAIL"));
    }
    private static void server(Consumer<ServerPlayer> f) {
        UiTest.server(p -> { try { f.accept(p); } catch (Throwable t) { check("server step", false, String.valueOf(t)); com.xenowarriors.XenoWarriors.LOGGER.error("XW-TEST bottp", t); } });
    }
    private static void step(String shot, Runnable a, int delay) { L.accept(new UiTest.Step(shot, a, delay)); }

    private static Object call(Object o, String m, Object... args) {
        if (o == null) return null;
        try {
            for (var mm : o.getClass().getMethods()) if (mm.getName().equals(m) && mm.getParameterCount() == args.length) return mm.invoke(o, args);
        } catch (Throwable t) { return "?" + t.getClass().getSimpleName(); }
        return "?nomethod";
    }

    private static PartyBotEntity bot(ServerPlayer p) {
        for (PartyBotEntity b : PartyBots.bots(p)) if (b.character() == CharacterType.KAERU) return b;
        return null;
    }

    private static Object pqd(ServerPlayer p) { return call(DmzPerCharacter.live(p), "getPlayerQuestData"); }

    /** The player's own DragonMineZ stats and TP (what must never change here). */
    private static String mine(ServerPlayer p) {
        CompoundTag t = DmzPerCharacter.saveData(DmzPerCharacter.live(p));
        return t == null ? "-" : "stats " + t.getCompound("Stats") + " TP " + t.getCompound("Resources").getFloat("TrainingPointsF")
                + " AP " + t.getCompound("Resources").getInt("PendingAttributePoints");
    }

    private static String snap(PartyBotEntity b) {
        float[] s = DmzBotTraining.snapshot(b);
        return s == null ? "-" : "level " + (int) s[0] + " total " + (int) s[1] + " TP " + s[2];
    }

    /** The mission to test with: the first saga step (saiyan saga first) that asks for a level above 2, and the steps before it. */
    @SuppressWarnings("unchecked")
    private static String[] target() {
        try {
            Class<?> reg = Class.forName("com.dragonminez.common.quest.QuestRegistry");
            var keyOf = Class.forName("com.dragonminez.common.quest.PlayerQuestData").getMethod("sagaQuestKey", String.class, int.class);
            Map<String, Object> sagas = new TreeMap<>((Map<String, Object>) reg.getMethod("getAllSagas").invoke(null));
            List<String> ids = new ArrayList<>(sagas.keySet());
            if (ids.remove("saiyan_saga")) ids.add(0, "saiyan_saga");
            for (String id : ids) {
                List<String> before = new ArrayList<>();
                for (Object q : (List<Object>) call(sagas.get(id), "getQuests")) {
                    String k = (String) keyOf.invoke(null, call(sagas.get(id), "getId"), call(q, "getId"));
                    if (DmzBotTraining.requiredLevelForTest(k) > 2) {
                        String[] out = new String[before.size() + 1];
                        out[0] = k;
                        for (int i = 0; i < before.size(); i++) out[i + 1] = before.get(i);
                        return out;
                    }
                    before.add(k);
                }
            }
        } catch (Throwable t) { log("target: " + t); }
        return null;
    }

    private static String[] T;
    private static CompoundTag STATS0;
    private static String MINE0;
    private static final float[] A = new float[8];
    private static int dumped;

    private static void dump() {
        synchronized (DmzBotTraining.LOG) { for (int i = dumped; i < DmzBotTraining.LOG.size(); i++) log("log " + DmzBotTraining.LOG.get(i)); dumped = DmzBotTraining.LOG.size(); }
    }

    private static String[] done() { return Arrays.copyOfRange(T, 1, T.length); }

    public static void build(Minecraft mc, Consumer<UiTest.Step> l, String focus) {
        L = l;
        step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null); UiTest.character(CharacterType.MIKIKO); }, 60);
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            Object d = DmzPerCharacter.live(p);
            call(call(d, "getCharacter"), "setRace", "saiyan");
            DmzPerCharacter.markCreatedForTest(p);
            com.xenowarriors.debug.SkillTestRunner.arena(p);
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60 * 30, 10, false, false));
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            call(call(d, "getResources"), "setTrainingPoints", 77777F);              // (the player has TP of their own: it must stay theirs)
            log("story mode " + DmzQuests.storyMode(true) + " focus " + focus);
            PartyBots.spawn(p, CharacterType.KAERU, null);
        }); }, 60);
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) { check("setup", false, "no bot"); return; }
            DmzBots.ensureRace(b);
            b.setNoAi(true);
            CompoundTag tag = DmzPerCharacter.saved(p, DmzPerCharacter.botKey(b));
            STATS0 = tag == null ? null : tag.getCompound("Stats").copy();
            T = target();
            MINE0 = mine(p);
            check("setup", STATS0 != null && T != null, "bot " + DmzPerCharacter.botKey(b) + " " + snap(b) + " class/split " + DmzBotTraining.splitForTest(b)
                    + " | mission " + (T == null ? "-" : T[0] + " needs level " + DmzBotTraining.requiredLevelForTest(T[0]) + " after " + Arrays.toString(done())));
            log("player " + MINE0);
        }); }, 20);

        // 1. enough TP: the player starts the mission -> the bot trains up to its level (DragonMineZ's price) and starts it too
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null || T == null) return;
            DmzBotTraining.setupForTest(b, STATS0, 200000F, done());
            call(pqd(p), "resetQuest", T[0]);
            call(pqd(p), "acceptQuest", T[0]);                                       // (the player is on it - as DragonMineZ's start leaves them)
            float[] s0 = DmzBotTraining.snapshot(b);
            A[0] = s0[0]; A[1] = s0[1]; A[2] = s0[2];
            A[3] = b.getMaxHealth();
            int t0 = DmzBotTraining.trainings;
            List<String> started = DmzBotQuests.followStart(p, T[0]);
            float[] s1 = DmzBotTraining.snapshot(b);
            A[4] = s1[0]; A[5] = s1[1]; A[6] = s1[2];
            int need = DmzBotTraining.requiredLevelForTest(T[0]);
            List<String> act = DmzBotQuests.active(b);
            log("enough TP: before level " + (int) A[0] + " total " + (int) A[1] + " TP " + A[2] + " -> after level " + (int) A[4] + " total " + (int) A[5] + " TP " + A[6]
                    + " | stats " + DmzBotTraining.statsForTest(b) + " | started " + started + " active " + act);
            check("enough TP: trained", DmzBotTraining.trainings == t0 + 1 && A[4] >= need && A[0] < need, "level " + (int) A[0] + " -> " + (int) A[4] + " (needs " + need + ")");
            check("enough TP: started the mission", act.contains(T[0]) || started.stream().anyMatch(x -> x.contains(T[0])), String.valueOf(started));
            check("enough TP: player untouched", mine(p).equals(MINE0), mine(p));
            check("enough TP: health refreshed", b.getMaxHealth() >= A[3], A[3] + " -> " + b.getMaxHealth());
            dump();
        }); }, 10);
        step("bottp_chat", () -> mc.setScreen(null), 30);
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {           // the TP taken is DragonMineZ's own price for those points
            PartyBotEntity b = bot(p);
            if (b == null || T == null) return;
            int n = (int) (A[5] - A[1]);
            DmzBotTraining.setupForTest(b, STATS0, 200000F, done());
            int price = DmzBotTraining.costForTest(b, n), lessOne = DmzBotTraining.costForTest(b, n - 1);
            check("enough TP: TP taken = DragonMineZ's price", Math.abs((A[2] - A[6]) - price) < 0.5F,
                    "spent " + (A[2] - A[6]) + " for " + n + " points, calculateRecursiveCost(" + n + ") = " + price + " (one point fewer: " + lessOne + ")");
        }); }, 10);

        // 2. not enough TP: it spends what it can, doesn't start, no crash
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null || T == null) return;
            int n = (int) (A[5] - A[1]);
            float give = Math.max(1, DmzBotTraining.costForTest(b, Math.max(1, n / 2)) + 0.5F);  // (half the points' worth)
            DmzBotTraining.setupForTest(b, STATS0, give, done());
            float[] s0 = DmzBotTraining.snapshot(b);
            List<String> started = DmzBotQuests.followStart(p, T[0]);
            float[] s1 = DmzBotTraining.snapshot(b);
            int need = DmzBotTraining.requiredLevelForTest(T[0]);
            int next = DmzBotTraining.costForTest(b, 1);
            List<String> act = DmzBotQuests.active(b);
            log("short of TP: gave " + give + " -> level " + (int) s0[0] + " -> " + (int) s1[0] + " TP " + s0[2] + " -> " + s1[2] + " next point costs " + next + " | started " + started);
            check("short of TP: spent what it could", s1[0] < need && s1[1] > s0[1] && s1[2] < next, "level " + (int) s1[0] + "/" + need + ", " + s1[2] + " TP left < " + next);
            check("short of TP: didn't start it", !act.contains(T[0]), "active " + act);
            check("short of TP: player untouched", mine(p).equals(MINE0), mine(p));
            dump();
        }); }, 30);

        // 3. something else in the way (the step before it not done): nothing spent
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null || T == null) return;
            DmzBotTraining.setupForTest(b, STATS0, 200000F);
            float[] s0 = DmzBotTraining.snapshot(b);
            int t0 = DmzBotTraining.trainings;
            List<String> started = DmzBotQuests.followStart(p, T[0]);
            float[] s1 = DmzBotTraining.snapshot(b);
            check("not just the level: nothing spent", s1[2] == s0[2] && DmzBotTraining.trainings == t0 && !DmzBotQuests.active(b).contains(T[0]),
                    "TP " + s0[2] + " -> " + s1[2] + " level " + (int) s0[0] + " -> " + (int) s1[0] + " started " + started);
            dump();
        }); }, 30);

        // 4. every 5 s it gets ready for its next saga step by itself (only up to that level), without starting it
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null || T == null) return;
            DmzBotTraining.setupForTest(b, STATS0, 200000F, done());
            A[7] = DmzBotTraining.trainings;
            log("ready: set up " + snap(b));
        }); }, 130);                                                                // (its own look runs within 5 s)
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null || T == null) return;
            float[] s = DmzBotTraining.snapshot(b);
            int need = DmzBotTraining.requiredLevelForTest(T[0]);
            check("ready by itself: trained to the next step's level, no further", DmzBotTraining.trainings > A[7] && s[0] >= need && s[1] == A[5],
                    snap(b) + " (with the player it bought the same: total " + (int) A[5] + "; TP differs only by the passive TP it earned meanwhile)");
            check("ready by itself: didn't start anything", DmzBotQuests.active(b).isEmpty(), "active " + DmzBotQuests.active(b));
            try { log("ready: again -> " + DmzBotTraining.ready(b, p, true)); } catch (Throwable t) { check("ready again", false, String.valueOf(t)); }
            check("ready by itself: no more spent", DmzBotTraining.snapshot(b)[2] == s[2], snap(b));
            check("ready by itself: player untouched", mine(p).equals(MINE0), mine(p));
            dump();
        }); }, 10);

        step(null, () -> server(p -> {
            PartyBotEntity b = bot(p);
            if (b != null) DmzBotTraining.setupForTest(b, STATS0, 0F);
            call(pqd(p), "resetQuest", T == null ? "x" : T[0]);
            log("summary ok=" + ok + " fail=" + fail);
        }), 20);
    }
}
