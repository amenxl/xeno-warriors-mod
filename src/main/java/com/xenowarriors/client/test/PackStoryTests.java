package com.xenowarriors.client.test;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.client.story.StoryDialogueScreen;
import com.xenowarriors.story.Story;
import com.xenowarriors.story.StoryRun;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import java.util.List;

/**
 * (focus "packstory") Story mode end to end, alone: start it with your own character (everyone else locked), Rhalix's
 * first mission played through (its scenes read, its fights won - the fleeing boss too), who you met and what you can
 * call as bots, a bond making someone playable, a hub visit and a side mission, every story place, then the rest
 * skipped to the finale, the finale against Ultima played, and everyone unlocked at the end. Pictures along the way.
 */
final class PackStoryTests {
    private PackStoryTests() {}

    private static final long[] MONEY = new long[1];
    private static final int[] HITS = new int[1];

    /** Plays the host's current story run until it's over (or the time runs out): reads every line, takes the first choice, wins every fight. */
    private static void drive(Minecraft mc, List<Step> l, String tag, int rounds, String shotAt) {
        for (int i = 0; i < rounds; i++) {
            int ii = i;
            l.add(new Step(ii == 3 && shotAt != null ? shotAt : null, () -> {
                if (mc.screen instanceof StoryDialogueScreen d) { d.testNext(0); return; }
                PackTests.server(p -> {
                    List<Mob> foes = StoryRun.testFoes(p);
                    for (Mob m : foes) {
                        if (m.getHealth() > m.getMaxHealth() * 0.3F) m.setHealth(m.getMaxHealth() * 0.3F);   // (a fleeing boss gets away below its line)
                        else m.hurt(p.damageSources().genericKill(), Float.MAX_VALUE);
                        HITS[0]++;
                    }
                });
            }, 6));
        }
    }

    /** The Season 1 storylines open now (Season 2's and the flashbacks left out). */
    private static List<String> s1(net.minecraft.server.level.ServerPlayer p) { return Story.available(p).stream().filter(x -> !Story.s2Path(x)).toList(); }

