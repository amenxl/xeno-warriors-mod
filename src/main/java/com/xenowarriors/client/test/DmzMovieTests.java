package com.xenowarriors.client.test;

import com.xenowarriors.XwConfig;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.compat.*;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.function.Consumer;

/**
 * UI test (focus "dmzmovie..."): DragonMineZ movie missions without Kami's Lookout (DmzMovieMissions), and bots
 * talking to DragonMineZ NPCs themselves (DmzBotTalk): the copy that came, the real one nearby, taking a mission from
 * its giver, and the owner still able to talk for a bot. Logged as "XW-TEST movie ...".
 */
public final class DmzMovieTests {
    private DmzMovieTests() {}

    public static boolean handles(String focus) { return focus != null && focus.startsWith("dmzmovie"); }

    private static Consumer<UiTest.Step> L;
    private static void log(String s) { UiTest.log("movie " + s); }
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

    private static Object startQuest(ServerPlayer p, String key) {
        Object r = stat("com.dragonminez.common.quest.QuestService", "startQuest", new Class<?>[]{ServerPlayer.class, String.class}, p, key);
        return r instanceof net.minecraft.network.chat.Component c ? c.getString() : r;
    }

    private static String sagaKey(int id) {
        return String.valueOf(stat("com.dragonminez.common.quest.PlayerQuestData", "sagaQuestKey", new Class<?>[]{String.class, int.class}, DmzMovieMissions.SAGA, id));
    }

    private static PartyBotEntity bot(ServerPlayer p) {
        for (PartyBotEntity b : PartyBots.bots(p)) if (b.character() == CharacterType.KAERU) return b;
        return null;
    }

    private static Object pqd(ServerPlayer p) { return call(DmzPerCharacter.live(p), "getPlayerQuestData"); }

    private static String inStructure(ServerPlayer p) {
        Object r = stat("com.dragonminez.common.quest.QuestLocationHelper", "isInStructure",
                new Class<?>[]{net.minecraft.server.level.ServerLevel.class, net.minecraft.core.BlockPos.class, String.class}, p.serverLevel(), p.blockPosition(), DmzMovieMissions.LOOKOUT);
        return String.valueOf(r);
    }

    private static int dumped, dumped2, dumped3;
    private static void dump() {
        synchronized (DmzBotQuests.LOG) { for (int i = dumped; i < DmzBotQuests.LOG.size(); i++) log("botq " + DmzBotQuests.LOG.get(i)); dumped = DmzBotQuests.LOG.size(); }
        synchronized (DmzTalkNpcs.LOG) { for (int i = dumped2; i < DmzTalkNpcs.LOG.size(); i++) log("npc " + DmzTalkNpcs.LOG.get(i)); dumped2 = DmzTalkNpcs.LOG.size(); }
        synchronized (DmzMovieMissions.LOG) { for (int i = dumped3; i < DmzMovieMissions.LOG.size(); i++) log("movies " + DmzMovieMissions.LOG.get(i)); dumped3 = DmzMovieMissions.LOG.size(); }
    }

