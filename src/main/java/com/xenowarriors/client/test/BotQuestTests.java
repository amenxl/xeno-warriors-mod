package com.xenowarriors.client.test;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.compat.DmzBotQuests;
import com.xenowarriors.compat.DmzBots;
import com.xenowarriors.compat.DmzPerCharacter;
import com.xenowarriors.compat.DmzQuests;
import com.xenowarriors.compat.DmzTalkNpcs;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.function.Consumer;

/**
 * UI test (focus "dmzbotq"): bots' own DragonMineZ missions (DmzBotQuests) and talk-to NPCs coming to you (DmzTalkNpcs).
 * Logged as "XW-TEST botq ...".
 */
public final class BotQuestTests {
    private BotQuestTests() {}

    public static boolean handles(String focus) { return focus != null && focus.startsWith("dmzbotq"); }

    private static Consumer<UiTest.Step> L;
    private static void log(String s) { UiTest.log("botq " + s); }
    private static void server(Consumer<ServerPlayer> f) { UiTest.server(f); }
    private static void step(String shot, Runnable a, int delay) { L.accept(new UiTest.Step(shot, a, delay)); }

    private static Object call(Object o, String m, Object... args) {
        if (o == null) return null;
        try {
            for (var mm : o.getClass().getMethods()) if (mm.getName().equals(m) && mm.getParameterCount() == args.length) return mm.invoke(o, args);
        } catch (Throwable t) { return "?" + t.getClass().getSimpleName(); }
        return "?nomethod";
    }

    private static Object stat(String cls, String m, Class<?>[] types, Object... args) {
        try { return Class.forName(cls).getMethod(m, types).invoke(null, args); } catch (Throwable t) { return "?" + t; }
    }

    private static final String QS = "com.dragonminez.common.quest.QuestService";

