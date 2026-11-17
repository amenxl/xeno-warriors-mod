package com.xenowarriors.client.test;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.client.compat.DmzForms;
import com.xenowarriors.client.compat.epicfight.EfBotBodies;
import com.xenowarriors.client.race.RaceLook;
import com.xenowarriors.client.render.CharacterDetailLayer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * (Pack test, focus "packlooks", pictures for the user) Every character in a line-up: Super Saiyan 1 / 3 / 4 and
 * Legendary, Buffed (tops off, muscle), each DragonMineZ race (Namekian, Majin, Frost Demon, Bio-Android, Glind, Saiyan
 * tail), and the new DragonMineZ-moment faces. Out of battle mode, then the races again on Epic Fight's body.
 */
final class PackLooksTests {
    private PackLooksTests() {}

    static final CharacterType[][] LINEUPS = {
            {CharacterType.MIKIKO, CharacterType.RHALIX, CharacterType.KYRIE, CharacterType.YURI, CharacterType.KAERU, CharacterType.KEITA},
            {CharacterType.SAM, CharacterType.MAX, CharacterType.LEXY, CharacterType.TECHNICA, CharacterType.LOGAN, CharacterType.GARRY},
            {CharacterType.ZANE, CharacterType.KILL, CharacterType.STEVIE, CharacterType.AIONI, CharacterType.ULTIMA}};

    private static void clear(net.minecraft.server.level.ServerPlayer p) {
        for (PartyBotEntity b : PartyBots.bots(p)) b.discard();
    }

