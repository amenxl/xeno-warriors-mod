package com.xenowarriors.client.test;

import com.xenowarriors.XwConfig;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.compat.*;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.*;
import java.util.function.Consumer;

/**
 * UI test (focus "dmzbiome"): DragonMineZ missions without biomes (missionsIgnoreBiomes). The arena is turned into a
 * mushroom field (no mission's biome, nothing hostile spawns there), then: every biome gate listed; a mission whose
 * start needs a desert is started there (blocked with the setting off), its bandits - who don't live there - come to
 * you, you and your bot (who followed you into the mission) finish the kills, and both hand it in; and the
 * "World Explorer" mission (go to plains, desert and forest) is finished on the spot, by you and by the bot.
 * Each check logs "XW-TEST biome ... -> PASS/FAIL". Focus "botbig" runs {@link BotBigBagTests} (the bigger bot tabs).
 */
public final class DmzBiomeTests {
    private DmzBiomeTests() {}

    public static boolean handles(String focus) { return focus != null && (focus.startsWith("dmzbiome") || focus.startsWith("botbig")); }

    private static Consumer<UiTest.Step> L;
    private static int pass, fail;
    private static void log(String s) { UiTest.log("biome " + s); }
    private static void check(String what, boolean ok, String detail) {
        if (ok) pass++; else fail++;
        log(what + ": " + detail + " -> " + (ok ? "PASS" : "FAIL"));
    }
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

    private static Object pqd(ServerPlayer p) { return call(DmzPerCharacter.live(p), "getPlayerQuestData"); }

    private static Object quest(String key) { return stat("com.dragonminez.common.quest.QuestRegistry", "getQuest", new Class<?>[]{String.class}, key); }

    /** Marks the quests this one needs first as done (on whichever data the player's capability holds - a bot's while viewing it). */
    private static String satisfy(ServerPlayer p, String key) {
        Object q = quest(key), pq = pqd(p);
        List<String> done = new ArrayList<>();
        Object pre = call(q, "getPrerequisites");
        Object conds = pre == null ? null : call(pre, "conditions");
        if (conds instanceof List<?> l) for (Object c : l) {
            String type = String.valueOf(call(c, "getType"));
            if (type.equals("SAGA_QUEST")) {
                String saga = String.valueOf(call(c, "getSagaId"));
                int n = ((Number) call(c, "getQuestId")).intValue();
                for (int i = 1; i <= n; i++) {
                    Object k = stat("com.dragonminez.common.quest.PlayerQuestData", "sagaQuestKey", new Class<?>[]{String.class, int.class}, saga, i);
                    call(pq, "completeQuest", k);
                }
                done.add(saga + " 1-" + n);
            } else if (type.equals("QUEST")) {
                Object k = call(c, "getRequiredQuestId");
                call(pq, "completeQuest", k);
                done.add(String.valueOf(k));
            } else done.add("(" + type + " left as it is)");
        }
        return done.toString();
    }

    private static String biomeHere(ServerPlayer p) {
        return p.serverLevel().getBiome(p.blockPosition()).unwrapKey().map(k -> k.location().toString()).orElse("?");
    }

    private static PartyBotEntity bot(ServerPlayer p) {
        for (PartyBotEntity b : PartyBots.bots(p)) if (b.character() == CharacterType.KAERU) return b;
        return null;
    }