    /** QuestService.startQuest - what DragonMineZ's "start mission" button does. Null = started. */
    private static Object startQuest(ServerPlayer p, String key) {
        Object r = stat(QS, "startQuest", new Class<?>[]{ServerPlayer.class, String.class}, p, key);
        return r instanceof net.minecraft.network.chat.Component c ? c.getString() : r;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> allQuests() {
        Object r = stat("com.dragonminez.common.quest.QuestRegistry", "getAllQuests", new Class<?>[0]);
        return r instanceof Map<?, ?> m ? new TreeMap<>((Map<String, Object>) m) : Map.of();
    }

    /** Missions made only of kill objectives (QUEST-summoned, or NATURAL counting any of the kind), no hand-in. QUEST ones first. */
    private static List<String> killQuests() {
        List<String> quest = new ArrayList<>(), natural = new ArrayList<>();
        for (var e : allQuests().entrySet()) {
            Object q = e.getValue();
            String turnIn = (String) call(q, "getTurnIn");
            if (turnIn != null && !turnIn.isBlank()) continue;
            List<?> objs = (List<?>) call(q, "getObjectives");
            if (objs == null || objs.isEmpty()) continue;
            boolean ok = true, allQuest = true;
            for (Object o : objs) {
                if (!o.getClass().getSimpleName().equals("KillObjective") || Boolean.TRUE.equals(call(o, "isTag")) || call(o, "resolveEntityType") == null) { ok = false; break; }
                boolean qm = "QUEST".equals(String.valueOf(call(o, "getSpawnMode")));
                if (!qm && !"ANY_MATCHING".equals(String.valueOf(call(o, "getCountMode")))) { ok = false; break; }
                allQuest &= qm;
            }
            if (ok) (allQuest ? quest : natural).add(e.getKey());
        }
        quest.addAll(natural);
        return quest;
    }

    /** Starts the first of these that DragonMineZ lets this player start now (on whatever data is live). */
    private static String startFirst(ServerPlayer p, List<String> keys, Set<String> skip) {
        int tried = 0;
        for (String k : keys) {
            if (skip.contains(k)) continue;
            Object r = startQuest(p, k);
            tried++;
            if (r == null) { log("started " + k + " (after " + tried + " tries)"); return k; }
        }
        log("no startable kill mission among " + keys.size() + " (" + tried + " tried)");
        return null;
    }

    private static PartyBotEntity bot(ServerPlayer p) {
        for (PartyBotEntity b : PartyBots.bots(p)) if (b.character() == CharacterType.KAERU) return b;
        return null;
    }

    private static Object pqd(ServerPlayer p) { return call(DmzPerCharacter.live(p), "getPlayerQuestData"); }

    private static String own(ServerPlayer p, String key) { return DmzQuests.progress(p, key, 0); }

    /** The targets DragonMineZ summoned for this mission near the player, or one of the kind made fresh (NATURAL). */
    private static LivingEntity target(ServerPlayer p, String key) { return target(p, key, null); }

    private static LivingEntity target(ServerPlayer p, String key, PartyBotEntity b) {
        int want = b == null ? -1 : DmzBotQuests.openObjective(b, key);
        for (Entity e : p.serverLevel().getEntities((Entity) null, p.getBoundingBox().inflate(48), x -> key.equals(x.getPersistentData().getString("dmz_quest_key"))))
            if (e instanceof LivingEntity le && le.isAlive()) return le;
        Object q = allQuests().get(key);
        List<?> objs = (List<?>) call(q, "getObjectives");
        if (objs == null) return null;
        for (int i = 0; i < objs.size(); i++) {
            Object o = objs.get(i);
            if (want >= 0 && i != want) continue;
            if ("QUEST".equals(String.valueOf(call(o, "getSpawnMode")))) continue;
            EntityType<?> t = (EntityType<?>) call(o, "resolveEntityType");
            Entity e = t == null ? null : t.create(p.serverLevel());
            if (e instanceof LivingEntity le) {
                le.moveTo(p.getX() + 3, p.getY(), p.getZ() + 3, 0, 0);
                p.serverLevel().addFreshEntity(le);
                return le;
            }
        }
        return null;
    }

    private static void kill(LivingEntity le, net.minecraft.world.damagesource.DamageSource src) {
        for (int n = 0; n < 20 && le.isAlive(); n++) { le.invulnerableTime = 0; le.setHealth(1F); le.hurt(src, 1.0E6F); }
    }

    /** Defeats targets of the bot's mission (by the bot, or by the player) until it's done (or 30 kills). */
    private static void defeat(ServerPlayer p, PartyBotEntity b, String key, boolean byBot, String what) {
        int kills = 0;
        while (kills < 60 && !DmzBotQuests.status(b, key, 0).startsWith("status=SUCCESS")) {
            LivingEntity le = target(p, key, b);
            if (le == null) { log(what + ": no target"); break; }
            if (le instanceof Mob m) m.setNoAi(true);
            kill(le, byBot ? b.damageSources().mobAttack(b) : p.damageSources().playerAttack(p));
            kills++;
            if (kills <= 2) log(what + ": kill " + kills + " (" + ForgeRegistries.ENTITY_TYPES.getKey(le.getType()) + " tagged=" + le.getPersistentData().contains("dmz_quest_key")
                    + " dead=" + le.isDeadOrDying() + ") bot " + DmzBotQuests.status(b, key, 0) + " | owner " + own(p, key));
        }
        boolean done = DmzBotQuests.status(b, key, 0).startsWith("status=SUCCESS");
        log(what + ": after " + kills + " kills bot " + DmzBotQuests.status(b, key, 0) + " | owner " + own(p, key) + (done ? " OK" : " FAIL"));
    }

    private static final String[] Q = new String[4];
    private static final float[] TP = new float[2];
    private static int dumped, dumped2, dumped3;

    private static void dump() {
        synchronized (DmzBotQuests.LOG) { for (int i = dumped; i < DmzBotQuests.LOG.size(); i++) log("log " + DmzBotQuests.LOG.get(i)); dumped = DmzBotQuests.LOG.size(); }
        synchronized (DmzTalkNpcs.LOG) { for (int i = dumped2; i < DmzTalkNpcs.LOG.size(); i++) log("npclog " + DmzTalkNpcs.LOG.get(i)); dumped2 = DmzTalkNpcs.LOG.size(); }
        synchronized (DmzBots.LOG) { for (int i = dumped3; i < DmzBots.LOG.size(); i++) log("botlog " + DmzBots.LOG.get(i)); dumped3 = DmzBots.LOG.size(); }
    }

    public static void build(Minecraft mc, Consumer<UiTest.Step> l, String focus) {
        L = l;
        step(null, () -> { mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE); mc.getToasts().clear(); mc.setScreen(null);
            UiTest.character(CharacterType.MIKIKO); }, 60);
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            Object d = DmzPerCharacter.live(p);
            call(call(d, "getCharacter"), "setRace", "saiyan");
            DmzPerCharacter.markCreatedForTest(p);
            Vec3 at = com.xenowarriors.debug.SkillTestRunner.arena(p);
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60 * 30, 10, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 60 * 30, 5, false, false));
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            log("story mode " + DmzQuests.storyMode(true) + " listening=" + DmzBotQuests.listeningForTest() + " at " + at);
            PartyBots.spawn(p, CharacterType.KAERU, null);
        }); }, 60);
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) { log("no bot FAIL"); return; }
            DmzBots.ensureRace(b);
            DmzBotQuests.resetForTest(b);
            b.setNoAi(true);
            log("bot " + DmzPerCharacter.botKey(b) + " data " + DmzBotQuests.status(b, "x", 0) + " owner live=" + DmzPerCharacter.isLive(p, DmzPerCharacter.botKey(b)));
        }); }, 30);

        // 1. a mission started from the bot's Dragon Ball menu: the bot kills the target -> the bot's mission completes
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) return;
            boolean open = DmzPerCharacter.beginView(p, b);
            Q[0] = startFirst(p, killQuests(), Set.of());
            DmzPerCharacter.endView(p, "menu closed");
            log("menu start: view=" + open + " mission " + Q[0] + " -> bot " + (Q[0] == null ? "-" : DmzBotQuests.status(b, Q[0], 0)) + " | owner " + (Q[0] == null ? "-" : own(p, Q[0])));
            TP[0] = DmzBots.tp(b);
        }); }, 40);
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null || Q[0] == null) return;
            defeat(p, b, Q[0], true, "bot kills");
            String o = own(p, Q[0]);
            log("bot kills: owner's quest unaffected = " + o.startsWith("accepted=false completed=false") + " (" + o + ") TP " + TP[0] + " -> " + DmzBots.tp(b));
            dump();
        }); }, 30);

        // 2. another one from the menu, the player defeats the targets for the bot
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) return;
            DmzPerCharacter.beginView(p, b);
            Q[1] = startFirst(p, killQuests(), Q[0] == null ? Set.of() : Set.of(Q[0]));
            DmzPerCharacter.endView(p, "menu closed");
            log("menu start 2: mission " + Q[1] + " -> bot " + (Q[1] == null ? "-" : DmzBotQuests.status(b, Q[1], 0)));
        }); }, 40);
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null || Q[1] == null) return;
            defeat(p, b, Q[1], false, "player kills");
            log("player kills: owner's quest unaffected = " + own(p, Q[1]).startsWith("accepted=false completed=false") + " (" + own(p, Q[1]) + ")");
            CompoundTagHolder.check(p, b, Q[0], Q[1]);
            dump();
        }); }, 30);

        // 3. the player starts a mission -> the bot starts one too (chat line), and both progress on the same targets
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            Set<String> skip = new HashSet<>();
            for (var m : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(64), m -> m.getPersistentData().contains("dmz_quest_key"))) m.discard();
            int before = DmzBotQuests.autoStarts;
            Q[2] = startFirst(p, killQuests(), skip);
            log("player start: " + Q[2] + " owner " + (Q[2] == null ? "-" : own(p, Q[2])) + " bot active before follow " + (b == null ? "-" : DmzBotQuests.active(b)) + " autoStarts " + before);
        }); }, 20);
        step(null, () -> mc.setScreen(null), 3);
        step("dmzbotq_chat", () -> { mc.setScreen(null); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null || Q[2] == null) return;
            List<String> act = DmzBotQuests.active(b);
            log("player start: bot active now " + act + " autoStarts " + DmzBotQuests.autoStarts + (act.isEmpty() ? " FAIL" : " OK") + " same=" + act.contains(Q[2]));
            if (act.contains(Q[2])) {
                LivingEntity le = target(p, Q[2]);
                if (le != null) {
                    String b0 = DmzBotQuests.status(b, Q[2], 0), o0 = own(p, Q[2]);
                    if (le instanceof Mob m) m.setNoAi(true);
                    kill(le, p.damageSources().playerAttack(p));
                    log("same mission: one kill by the player: bot " + b0 + " -> " + DmzBotQuests.status(b, Q[2], 0) + " | owner " + o0 + " -> " + own(p, Q[2]));
                }
            }
            dump();
        }); }, 40);
        // 3c. the bot can do yours: it takes the same one, and one kill counts for both
        step(null, () -> server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null || Q[2] == null) return;
            for (var m : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(64), m -> m.getPersistentData().contains("dmz_quest_key"))) m.discard();
            DmzBotQuests.resetForTest(b);
            call(pqd(p), "resetQuest", Q[2]);
            Object r = startQuest(p, Q[2]);
            log("same mission: player restarted " + Q[2] + " -> " + (r == null ? "started" : r));
        }), 20);
        step(null, () -> server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null || Q[2] == null) return;
            List<String> act = DmzBotQuests.active(b);
            log("same mission: bot active " + act + (act.contains(Q[2]) ? " OK" : " FAIL"));
            LivingEntity le = target(p, Q[2]);
            if (le == null || !act.contains(Q[2])) { log("same mission: no target"); return; }
            String b0 = DmzBotQuests.status(b, Q[2], 0), o0 = own(p, Q[2]);
            if (le instanceof Mob m) m.setNoAi(true);
            kill(le, p.damageSources().playerAttack(p));
            String b1 = DmzBotQuests.status(b, Q[2], 0), o1 = own(p, Q[2]);
            log("same mission: one kill by the player: bot " + b0 + " -> " + b1 + " | owner " + o0 + " -> " + o1 + (!b0.equals(b1) && !o0.equals(o1) ? " OK" : " FAIL"));
            dump();
        }), 20);
        // 3b. no starts without the player: nothing new for a while
        step(null, () -> server(p -> { Q[3] = String.valueOf(DmzBotQuests.autoStarts); }), 5);
        step(null, () -> server(p -> log("no start without the player: autoStarts " + Q[3] + " -> " + DmzBotQuests.autoStarts + (Q[3].equals(String.valueOf(DmzBotQuests.autoStarts)) ? " OK" : " FAIL"))), 100);

        talk(mc);
        step(null, () -> server(p -> { dump(); log("done"); }), 20);
    }

    /** Reads the bot's mission statuses back from its saved character (what a relog / re-add loads). */
    private static final class CompoundTagHolder {
        static void check(ServerPlayer p, PartyBotEntity b, String... keys) {
            var tag = DmzPerCharacter.saved(p, DmzPerCharacter.botKey(b));
            if (tag == null) { log("saved: none FAIL"); return; }
            try {
                Object q = Class.forName("com.dragonminez.common.quest.PlayerQuestData").getConstructor().newInstance();
                call(q, "deserializeNBT", tag.getCompound("PlayerQuestData").copy());
                StringBuilder sb = new StringBuilder();
                for (String k : keys) if (k != null) sb.append(k).append('=').append(call(q, "getQuestStatus", k)).append(' ');
                log("saved with its character (relog / re-add): " + sb + " TP " + tag.getCompound("Resources").getFloat("TrainingPointsF"));
            } catch (Throwable t) { log("saved: read failed " + t); }
        }
    }

    // ---------------------------------------------------------------- talk-to missions: the person comes to you

    /** {key, npc, "turnin"/"talk"} of a mission ending in a talk (or hand-in). */
    private static String[] talkQuest(boolean turnIn, Set<String> skip) {
        for (var e : allQuests().entrySet()) {
            if (skip.contains(e.getKey())) continue;
            Object q = e.getValue();
            String t = (String) call(q, "getTurnIn");
            boolean hasTurnIn = t != null && !t.isBlank();
            if (turnIn && hasTurnIn) return new String[]{e.getKey(), t, "turnin"};
            if (turnIn || hasTurnIn) continue;
            List<?> objs = (List<?>) call(q, "getObjectives");
            if (objs == null || objs.isEmpty()) continue;
            Object last = objs.get(objs.size() - 1);
            if (last.getClass().getSimpleName().equals("TalkToObjective")) return new String[]{e.getKey(), String.valueOf(call(last, "getNpcId")), "talk"};
        }
        return null;
    }

    /** Accepts it on the live data and finishes everything but the talk. */
    private static void readyToTalk(ServerPlayer p, String key) {
        DmzQuests.accept(p, key);
        Object pq = pqd(p);
        Object q = allQuests().get(key);
        List<?> objs = (List<?>) call(q, "getObjectives");
        for (int i = 0; i < objs.size(); i++) {
            if (objs.get(i).getClass().getSimpleName().equals("TalkToObjective")) continue;
            call(pq, "setObjectiveProgress", key, i, call(q, "getObjectiveRequired", pq, key, i));
        }
        DmzPerCharacter.syncAll(p);
    }

    private static void talkTo(ServerPlayer p, Entity npc, String[] tq) {
        boolean cancelled = net.minecraftforge.common.ForgeHooks.onInteractEntity(p, npc, InteractionHand.MAIN_HAND) != null;   // (what a right-click posts)
        if (tq[2].equals("turnin"))                                                    // (the dialogue's hand-in button)
            log("talk: hand-in -> " + stat(QS, "turnInQuest", new Class<?>[]{ServerPlayer.class, String.class, String.class}, p, tq[0], tq[1]));
        log("talk: right-click on " + npc.getDisplayName().getString() + " posted (cancelled=" + cancelled + ")");
    }

    private static void talk(Minecraft mc) {
        final String[][] tq = new String[2][];
        final int[] npcId = {-1};
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            for (var m : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(64), m -> m instanceof net.minecraft.world.entity.monster.Enemy || m.getPersistentData().contains("dmz_quest_key"))) m.discard();
            tq[0] = talkQuest(false, Set.of());
            tq[1] = talkQuest(true, Set.of());
            log("talk missions: talk=" + Arrays.toString(tq[0]) + " handin=" + Arrays.toString(tq[1]));
            String[] t = tq[0] != null ? tq[0] : tq[1];
            if (t == null) return;
            readyToTalk(p, t[0]);
            DmzTalkNpcs.update(p);
            Entity e = DmzTalkNpcs.tempFor(p, t[0]);
            log("talk: " + t[0] + " owner " + own(p, t[0]) + " -> came: " + (e == null ? "nobody FAIL" : e.getDisplayName().getString() + " (" + ForgeRegistries.ENTITY_TYPES.getKey(e.getType()) + ") at "
                    + String.format(Locale.ROOT, "%.1f", Math.sqrt(e.distanceToSqr(p))) + " blocks, temp=" + e.getPersistentData().getBoolean(DmzTalkNpcs.TAG) + " OK"));
            if (e != null) {
                npcId[0] = e.getId();
                Vec3 d = e.position().subtract(p.position());
                p.teleportTo(p.getX(), p.getY(), p.getZ());
                p.setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z))); p.setXRot(10); p.setYHeadRot(p.getYRot());
            }
            DmzTalkNpcs.update(p);
            log("talk: no duplicate on the next check: copies=" + DmzTalkNpcs.count(p));
        }); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 40);
        step(null, () -> mc.setScreen(null), 10);
        step("dmzbotq_npc", () -> mc.setScreen(null), 3);
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            String[] t = tq[0] != null ? tq[0] : tq[1];
            if (t == null) return;
            Entity e = DmzTalkNpcs.tempFor(p, t[0]);
            if (e == null) return;
            talkTo(p, e, t);
            log("talk: after talking " + own(p, t[0]));
            DmzTalkNpcs.update(p);
            log("talk: copy gone=" + e.isRemoved() + " copies=" + DmzTalkNpcs.count(p) + (e.isRemoved() && String.valueOf(own(p, t[0])).contains("completed=true") ? " OK" : " FAIL"));
        }); }, 20);
        step("dmzbotq_npc_gone", () -> mc.setScreen(null), 10);
        // the same for the bot's own mission: the person comes to the owner; talking to them hands it in for the bot
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            String[] t = tq[1] != null ? tq[1] : tq[0];
            if (b == null || t == null) return;
            if (t == tq[0]) { String[] o = talkQuest(false, Set.of(t[0])); if (o != null) t = tq[1] = o; }
            DmzPerCharacter.beginView(p, b);
            readyToTalk(p, t[0]);
            DmzPerCharacter.endView(p, "menu closed");
            DmzTalkNpcs.update(p);
            String id = DmzPerCharacter.botKey(b) + "|" + t[0];
            Entity e = DmzTalkNpcs.tempFor(p, id);
            log("bot talk: " + t[0] + " (" + t[2] + " " + t[1] + ") bot " + DmzBotQuests.status(b, t[0], 0) + " -> came: " + (e == null ? "nobody FAIL" : e.getDisplayName().getString() + " OK"));
            if (e == null) return;
            net.minecraftforge.common.ForgeHooks.onInteractEntity(p, e, InteractionHand.MAIN_HAND);
            log("bot talk: after talking bot " + DmzBotQuests.status(b, t[0], 0) + " | owner " + own(p, t[0]));
            DmzTalkNpcs.update(p);
            log("bot talk: copy gone=" + e.isRemoved() + (e.isRemoved() && DmzBotQuests.status(b, t[0], 0).startsWith("status=SUCCESS") ? " OK" : " FAIL"));
            // a mission dropped some other way: its copy leaves too
            String[] o = talkQuest(false, Set.of(t[0], tq[0] == null ? "" : tq[0][0]));
            if (o == null) return;
            readyToTalk(p, o[0]);
            DmzTalkNpcs.update(p);
            Entity e2 = DmzTalkNpcs.tempFor(p, o[0]);
            call(pqd(p), "resetQuest", o[0]);
            DmzTalkNpcs.update(p);
            log("dropped mission " + o[0] + ": came=" + (e2 != null) + " gone after dropping=" + (e2 != null && e2.isRemoved()));
        }); }, 20);
    }
}