    static void build(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> PackTests.server(p -> {
            com.xenowarriors.character.Seasons.set(p.server, 3);
            PackTests.arena(p); PackTests.clear(p);
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);          // (no Karma Level wait)
            var d = p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).orElse(null);
            var s = new com.xenowarriors.you.YouSlot(); s.name = "Aster"; s.race = com.xenowarriors.character.Race.SKYLANDER; s.element = com.xenowarriors.ka.Element.WIND;
            d.youSlots[6] = s;
            com.xenowarriors.character.CharacterManager.setYou(p, 6);
            com.xenowarriors.you.YouRaces.apply(p);
            Story.reset(p);
            MONEY[0] = com.xenowarriors.economy.Money.get(p);
        }), 30));
        l.add(new Step(null, () -> PackTests.server(Story::start), 30));
        l.add(new Step("story_menu_start", () -> {}, 5));
        l.add(new Step(null, () -> PackTests.server(p -> {
            com.xenowarriors.network.SelectCharacterPacket.select(p, CharacterType.RHALIX);
            PackTests.check("story: Rhalix can't be picked before you meet him", com.xenowarriors.character.ModCapabilities.getType(p) == CharacterType.YOU, "playing " + com.xenowarriors.character.ModCapabilities.getType(p));
            PackTests.check("story: the select screen data marks him locked", !Story.playable(p, CharacterType.RHALIX) && Story.lockReason(p, CharacterType.RHALIX) != null, "" + Story.lockReason(p, CharacterType.RHALIX));
            PackTests.check("story: Season 1's flashbacks open as soon as Season 2 is unlocked (before Season 1 is beaten)", Story.available(p).contains("past") && !Story.available(p).contains("s2_xeno"), "" + Story.available(p));
            PackTests.check("story: only Rhalix's and Mikiko's storylines to start with", s1(p).equals(List.of("rhalix", "mikiko")), "" + Story.available(p));
            Story.pick(p, "rhalix");
            com.xenowarriors.story.StoryRun.openMenu(p);
        }), 20));
        l.add(new Step("story_menu_rhalix", () -> {}, 5));
        l.add(new Step(null, () -> { mc.setScreen(null); mc.options.setCameraType(CameraType.FIRST_PERSON); }, 5));
        // Rhalix's first mission, all of it
        l.add(new Step(null, () -> PackTests.server(StoryRun::begin), 30));
        l.add(new Step("story_r1_scene", () -> {}, 40));
        l.add(new Step(null, () -> { if (mc.screen instanceof StoryDialogueScreen d) for (int k = 0; k < 3; k++) d.testNext(0); }, 10));
        l.add(new Step("story_r1_dialogue", () -> {}, 5));
        l.add(new Step(null, () -> PackTests.server(p -> PackTests.log("story r1 npcs: " + StoryRun.testNpcs(p).size())), 2));
        drive(mc, l, "r1", 140, "story_r1_fight");
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.check("story: the first mission completes", Story.missionIndex(p) == 1 && "rhalix".equals(Story.current(p)), "mission " + Story.missionIndex(p) + " path " + Story.current(p) + " hits " + HITS[0]);
            PackTests.check("story: you met the Sky Patrol (and Kyrie, and Kill)", Story.met(p).containsAll(List.of("rhalix", "lexy", "sam", "max", "kyrie", "kill")), "met " + Story.met(p));
            PackTests.check("story: the mission paid", com.xenowarriors.economy.Money.get(p) > MONEY[0], MONEY[0] + " -> " + com.xenowarriors.economy.Money.get(p));
            PackTests.check("story: back home after the mission", !StoryRun.inRun(p) && !com.xenowarriors.tower.Tower.is(p.level()), "in run " + StoryRun.inRun(p) + " dim " + p.level().dimension().location());
            PackTests.check("story: met people can come as bots, others can't", Story.botAllowed(p, CharacterType.SAM) && !Story.botAllowed(p, CharacterType.MIKIKO), "");
            PackTests.check("story: met isn't playable yet (bond)", !Story.playable(p, CharacterType.SAM), "bond " + Story.bond(p, CharacterType.SAM));
            com.xenowarriors.party.Bonds.set(p, "sam", Story.BOND_NEED, 0);
            PackTests.check("story: bond 40 makes Sam playable", Story.playable(p, CharacterType.SAM), "bond " + Story.bond(p, CharacterType.SAM));
            com.xenowarriors.party.Bonds.set(p, "rhalix", 60, 0);
            PackTests.check("story: Rhalix needs 75 (or his storyline)", !Story.playable(p, CharacterType.RHALIX), "bond " + Story.bond(p, CharacterType.RHALIX));
        }), 10));
        // the hub: talk to Sam, take his side mission
        l.add(new Step(null, () -> PackTests.server(StoryRun::visitHub), 40));
        l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 5));
        l.add(new Step("story_hub", () -> PackTests.server(p -> PackTests.check("story: the hub has the people you met", StoryRun.testNpcs(p).size() >= 3, "npcs " + StoryRun.testNpcs(p).size())), 5));
        l.add(new Step(null, () -> { mc.options.hideGui = false; PackTests.server(p -> com.xenowarriors.story.StorySides.talkForTest(p, "sam")); }, 20));
        l.add(new Step("story_side_offer", () -> {}, 40));
        for (int k = 0; k < 6; k++) l.add(new Step(null, () -> { if (mc.screen instanceof StoryDialogueScreen d) d.testNext(0); }, 8));
        l.add(new Step(null, () -> PackTests.server(p -> PackTests.check("story: Sam's side mission started (back in the world)", com.xenowarriors.story.StorySides.testActive(p) && !StoryRun.inRun(p), "side " + com.xenowarriors.story.StorySides.testActive(p))), 60));
        for (int k = 0; k < 50; k++) l.add(new Step(k == 2 ? "story_side_fight" : null, () -> {
            if (mc.screen instanceof StoryDialogueScreen d) { d.testNext(0); return; }
            PackTests.server(p -> { for (Mob m : com.xenowarriors.story.StorySides.testFoes(p)) m.hurt(p.damageSources().genericKill(), Float.MAX_VALUE); });
        }, 20));
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.check("story: the side mission finished and grew the bond", !com.xenowarriors.story.StorySides.testActive(p) && Story.bond(p, CharacterType.SAM) > Story.BOND_NEED, "bond " + Story.bond(p, CharacterType.SAM));
            PackTests.check("story: Sam's next request waits a day", com.xenowarriors.story.StorySides.readyIn(p, "sam") > 0, "");
        }), 10));
        l.add(new Step(null, () -> mc.setScreen(null), 5));
        // every place
        for (String place : com.xenowarriors.story.StoryScenes.PLACES) {
            l.add(new Step(null, () -> PackTests.server(p -> StoryRun.testPlace(p, place, List.of("rhalix", "mikiko", "kyrie"))), 40));
            l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON); if (mc.player != null) { mc.player.setYRot(180F); mc.player.setXRot(8F); } }, 15));
            l.add(new Step("story_place_" + place, () -> {}, 3));
        }
        l.add(new Step(null, () -> { mc.options.hideGui = false; PackTests.server(p -> StoryRun.quit(p, null)); }, 20));
        // skip to the finale (the storylines in order), checking the unlocks along the way
        l.add(new Step(null, () -> PackTests.server(p -> {
            for (int k = 0; k < 20 && "rhalix".equals(Story.current(p)); k++) Story.skip(p);
            PackTests.check("story: finishing Rhalix's storyline unlocks him", Story.playable(p, CharacterType.RHALIX) && Story.pathDone(p, "rhalix"), "done " + Story.done(p));
            PackTests.check("story: then only Mikiko's", s1(p).equals(List.of("mikiko")), "" + Story.available(p));
            Story.pick(p, "mikiko");
            for (int k = 0; k < 20 && "mikiko".equals(Story.current(p)); k++) Story.skip(p);
            PackTests.check("story: then the Sky Rebellion and the Government", s1(p).equals(List.of("sr", "gov")), "" + Story.available(p));
            Story.pick(p, "gov");
            for (int k = 0; k < 20 && "gov".equals(Story.current(p)); k++) Story.skip(p);
            Story.pick(p, "sr");
            for (int k = 0; k < 20 && "sr".equals(Story.current(p)); k++) Story.skip(p);
            PackTests.check("story: then the finale", s1(p).equals(List.of("finale")) && Story.playable(p, CharacterType.KYLE) && Story.playable(p, CharacterType.SENJU), "" + Story.available(p) + " done " + Story.done(p));
            Story.pick(p, "finale");
            HITS[0] = 0;
        }), 10));
        l.add(new Step(null, () -> PackTests.server(StoryRun::begin), 40));
        l.add(new Step("story_finale_scene", () -> {}, 30));
        drive(mc, l, "finale", 160, "story_finale_fight");
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.check("story: the finale against Ultima completes - Season 1 done", Story.finished(p) && Story.pathDone(p, "finale"), "done " + Story.done(p) + " hits " + HITS[0]);
            PackTests.check("story: everyone playable at the end", Story.playable(p, CharacterType.AIONI) && Story.playable(p, CharacterType.LOGAN), "");
        }), 80));
        // ---- Season 2 (unlocked above): its storylines, and Season 1's flashbacks, open up once Season 1 is beaten
        l.add(new Step(null, () -> PackTests.server(p -> {
            List<String> av = Story.available(p);
            PackTests.check("story s2: Season 2's first two storylines and Season 1's flashbacks open after Season 1", av.contains("past") && av.contains("s2_xeno") && av.contains("s2_sp") && !av.contains("s2_fall"), "" + av);
            PackTests.check("story s2: every Season 2 storyline loaded", com.xenowarriors.story.StoryDefs.PATHS.keySet().containsAll(List.of("past", "s2_xeno", "s2_sp", "s2_fall", "s2_royals", "s2_blade", "s2_demons", "s2_finale")), "" + com.xenowarriors.story.StoryDefs.PATHS.keySet());
            Story.pick(p, "s2_xeno");
            HITS[0] = 0;
        }), 10));
        l.add(new Step(null, () -> PackTests.server(StoryRun::begin), 40));
        l.add(new Step("story_s2_scene", () -> {}, 30));
        drive(mc, l, "s2", 160, "story_s2_fight");
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.check("story s2: the first Season 2 mission (Xeno Warriors vs Ultima) plays through", "s2_xeno".equals(Story.current(p)) && Story.missionIndex(p) >= 1, "cur " + Story.current(p) + " mi " + Story.missionIndex(p) + " hits " + HITS[0]);
            StoryRun.quit(p, null);
            // the rest, skipped through a group at a time
            String[][] order = {{"s2_xeno", "s2_sp", "past"}, {"s2_fall", "s2_royals"}, {"s2_blade", "s2_demons"}, {"s2_finale"}};
            StringBuilder why = new StringBuilder();
            for (String[] g : order) for (String path : g) {
                if (!path.equals(Story.current(p)) && !Story.available(p).contains(path)) { why.append(path).append(" not open (").append(Story.available(p)).append(") "); continue; }
                if (!path.equals(Story.current(p))) Story.pick(p, path);
                for (int i = 0; i < 20 && Story.mission(p) != null; i++) Story.skip(p);
            }
            PackTests.check("story s2: every storyline opens in turn and Season 2's finale completes", Story.pathDone(p, "s2_finale") && Story.pathDone(p, "past"), why + "done " + Story.done(p));
        }), 30));
        l.add(new Step(null, () -> PackTests.server(p -> {
            Story.stop(p);
            com.xenowarriors.story.StoryRun.openMenu(p);
        }), 20));
        l.add(new Step("story_menu_end", () -> {}, 5));
        // DragonMineZ's quest screen gets the story button
        l.add(new Step(null, () -> {
            mc.setScreen(null);
            try {
                Class<?> c = Class.forName(com.xenowarriors.client.story.StoryClientEvents.DMZ_QUESTS);
                Object s = null;
                for (var k : c.getConstructors()) {
                    if (k.getParameterCount() == 0) { s = k.newInstance(); break; }
                    if (k.getParameterCount() == 1 && net.minecraft.client.gui.screens.Screen.class.isAssignableFrom(k.getParameterTypes()[0])) { s = k.newInstance((Object) null); break; }
                }
                if (s instanceof net.minecraft.client.gui.screens.Screen sc) mc.setScreen(sc);
                PackTests.log("story: dmz quest screen " + (s == null ? "not opened" : "opened"));
            } catch (Throwable t) { PackTests.log("story: no DragonMineZ quest screen (" + t + ")"); }
        }, 30));
        l.add(new Step("story_dmz", () -> {
            boolean dmz = net.minecraftforge.fml.ModList.get().isLoaded("dragonminez");
            if (dmz && mc.screen != null && mc.screen.getClass().getName().equals(com.xenowarriors.client.story.StoryClientEvents.DMZ_QUESTS))
                PackTests.check("story: DragonMineZ's quest screen has the story button", com.xenowarriors.client.story.StoryClientEvents.dmzButton, "");
        }, 5));
        l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = false; PackTests.server(p -> { PackTests.arena(p); PackTests.clear(p); }); }, 10));
    }
}
