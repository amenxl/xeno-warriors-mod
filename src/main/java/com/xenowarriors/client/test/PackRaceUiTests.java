package com.xenowarriors.client.test;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.client.race.RaceLook;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * (focus "packraceui") The menus and HUD with each DragonMineZ race: the main menu's model, Character Select,
 * Character Customization (outfits preview), Party Setup and the in-game HUD (portrait, party card for a bot) -
 * pictures of each, the HUD portrait checked for a face, and the screens checked they open.
 */
final class PackRaceUiTests {
    private PackRaceUiTests() {}

    static void build(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> UiTest.character(CharacterType.MIKIKO), 30));
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p);
            for (PartyBotEntity b : PartyBots.bots(p)) b.discard();
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.KYRIE, null);
            if (b != null) b.moveTo(p.getX() + 2, p.getY(), p.getZ() + 3, 180F, 0F);
            p.setYRot(0); p.setXRot(10);
        }), 40));
        for (String r : new String[]{RaceLook.NAMEK, RaceLook.MAJIN, RaceLook.FROST, RaceLook.BIO, RaceLook.GLIND, RaceLook.SAIYAN}) {
            l.add(new Step(null, () -> { RaceLook.testRace = r; mc.setScreen(null); }, 10));
            screen(mc, l, r, "main", () -> com.xenowarriors.client.PlayerStatsScreen.open());
            screen(mc, l, r, "select", () -> mc.setScreen(new com.xenowarriors.client.screen.CharacterSelectScreen()));
            screen(mc, l, r, "customization", () -> com.xenowarriors.client.PlayerStatsScreen.openTab(null, 1));
            screen(mc, l, r, "party", () -> {
                var m = new com.xenowarriors.client.menu.MainMenuScreen();
                mc.setScreen(m);
                for (int i = 0; i < m.entryCount(); i++) if (m.entryName(i).toLowerCase(java.util.Locale.ROOT).contains("party")) { m.select(i); break; }
                m.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0);
            });
            l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = false; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); }, 25));
            l.add(new Step("raceui_" + r + "_hud", () -> PackTests.check("raceui " + r + ": HUD portrait shows the face",
                    mc.player != null && com.xenowarriors.client.HudPortrait.faceShown(mc.player, CharacterType.MIKIKO), r), 3));
        }
        l.add(new Step(null, () -> { RaceLook.testRace = null; mc.setScreen(null); mc.options.setCameraType(CameraType.FIRST_PERSON);
            PackTests.server(p -> { for (PartyBotEntity b : PartyBots.bots(p)) b.discard(); }); }, 5));
    }

    private static void screen(Minecraft mc, List<Step> l, String race, String name, Runnable open) {
        l.add(new Step(null, () -> { try { mc.setScreen(null); open.run(); } catch (Throwable t) { PackTests.check("raceui " + race + " " + name + " opens", false, String.valueOf(t)); } }, 25));
        l.add(new Step("raceui_" + race + "_" + name, () -> PackTests.check("raceui " + race + ": " + name + " opens", mc.screen != null,
                mc.screen == null ? "no screen" : mc.screen.getClass().getSimpleName()), 2));
    }
}