    private static void quiet(ServerPlayer p) {
        for (var m : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(64),
                m -> m instanceof net.minecraft.world.entity.monster.Enemy || m.getPersistentData().contains("dmz_quest_key"))) m.discard();
        p.serverLevel().setDayTime(6000);
    }

    private static void botHere(ServerPlayer p, PartyBotEntity b, boolean ai) {
        b.setNoAi(!ai);
        b.moveTo(p.getX() + 2, p.getY(), p.getZ() - 2, 0, 0);
        b.getNavigation().stop();
        b.setHealth(b.getMaxHealth());
    }

    /** Defeats (as the player) everything summoned for this mission nearby. */
    private static int defeat(ServerPlayer p, String key) {
        int n = 0;
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(80),
                x -> x.isAlive() && key.equals(x.getPersistentData().getString("dmz_quest_key")))) {
            e.invulnerableTime = 0;
            e.setHealth(1F);
            e.hurt(p.damageSources().playerAttack(p), 1000F);
            if (!e.isAlive()) n++;
        }
        return n;
    }

    private static int dumped, dumped2;
    private static void dump() {
        synchronized (DmzQuestHelp.LOG) { for (int i = dumped; i < DmzQuestHelp.LOG.size(); i++) log("help " + DmzQuestHelp.LOG.get(i)); dumped = DmzQuestHelp.LOG.size(); }
        synchronized (DmzBotQuests.LOG) { for (int i = dumped2; i < DmzBotQuests.LOG.size(); i++) log("botq " + DmzBotQuests.LOG.get(i)); dumped2 = DmzBotQuests.LOG.size(); }
    }

    private static final String Q = "yamcha_desert_bandit", EXPLORE = "world_explorer";
    private static final int[] N = new int[4];

    public static void build(Minecraft mc, Consumer<UiTest.Step> l, String focus) {
        L = l;
        if (focus.startsWith("botbig")) { BotBigBagTests.build(mc, l); return; }
        step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null); UiTest.character(CharacterType.MIKIKO); }, 60);
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            Object d = DmzPerCharacter.live(p);
            call(call(d, "getCharacter"), "setRace", "saiyan");
            DmzPerCharacter.markCreatedForTest(p);
            var at = com.xenowarriors.debug.SkillTestRunner.arena(p);
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60 * 30, 10, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 60 * 30, 5, false, false));
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            quiet(p);
            String was = biomeHere(p);
            int r = p.server.getCommands().performPrefixedCommand(p.server.createCommandSourceStack().withPosition(p.position()).withLevel(p.serverLevel()),
                    "fillbiome ~-48 ~-24 ~-48 ~48 ~24 ~48 minecraft:mushroom_fields");
            log("setup: story mode " + DmzQuests.storyMode(true) + " at " + at + " in " + p.level().dimension().location() + " biome " + was + " -> " + biomeHere(p) + " (fillbiome " + r + ")"
                    + " missionsIgnoreBiomes=" + XwConfig.get(XwConfig.DMZ_MISSIONS_IGNORE_BIOMES));
            DmzQuestHelp.BIOME_RESUMMON_TICKS = 40;
            PartyBots.spawn(p, CharacterType.KAERU, null);
        }); }, 60);
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) { check("bot", false, "no bot"); return; }
            DmzBots.ensureRace(b);
            DmzBotQuests.resetForTest(b);
            botHere(p, b, false);
            List<String[]> gates = DmzQuests.biomeGates();
            for (String[] g : gates) log("gate: " + g[0] + " " + g[1] + " biome " + g[2]);
            check("biome gates found", gates.size() > 0 && gates.stream().anyMatch(g -> g[0].equals(Q)) && gates.stream().anyMatch(g -> g[0].equals(EXPLORE)),
                    gates.size() + " gates (" + gates.stream().filter(g -> g[1].equals("start")).count() + " start requirements, "
                            + gates.stream().filter(g -> g[1].startsWith("objective")).count() + " objectives) in " + biomeHere(p));
            // prerequisites (an earlier saga mission) done, for you and the bot
            log("prerequisites for you: " + satisfy(p, Q));
            if (DmzPerCharacter.beginView(p, b)) { log("prerequisites for the bot: " + satisfy(p, Q)); DmzPerCharacter.endView(p, "menu closed"); }
        }); }, 30);

        // 1. the biome test itself, and a "be in the desert" start requirement: off = blocked here, on = fine
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            XwConfig.DMZ_MISSIONS_IGNORE_BIOMES.set(false);
            String mOff = DmzQuests.matchesBiome(p, "minecraft:desert"), oOff = DmzQuests.biomeObjectiveMet(p, "minecraft:desert");
            String sOff = DmzQuests.startable(p, Q), whyOff = DmzQuests.blocker(p, Q), bOff = b == null ? "-" : DmzBotQuests.eligibleForTest(b, Q);
            XwConfig.DMZ_MISSIONS_IGNORE_BIOMES.set(true);
            String mOn = DmzQuests.matchesBiome(p, "minecraft:desert"), oOn = DmzQuests.biomeObjectiveMet(p, "minecraft:desert");
            String sOn = DmzQuests.startable(p, Q), whyOn = DmzQuests.blocker(p, Q), bOn = b == null ? "-" : DmzBotQuests.eligibleForTest(b, Q);
            check("control: off, the desert is needed", "false".equals(mOff) && "false".equals(oOff) && "false".equals(sOff),
                    "matchesBiome=" + mOff + " biome objective met=" + oOff + " startable=" + sOff + " (" + whyOff + ") bot eligible=" + bOff);
            check("on: desert counted as met in a mushroom field", "true".equals(mOn) && "true".equals(oOn), "matchesBiome=" + mOn + " biome objective met=" + oOn);
            check("on: the desert mission can be started here", "true".equals(sOn) && "nothing".equals(whyOn), "startable=" + sOn + " blocker=" + whyOn);
            check("on: the bot can start it too", "true".equals(bOn), "bot eligible=" + bOn);
            call(pqd(p), "resetQuest", Q);
            botHere(p, b, true);
            Object r = startQuest(p, Q);
            check("started away from the desert", r == null && String.valueOf(DmzQuests.progress(p, Q, 0)).contains("accepted=true"), "startQuest -> " + (r == null ? "started" : r) + " | " + DmzQuests.progress(p, Q, 0));
            N[0] = DmzQuests.biomeWaivedCount();
        }); }, 40);
        step(null, () -> server(p -> {
            PartyBotEntity b = bot(p);
            check("the bot followed you into it", b != null && DmzBotQuests.active(b).contains(Q), b == null ? "no bot" : "bot active " + DmzBotQuests.active(b) + " " + DmzBotQuests.status(b, Q, 0));
            if (b != null) botHere(p, b, false);
            dump();
        }), 20);

        // 2. its bandits don't live in a mushroom field: they come to you; defeat them (rounds until the count is done)
        for (int round = 0; round < 4; round++) {
            final int rn = round;
            step(rn == 0 ? "dmzbiome_bandits_came" : null, () -> server(p -> {
                int alive = p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(80), x -> x.isAlive() && Q.equals(x.getPersistentData().getString("dmz_quest_key"))).size();
                int killed = defeat(p, Q);
                PartyBotEntity b = bot(p);
                log("round " + (rn + 1) + ": summoned here " + alive + ", defeated " + killed + " | you " + DmzQuests.progress(p, Q, 0) + " | bot " + (b == null ? "-" : DmzBotQuests.status(b, Q, 0)));
                dump();
            }), 280);
        }
        step(null, () -> server(p -> {
            PartyBotEntity b = bot(p);
            String you = DmzQuests.progress(p, Q, 0), bs = b == null ? "-" : DmzBotQuests.status(b, Q, 0);
            int req = 0;
            try { req = (Integer) call(quest(Q), "getObjectiveRequired", pqd(p), Q, 0); } catch (Throwable ignored) {}
            check("your kills done outside their biome", you.contains("objective0=" + req) && req > 0, you + " (needs " + req + ")");
            check("the bot's kills done outside their biome", bs.contains("obj0=" + bs.replaceAll(".*obj0=\\d+/(\\d+).*", "$1") + "/"), bs);
            DmzTalkNpcs.update(p);
        }), 40);
        // 3. hand it in: you (Yamcha comes), then the bot
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            Entity e = DmzTalkNpcs.tempFor(p, Q);
            log("talk: Yamcha came=" + (e != null));
            if (e != null) net.minecraftforge.common.ForgeHooks.onInteractEntity(p, e, InteractionHand.MAIN_HAND);
        }); }, 30);
        step("dmzbiome_done", () -> { mc.setScreen(null); server(p -> {
            String pr = DmzQuests.progress(p, Q, 1);
            check("your desert mission completed in a mushroom field", pr.contains("completed=true") || pr.contains("COMPLETED") || pr.contains("objective1=1"), pr + " biome " + biomeHere(p));
            PartyBotEntity b = bot(p);
            if (b != null) botHere(p, b, false);
            DmzTalkNpcs.update(p);
        }); }, 60);
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) return;
            DmzTalkNpcs.update(p);
            Entity e = DmzTalkNpcs.tempFor(p, DmzPerCharacter.botKey(b) + "|" + Q);
            log("bot talk: Yamcha came for the bot=" + (e != null) + " " + DmzBotQuests.status(b, Q, 1));
            if (e != null) net.minecraftforge.common.ForgeHooks.onInteractEntity(p, e, InteractionHand.MAIN_HAND);
        }); }, 30);
        step(null, () -> server(p -> {
            PartyBotEntity b = bot(p);
            String st = b == null ? "-" : DmzBotQuests.status(b, Q, 1);
            check("the bot's desert mission completed in a mushroom field", st.startsWith("status=SUCCESS") || st.startsWith("status=COMPLETED"), st);
            dump();
        }), 20);

        // 4. "World Explorer": be in the plains, the desert and the forest - all here. Off: nothing; on: done (you and the bot)
        step(null, () -> server(p -> {
            quiet(p);
            XwConfig.DMZ_MISSIONS_IGNORE_BIOMES.set(false);
            call(pqd(p), "resetQuest", EXPLORE);
            DmzQuests.accept(p, EXPLORE);
            DmzPerCharacter.syncAll(p);
        }), 80);
        step(null, () -> server(p -> {
            String pr = DmzQuests.progress(p, EXPLORE, 1);
            check("control: off, World Explorer doesn't move here", pr.contains("objective1=0") && !pr.contains("completed=true"), pr);
            XwConfig.DMZ_MISSIONS_IGNORE_BIOMES.set(true);
        }), 80);
        step("dmzbiome_explorer", () -> server(p -> {
            String a = DmzQuests.progress(p, EXPLORE, 0), b = DmzQuests.progress(p, EXPLORE, 1), c = DmzQuests.progress(p, EXPLORE, 2);
            boolean ok = (a.contains("objective0=1") || a.contains("completed=true")) && (b.contains("objective1=1") || b.contains("completed=true")) && (c.contains("objective2=1") || c.contains("completed=true"));
            check("on: World Explorer's three biomes reached in a mushroom field", ok, a + " | " + b + " | " + c);
            PartyBotEntity bt = bot(p);
            if (bt != null) { DmzBotQuests.resetForTest(bt); log("bot takes World Explorer: " + DmzBotQuests.startForTest(bt, EXPLORE)); }
        }), 80);
        step(null, () -> server(p -> {
            PartyBotEntity bt = bot(p);
            String st = bt == null ? "-" : DmzBotQuests.status(bt, EXPLORE, 0) + " | " + DmzBotQuests.status(bt, EXPLORE, 2);
            check("the bot's World Explorer done in a mushroom field", st.startsWith("status=SUCCESS") || st.startsWith("status=COMPLETED") || (st.contains("obj0=1/") && st.contains("obj2=1/")), st);
            check("biome waivers counted", DmzQuests.biomeWaivedCount() > N[0], N[0] + " -> " + DmzQuests.biomeWaivedCount());
            dump();
            log("summary pass=" + pass + " fail=" + fail);
            log("done");
        }), 20);
    }
}