    static void build(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> { UiTest.character(CharacterType.MIKIKO); EfBotBodies.off = true; }, 40));
        for (int li = 0; li < LINEUPS.length; li++) {
            CharacterType[] who = LINEUPS[li];
            int n = li;
            l.add(new Step(null, () -> PackTests.server(p -> {
                PackTests.arena(p); clear(p);
                p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                for (int i = 0; i < who.length; i++) {
                    PartyBotEntity b = PartyBots.spawn(p, who[i], null);
                    if (b == null) continue;
                    b.setNoAi(true);
                    b.moveTo(p.getX() + (i - (who.length - 1) / 2.0) * 1.7, p.getY(), p.getZ() + 4.2, 180F, 0F);
                    b.setYHeadRot(180F); b.yBodyRot = 180F;
                }
                p.setYRot(0); p.setXRot(8);
            }), 60));
            l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON);
                if (mc.player != null) { mc.player.setYRot(0); mc.player.setXRot(8); } }, 10));
            String[][] forms = {{"base", ""}, {"ssj", "supersaiyan"}, {"ssj2", "supersaiyan2"}, {"ssj3", "supersaiyan3"}, {"ssj4", "supersaiyan4"},
                    {"lssj", "ssjfullpower"}, {"buffed", "buffed"}};
            for (String[] f : forms) {
                l.add(new Step(null, () -> { DmzForms.testForm = f[1]; RaceLook.testRace = ""; CharacterDetailLayer.testExpr = null; }, 12));
                l.add(new Step("looks_" + n + "_form_" + f[0], () -> {}, 3));
            }
            for (String r : new String[]{RaceLook.NAMEK, RaceLook.MAJIN, RaceLook.FROST, RaceLook.BIO, RaceLook.GLIND, RaceLook.SAIYAN}) {
                l.add(new Step(null, () -> { DmzForms.testForm = ""; RaceLook.testRace = r; CharacterDetailLayer.testExpr = null; }, 12));
                l.add(new Step("looks_" + n + "_race_" + r, () -> {
                    PackTests.log("looks race " + r + ": recoloured textures " + RaceLook.tinted + ", parts drawn " + RaceLook.partsDrawn);
                    if (n == 0 && mc.player != null) PackTests.check("looks: HUD portrait shows the face as a " + r, com.xenowarriors.client.HudPortrait.faceShown(mc.player, CharacterType.MIKIKO), r);
                }, 3));
            }
            for (String e : new String[]{"powerup", "beam", "strain", "meditate", "rhythm", "transform"}) {
                l.add(new Step(null, () -> { DmzForms.testForm = ""; RaceLook.testRace = ""; CharacterDetailLayer.testExpr = e; }, 8));
                l.add(new Step("looks_" + n + "_face_" + e, () -> {}, 3));
            }
            // the other races' forms on our characters (DragonMineZ would otherwise draw its own body)
            String[][] raceForms = {{RaceLook.MAJIN, "evil"}, {RaceLook.MAJIN, "kid"}, {RaceLook.FROST, "final"}, {RaceLook.FROST, "metal"},
                    {RaceLook.BIO, "perfect"}, {RaceLook.GLIND, "darkking"}, {RaceLook.SAIYAN, "supersaiyangodritual"}};
            for (String[] rf : raceForms) {
                l.add(new Step(null, () -> { DmzForms.testForm = rf[1]; RaceLook.testRace = rf[0]; CharacterDetailLayer.testExpr = null; }, 12));
                l.add(new Step("looks_" + n + "_raceform_" + rf[0] + "_" + rf[1], () -> PackTests.check("looks: " + rf[1] + " drawn on our characters, not DragonMineZ's",
                        mc.player != null && !DmzForms.dmzDraws(mc.player), rf[0] + " " + rf[1]), 3));
            }
            // a race and a form together
            l.add(new Step(null, () -> { DmzForms.testForm = "supersaiyan"; RaceLook.testRace = RaceLook.NAMEK; CharacterDetailLayer.testExpr = "powerup"; }, 12));
            l.add(new Step("looks_" + n + "_namek_ssj_powerup", () -> {}, 3));
            if (n == 0) {                                                       // on Epic Fight's body
                l.add(new Step(null, () -> { EfBotBodies.off = false; DmzForms.testForm = ""; CharacterDetailLayer.testExpr = null; }, 30));
                for (String r : new String[]{RaceLook.NAMEK, RaceLook.FROST, RaceLook.SAIYAN}) {
                    l.add(new Step(null, () -> RaceLook.testRace = r, 12));
                    l.add(new Step("looks_ef_race_" + r, () -> {}, 3));
                }
                l.add(new Step(null, () -> { RaceLook.testRace = ""; DmzForms.testForm = "buffed"; }, 12));
                l.add(new Step("looks_ef_buffed", () -> {}, 3));
                l.add(new Step(null, () -> EfBotBodies.off = true, 10));
            }
        }
        l.add(new Step(null, () -> {
            DmzForms.testForm = null; RaceLook.testRace = null; CharacterDetailLayer.testExpr = null; EfBotBodies.off = false;
            PackTests.check("looks: race skins recoloured and race parts drawn", RaceLook.tinted > 0 && RaceLook.partsDrawn > 0, "tinted " + RaceLook.tinted + " parts " + RaceLook.partsDrawn);
            PackTests.check("looks: their own hair recoloured for the forms", com.xenowarriors.client.race.FormHair.recoloured > 0, "hair sheets recoloured " + com.xenowarriors.client.race.FormHair.recoloured);
            PackTests.server(PackLooksTests::clear);
            mc.options.hideGui = false;
        }, 5));
    }

    /** (focus "packoutfits") Rhalix in each outfit, from the front: out of battle mode and in it. */
    static void outfits(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> UiTest.character(CharacterType.RHALIX), 40));
        l.add(new Step(null, () -> PackTests.server(p -> { PackTests.arena(p); clear(p); p.setGameMode(net.minecraft.world.level.GameType.CREATIVE); p.setYRot(0); p.setXRot(10); }), 20));
        for (int battle = 0; battle < 3; battle++) {
            boolean ef = battle == 1, cel = battle == 2;
            l.add(new Step(null, () -> { com.xenowarriors.client.cel.Cel.force = cel; com.xenowarriors.client.cel.Cel.clear(); }, 2));
            l.add(new Step(null, () -> {
                mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                try { var pp = yesman.epicfight.client.ClientEngine.getInstance().getPlayerPatch(); if (pp != null) { if (ef) pp.toEpicFightMode(true); else pp.toVanillaMode(true); } } catch (Throwable ignored) {}
            }, 20));
            for (int o = 0; o < 4; o++) {
                int oo = o;
                l.add(new Step(null, () -> PackTests.server(p -> {
                    p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.outfit = oo);
                    com.xenowarriors.character.CharacterManager.syncToTrackingAndSelf(p);
                }), 20));
                l.add(new Step("outfits_rhalix_" + oo + (ef ? "_ef" : cel ? "_cel" : ""), () -> {}, 3));
            }
        }
        l.add(new Step(null, () -> { com.xenowarriors.client.cel.Cel.force = false; com.xenowarriors.client.cel.Cel.clear();
            PackTests.server(p -> p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.outfit = 0));
            mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = false; }, 5));
    }

    /** (focus "packallmodels") Every character in every outfit, front and back, out of battle mode and in it. */
    static void allModels(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> PackTests.server(p -> { PackTests.arena(p); clear(p); p.setGameMode(net.minecraft.world.level.GameType.CREATIVE); p.setYRot(0); p.setXRot(10); }), 20));
        for (CharacterType t : CharacterType.PLAYABLE) {
            int outfits = Math.max(1, t.outfitKeys().length);
            l.add(new Step(null, () -> UiTest.character(t), 30));
            for (int o = 0; o < outfits; o++) {
                int oo = o;
                l.add(new Step(null, () -> PackTests.server(p -> {
                    p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.outfit = oo);
                    com.xenowarriors.character.CharacterManager.syncToTrackingAndSelf(p);
                }), 15));
                for (int battle = 0; battle < 2; battle++) {
                    boolean ef = battle == 1;
                    l.add(new Step(null, () -> {
                        mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                        try { var pp = yesman.epicfight.client.ClientEngine.getInstance().getPlayerPatch(); if (pp != null) { if (ef) pp.toEpicFightMode(true); else pp.toVanillaMode(true); } } catch (Throwable ignored) {}
                    }, 14));
                    l.add(new Step("models_" + t.getId() + "_" + oo + (ef ? "_ef" : "") + "_front", () -> {}, 2));
                    l.add(new Step(null, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK), 8));
                    l.add(new Step("models_" + t.getId() + "_" + oo + (ef ? "_ef" : "") + "_back", () -> {}, 2));
                }
            }
            l.add(new Step(null, () -> PackTests.server(p -> p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.outfit = 0)), 5));
        }
        l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = false; }, 5));
    }
}