    /** Clears hostiles and quest mobs near the player (a bot only talks when it's quiet). */
    private static void quiet(ServerPlayer p) {
        for (var m : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(64),
                m -> m instanceof net.minecraft.world.entity.monster.Enemy || m.getPersistentData().contains("dmz_quest_key"))) m.discard();
        p.serverLevel().setDayTime(6000);
    }

    /** The bot back next to the player, awake. */
    private static void botHere(ServerPlayer p, PartyBotEntity b, boolean ai) {
        b.setNoAi(!ai);
        b.moveTo(p.getX() + 2, p.getY(), p.getZ() - 2, 0, 0);
        b.getNavigation().stop();
        b.setHealth(b.getMaxHealth());
    }

    private static final String[] K = new String[2];
    private static final int[] N = new int[4];
    private static Entity realDende;

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
            quiet(p);
            K[0] = sagaKey(1);
            K[1] = "dende_lookout_vigil";
            log("setup: story mode " + DmzQuests.storyMode(true) + " at " + at + " in " + p.level().dimension().location() + " at Kami's Lookout=" + inStructure(p)
                    + " movie keys " + Arrays.toString(K) + " isMovie=" + DmzMovieMissions.isMovie(K[0]) + "/" + DmzMovieMissions.isMovie(K[1])
                    + " not movie: saiyan_saga 1=" + DmzMovieMissions.isMovie(String.valueOf(stat("com.dragonminez.common.quest.PlayerQuestData", "sagaQuestKey",
                            new Class<?>[]{String.class, int.class}, "saiyan_saga", 1))));
            for (String[] m : DmzMovieMissions.lookoutMissions()) log("lookout gate: " + m[0] + (m[1].equals("-1") ? " start requirement" : " objective #" + m[1] + " of " + m[2]));
            PartyBots.spawn(p, CharacterType.KAERU, null);
        }); }, 60);
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) { log("no bot FAIL"); return; }
            DmzBots.ensureRace(b);
            DmzBotQuests.resetForTest(b);
            botHere(p, b, false);
            log("bot " + DmzPerCharacter.botKey(b));
        }); }, 30);

        // 1. the Lookout objective of the Movies saga's first mission: off = needs the Lookout, on = met here
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            XwConfig.DMZ_MOVIES_ANYWHERE.set(false);
            call(pqd(p), "resetQuest", K[0]);
            DmzQuests.accept(p, K[0]);
            DmzPerCharacter.syncAll(p);
            log("control (moviesWithoutLookout=false): accepted " + K[0] + " here -> " + DmzQuests.progress(p, K[0], 0) + " locationMet=" + DmzQuests.locationMet(p, K[0], 0));
        }); }, 60);
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            String pr = DmzQuests.progress(p, K[0], 0);
            log("control: after 3 s " + pr + (pr.contains("objective0=0") ? " OK (the Lookout is needed without the change)" : " ?"));
            XwConfig.DMZ_MOVIES_ANYWHERE.set(true);
            log("on: locationMet=" + DmzQuests.locationMet(p, K[0], 0) + " (at Kami's Lookout=" + inStructure(p) + ")");
        }); }, 60);
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            String pr0 = DmzQuests.progress(p, K[0], 0), pr1 = DmzQuests.progress(p, K[0], 1);
            log("movie progress away from the Lookout: " + pr0 + (pr0.contains("objective0=1") ? " OK" : " FAIL") + " | next " + pr1);
            DmzTalkNpcs.update(p);
            Entity e = DmzTalkNpcs.tempFor(p, K[0]);
            log("movie: Dende came for the talk: " + (e == null ? "nobody FAIL" : e.getDisplayName().getString() + String.format(Locale.ROOT, " at %.1f blocks OK", Math.sqrt(e.distanceToSqr(p)))));
            if (e != null) {
                Vec3 d = e.position().subtract(p.position());
                p.connection.teleport(p.getX(), p.getY(), p.getZ(), (float) Math.toDegrees(Math.atan2(-d.x, d.z)), 10F);
            }
            dump();
        }); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 30);
        step("dmzmovie_dende_came", () -> mc.setScreen(null), 5);
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            Entity e = DmzTalkNpcs.tempFor(p, K[0]);
            if (e == null) return;
            net.minecraftforge.common.ForgeHooks.onInteractEntity(p, e, InteractionHand.MAIN_HAND);
            String pr = DmzQuests.progress(p, K[0], 1);
            log("movie: talked to Dende -> " + pr + (pr.contains("objective1=1") || pr.contains("completed=true") || pr.contains("COMPLETED") ? " OK" : " ?"));
        }); }, 20);
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            String pr = DmzQuests.progress(p, K[0], 1);
            log("movie mission 1 now: " + pr);
            // 1b. Dende's Lookout Vigil: its start requirement is standing at the Lookout
            Object pq = pqd(p);
            if (!String.valueOf(call(pq, "isQuestCompleted", K[0])).equals("true")) call(pq, "completeQuest", K[0]);
            XwConfig.DMZ_MOVIES_ANYWHERE.set(false);
            String off = DmzQuests.startable(p, K[1]), offWhy = DmzQuests.blocker(p, K[1]);
            XwConfig.DMZ_MOVIES_ANYWHERE.set(true);
            String on = DmzQuests.startable(p, K[1]), onWhy = DmzQuests.blocker(p, K[1]);
            log("vigil start requirement: off startable=" + off + " (" + offWhy + ") | on startable=" + on + " (" + onWhy + ")" + ("true".equals(on) ? " OK" : " FAIL"));
            dump();
        }); }, 20);

        // 2. the bot's own movie mission: Dende comes, the bot walks over and talks to him itself
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) return;
            quiet(p);
            for (Entity e : p.serverLevel().getEntities((Entity) null, p.getBoundingBox().inflate(64), x -> x.getPersistentData().getBoolean(DmzTalkNpcs.TAG))) e.discard();
            DmzBotQuests.resetForTest(b);
            DmzPerCharacter.beginView(p, b);
            DmzQuests.accept(p, K[0]);
            DmzPerCharacter.endView(p, "menu closed");
            botHere(p, b, true);
            N[0] = DmzBotQuests.botTalks;
            log("bot copy: accepted " + K[0] + " for the bot: " + DmzBotQuests.status(b, K[0], 0) + " | " + DmzBotQuests.status(b, K[0], 1));
        }); }, 60);
        for (int i = 0; i < 6; i++) {
            final int n = i;
            step(n == 2 ? "dmzmovie_bot_walks" : null, () -> server(p -> {
                PartyBotEntity b = bot(p);
                if (b == null) return;
                Entity e = DmzTalkNpcs.tempFor(p, DmzPerCharacter.botKey(b) + "|" + K[0]);
                log("bot copy t+" + (n + 1) * 2 + "s: " + DmzBotQuests.status(b, K[0], 1) + " copy=" + (e == null ? "-" : String.format(Locale.ROOT, "%.1f blocks from the bot", Math.sqrt(e.distanceToSqr(b))))
                        + " talks=" + DmzBotQuests.botTalks);
            }), 40);
        }
        step("dmzmovie_bot_talked", () -> { mc.setScreen(null); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) return;
            String st = DmzBotQuests.status(b, K[0], 1);
            log("bot copy: the bot talked to Dende itself: talks " + N[0] + " -> " + DmzBotQuests.botTalks + " " + st
                    + (DmzBotQuests.botTalks > N[0] && st.startsWith("status=SUCCESS") ? " OK" : " FAIL") + " copies left=" + DmzTalkNpcs.count(p));
            dump();
        }); }, 30);

        // 3. a real Dende within 48 blocks: no copy comes, the bot walks to him
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) return;
            quiet(p);
            realDende = DmzTalkNpcs.realForTest(p, "dende", p.getX() + 3, p.getY(), p.getZ() + 14);
            if (realDende != null) p.serverLevel().addFreshEntity(realDende);
            DmzBotQuests.resetForTest(b);
            DmzPerCharacter.beginView(p, b);
            DmzQuests.accept(p, K[0]);
            DmzPerCharacter.endView(p, "menu closed");
            botHere(p, b, true);
            N[1] = DmzBotQuests.botTalks;
            log("bot real: Dende " + (realDende == null ? "couldn't be made FAIL" : String.format(Locale.ROOT, "at %.1f blocks from the bot", Math.sqrt(realDende.distanceToSqr(b)))));
        }); }, 200);
        step("dmzmovie_bot_real", () -> { mc.setScreen(null); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) return;
            String st = DmzBotQuests.status(b, K[0], 1);
            Entity copy = DmzTalkNpcs.tempFor(p, DmzPerCharacter.botKey(b) + "|" + K[0]);
            log("bot real: talks " + N[1] + " -> " + DmzBotQuests.botTalks + " " + st + " copy came=" + (copy != null) + " bot " + (realDende == null ? "?" : String.format(Locale.ROOT, "%.1f", Math.sqrt(realDende.distanceToSqr(b))))
                    + " blocks from Dende" + (DmzBotQuests.botTalks > N[1] && st.startsWith("status=SUCCESS") ? " OK" : " FAIL"));
            dump();
        }); }, 20);

        // 4. you start a mission whose giver (Dende) stands close by: the bot walks over and takes its own from him
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) return;
            quiet(p);
            botHere(p, b, true);
            Object pq = pqd(p);
            call(pq, "resetQuest", K[1]);
            N[2] = DmzBotQuests.botTakes;
            Object r = startQuest(p, K[1]);
            log("take: player started " + K[1] + " away from the Lookout -> " + (r == null ? "started OK" : r + " FAIL") + " | " + DmzQuests.progress(p, K[1], 0));
        }); }, 10);
        step(null, () -> server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) return;
            log("take: bot " + (DmzBotQuests.takingForTest(b) == null ? "not going to anyone (active " + DmzBotQuests.active(b) + ")" : "on its way: " + DmzBotQuests.takingForTest(b)));
        }), 200);
        step("dmzmovie_bot_took", () -> { mc.setScreen(null); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) return;
            List<String> act = DmzBotQuests.active(b);
            String st = DmzBotQuests.status(b, K[1], 0);                             // (it may have done it and handed it in to Dende already)
            boolean has = act.contains(K[1]) || st.startsWith("status=SUCCESS");
            log("take: takes " + N[2] + " -> " + DmzBotQuests.botTakes + " bot active " + act + " " + st + (DmzBotQuests.botTakes > N[2] && has ? " OK" : has ? " (started without walking)" : " FAIL"));
            dump();
        }); }, 20);

        // 5. the owner can still talk for the bot (bot standing still)
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) return;
            if (realDende != null) realDende.discard();
            quiet(p);
            DmzBotQuests.resetForTest(b);
            botHere(p, b, false);
            DmzPerCharacter.beginView(p, b);
            DmzQuests.accept(p, K[0]);
            DmzPerCharacter.endView(p, "menu closed");
            N[3] = DmzBotQuests.botTalks;
        }); }, 60);
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) return;
            DmzTalkNpcs.update(p);
            Entity e = DmzTalkNpcs.tempFor(p, DmzPerCharacter.botKey(b) + "|" + K[0]);
            log("owner path: bot " + DmzBotQuests.status(b, K[0], 1) + " copy came=" + (e != null));
            if (e == null) return;
            net.minecraftforge.common.ForgeHooks.onInteractEntity(p, e, InteractionHand.MAIN_HAND);
            String st = DmzBotQuests.status(b, K[0], 1);
            DmzTalkNpcs.update(p);
            log("owner path: after the owner talked: " + st + " copy gone=" + e.isRemoved() + " bot talks unchanged=" + (DmzBotQuests.botTalks == N[3])
                    + (st.startsWith("status=SUCCESS") ? " OK" : " FAIL"));
            botHere(p, b, true);
            dump();
        }); }, 20);
        step(null, () -> server(p -> { dump(); log("done"); }), 20);
    }
}
