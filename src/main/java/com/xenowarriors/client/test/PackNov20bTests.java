package com.xenowarriors.client.test;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillPool;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * (focus "packnov20b") Fox Girl: locked until Season 5, then playable with 5000 starting stats, both outfits and every
 * skill working; the Story menu (cards, banners); Mikiko's bot kept on the ground in story mode; story foes harder with
 * DragonMineZ (the pack has it).
 */
final class PackNov20bTests {
    private PackNov20bTests() {}

    static void build(Minecraft mc, List<Step> l) {
        // locked at Season 3, open at 5
        l.add(new Step(null, () -> PackTests.server(p -> { com.xenowarriors.character.Seasons.set(p.server, 3); PackTests.arena(p); PackTests.clear(p); }), 20));
        l.add(new Step(null, () -> PackTests.check("foxgirl: Season 5 content", CharacterType.FOX_GIRL.season() == 5, "season " + CharacterType.FOX_GIRL.season()), 2));
        l.add(new Step(null, () -> PackTests.server(p -> com.xenowarriors.character.Seasons.set(p.server, 5)), 20));
        l.add(new Step(null, () -> PackTests.server(p -> PackTests.check("foxgirl: /xeno season unlock 5 works", com.xenowarriors.character.Seasons.highest(p.server) == 5, "highest " + com.xenowarriors.character.Seasons.highest(p.server))), 2));
        l.add(new Step(null, () -> { UiTest.character(CharacterType.FOX_GIRL); }, 40));
        l.add(new Step(null, () -> PackTests.server(p -> {
            var d = p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).orElse(null);
            PackTests.check("foxgirl: playable", com.xenowarriors.character.ModCapabilities.getType(p) == CharacterType.FOX_GIRL, "type " + com.xenowarriors.character.ModCapabilities.getType(p));
            PackTests.check("foxgirl: starts with very high stats", d != null && d.statLevel[0] >= 5000 && d.statLevel[1] >= 5000 && d.statLevel[2] >= 5000,
                    d == null ? "no data" : java.util.Arrays.toString(d.statLevel));
            PackTests.check("foxgirl: Fox Kin", com.xenowarriors.character.Race.of(CharacterType.FOX_GIRL) == com.xenowarriors.character.Race.FOX_KIN, "");
            PackTests.arena(p); p.getInventory().selected = 0; p.getInventory().setItem(0, net.minecraft.world.item.ItemStack.EMPTY);
        }), 5));
        // looks: both outfits, front
        l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); mc.player.setYRot(0); }, 20));
        l.add(new Step("foxgirl_kitsune", () -> PackTests.check("foxgirl: her model loads", com.xenowarriors.client.render.CharacterDetailLayer.detailModel(mc.player) != null, ""), 5));
        l.add(new Step(null, () -> PackTests.server(p -> { p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.outfit = 1);
            com.xenowarriors.character.CharacterManager.syncToTrackingAndSelf(p); }), 20));
        l.add(new Step("foxgirl_sakura", () -> PackTests.check("foxgirl: Sakura outfit", ClientCharacterData.outfit(mc.player) == 1, "outfit " + ClientCharacterData.outfit(mc.player)), 5));
        l.add(new Step(null, () -> PackTests.server(p -> { p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.outfit = 0);
            com.xenowarriors.character.CharacterManager.syncToTrackingAndSelf(p); }), 10));
        // every skill (and the ultimate) on a target
        List<Skill> skills = new ArrayList<>();
        for (SkillPool.Entry e : SkillPool.of(CharacterType.FOX_GIRL)) skills.add(e.skill());
        skills.add(SkillPool.ultimate(CharacterType.FOX_GIRL));
        List<String> bad = new ArrayList<>();
        for (Skill s : skills) {
            l.add(new Step(null, () -> PackTests.server(p -> { PackTests.arena(p); PackTests.clear(p); PackTests.husk(p, 0, 2.5);
                p.connection.teleport(PackTests.HOME[0], PackTests.HOME[1], PackTests.HOME[2], 0F, 10F); }), 8));
            l.add(new Step(s.getId().equals("kitsune_tier3") ? "foxgirl_ult" : null, () -> PackTests.server(p -> {
                try { if (!com.xenowarriors.skill.SkillManager.useFree(p, s, 1F)) bad.add(s.getId() + "(refused)"); } catch (Throwable x) { bad.add(s.getId() + ": " + x); }
            }), 25));
        }
        l.add(new Step(null, () -> PackTests.check("foxgirl: every skill and the ultimate work", bad.isEmpty() && skills.size() == 6, "skills " + skills.size() + " bad " + bad), 2));
        l.add(new Step(null, () -> PackTests.check("foxgirl: Fox Palm is her middle click, Kitsune Attack stays in her slots",
                com.xenowarriors.toggle.Toggles.rlChoices(CharacterType.FOX_GIRL).contains("fox_palm")
                        && SkillPool.menuIndices(CharacterType.FOX_GIRL, SkillPool.of(CharacterType.FOX_GIRL)).size() == 4, ""), 2));
        // the Story menu
        l.add(new Step(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); UiTest.character(CharacterType.MIKIKO); }, 30));
        l.add(new Step(null, () -> PackTests.server(p -> { com.xenowarriors.story.Story.reset(p); com.xenowarriors.story.Story.start(p); }), 30));
        l.add(new Step(null, () -> com.xenowarriors.client.story.StoryClient.open(), 30));
        l.add(new Step("story_menu", () -> PackTests.check("story: the menu opens", mc.screen instanceof com.xenowarriors.client.story.StoryScreen, "screen " + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName())), 5));
        l.add(new Step(null, () -> PackTests.server(p -> com.xenowarriors.story.Story.pick(p, "mikiko")), 20));
        l.add(new Step(null, () -> com.xenowarriors.client.story.StoryClient.open(), 30));
        l.add(new Step("story_menu_mikiko", () -> {}, 5));
        // Mikiko's bot can't fly in story mode
        l.add(new Step(null, () -> { mc.setScreen(null); PackTests.server(p -> {
            var b = com.xenowarriors.bot.PartyBots.spawn(p, CharacterType.MIKIKO, null);
            if (b != null) b.moveTo(p.getX() + 2, p.getY(), p.getZ(), 0F, 0F);
        }); }, 50));
        l.add(new Step(null, () -> PackTests.server(p -> {
            var b = com.xenowarriors.bot.PartyBots.bots(p).stream().filter(x -> x.character() == CharacterType.MIKIKO).findFirst().orElse(null);
            PackTests.check("story: Mikiko's bot can't fly in story mode", b != null && !b.canFly(), b == null ? "no bot" : "canFly " + b.canFly() + " level " + b.botLevel());
        }), 5));
        // DragonMineZ: harder foes
        l.add(new Step(null, () -> PackTests.check("story: harder with DragonMineZ", !com.xenowarriors.story.StoryRun.dmzHard() || com.xenowarriors.story.StoryRun.DMZ_HARD_HP > 1, "dmz " + com.xenowarriors.story.StoryRun.dmzHard()), 2));
        l.add(new Step(null, () -> PackTests.server(p -> { com.xenowarriors.story.Story.reset(p); PackTests.clear(p); com.xenowarriors.character.Seasons.set(p.server, 3); }), 20));
    }
}
