package com.xenowarriors.client.test;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillPool;
import com.xenowarriors.skill.nc.NcSkills;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * (focus "packnew") The seven new characters (Kyle, Zack, Kaitlyn, Chase, Senju, Ava, Persephone): pictures of each from
 * the front and back, every skill, ultimate and KA used on a husk (none may throw), their form on the transform key
 * (Ava's and Persephone's demon looks), names and icons there, and all seven together as bots.
 */
final class PackNewCharsTests {
    private PackNewCharsTests() {}

    static final CharacterType[] NEW = {CharacterType.KYLE, CharacterType.ZACK, CharacterType.KAITLYN, CharacterType.CHASE, CharacterType.SENJU, CharacterType.AVA, CharacterType.PERSEPHONE};

    private static void reset(ServerPlayer p) {
        PackTests.arena(p);
        p.setHealth(p.getMaxHealth());
        p.removeAllEffects();
        p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
    }

    static void build(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> PackTests.server(p -> {
            com.xenowarriors.character.Seasons.set(p.server, 3);
            PackTests.arena(p); PackTests.clear(p);
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        }), 20));
        for (CharacterType t : NEW) {
            l.add(new Step(null, () -> { UiTest.character(t); PackTests.server(p -> { reset(p); p.getAbilities().flying = false; p.onUpdateAbilities(); }); }, 40));
            l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                mc.player.setXRot(8F); mc.player.setYRot(0F); }, 25));
            l.add(new Step("new_" + t.getId() + "_front", () -> {}, 5));
            l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); mc.player.setXRot(8F); }, 15));
            l.add(new Step("new_" + t.getId() + "_back", () -> {}, 5));
            // the names and icons are there
            l.add(new Step(null, () -> {
                List<String> missing = new ArrayList<>();
                List<Skill> all = new ArrayList<>();
                for (SkillPool.Entry e : SkillPool.of(t)) all.add(e.skill());
                if (SkillPool.ultimate(t) != null) all.add(SkillPool.ultimate(t));
                for (Skill s : all) {
                    if (!net.minecraft.client.resources.language.I18n.exists("skill.xenowarriors." + s.getId())) missing.add("name:" + s.getId());
                    String icon = s.getId().endsWith("_click") ? s.getId().substring(0, s.getId().length() - 6) : s.getId();
                    if (!icon.equals("devil_self_split") && mc.getResourceManager().getResource(com.xenowarriors.XenoWarriors.id("textures/gui/skill/" + icon + ".png")).isEmpty()) missing.add("icon:" + icon);
                }
                if (!net.minecraft.client.resources.language.I18n.exists("character.xenowarriors." + t.getId() + ".title")) missing.add("title");
                if (mc.getResourceManager().getResource(com.xenowarriors.XenoWarriors.id("textures/gui/hud/portrait_" + t.getId() + "_normal.png")).isEmpty()) missing.add("portrait");
                PackTests.check("new " + t.getId() + ": names, icons and portrait", missing.isEmpty(), "missing " + missing);
                for (String rl : com.xenowarriors.toggle.Toggles.rlChoices(t)) {
                    boolean inPool = false;
                    for (SkillPool.Entry e : SkillPool.of(t)) if (e.skill().getId().equals(rl)) inPool = true;
                    PackTests.check("new " + t.getId() + ": click move " + rl + " is in its pool", inPool, "");
                }
                int slots = 0;
                for (int i = 0; i < 4; i++) if (SkillPool.entry(t, i) != null && !SkillPool.rlOnly(t, SkillPool.entry(t, i))) slots++;
                PackTests.check("new " + t.getId() + ": four skills in the default slots", slots == 4, "slots " + slots);
            }, 2));
            // every skill and the ultimate, on a husk in front
            List<Skill> skills = new ArrayList<>();
            for (SkillPool.Entry e : SkillPool.of(t)) skills.add(e.skill());
            skills.add(SkillPool.ultimate(t));
            List<String> threw = new ArrayList<>(), used = new ArrayList<>();
            for (Skill s : skills) {
                l.add(new Step(null, () -> PackTests.server(p -> {
                    reset(p); PackTests.clear(p);
                    PackTests.husk(p, 0, 4); PackTests.husk(p, 1.5, 6);
                }), 6));
                l.add(new Step(null, () -> PackTests.server(p -> {
                    p.connection.teleport(PackTests.HOME[0], PackTests.HOME[1], PackTests.HOME[2], 0F, 12F);
                    try { if (s.use(p)) used.add(s.getId()); } catch (Throwable x) { threw.add(s.getId() + ": " + x); com.xenowarriors.XenoWarriors.LOGGER.error("XW-TEST skill threw", x); }
                }), s == skills.get(skills.size() - 1) ? 50 : 30));
            }
            if (t == CharacterType.ZACK || t == CharacterType.KAITLYN) {
                l.add(new Step(null, () -> PackTests.server(p -> { reset(p); PackTests.clear(p); PackTests.husk(p, 0, 5); }), 6));
                l.add(new Step(null, () -> PackTests.server(p -> {
                    p.connection.teleport(PackTests.HOME[0], PackTests.HOME[1], PackTests.HOME[2], 0F, 10F);
                    try { if (t == CharacterType.ZACK ? NcSkills.infiniteLoop(p) : NcSkills.raisedHand(p)) used.add("ka"); } catch (Throwable x) { threw.add("ka: " + x); }
                }), 120));
            }
            l.add(new Step(null, () -> {
                PackTests.check("new " + t.getId() + ": every skill, the ultimate and the KA go off without an error", threw.isEmpty(), "threw " + threw);
                PackTests.log("new " + t.getId() + " used: " + used);
            }, 2));
            // the transform key / wheel
            var form = NcSkills.formOf(t);
            if (form != null) {
                l.add(new Step(null, () -> PackTests.server(p -> { reset(p); PackTests.clear(p); com.xenowarriors.skill.UltForms.resetForTest(p); NcSkills.clear(p.getUUID());
                    com.xenowarriors.skill.UltForms.press(p); }), 30));
                l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); mc.player.setXRot(8F); mc.player.setYRot(0F); }, 15));
                l.add(new Step("new_" + t.getId() + "_form", () -> {
                    boolean look = t.formOutfit() == 0 || ClientCharacterData.outfit(mc.player) == t.formOutfit();
                    PackTests.server(p -> PackTests.check("new " + t.getId() + ": " + form.title + " on the transform key" + (t.formOutfit() > 0 ? " (with the demon look)" : ""),
                            NcSkills.formActive(p) && com.xenowarriors.toggle.Toggles.active(p, com.xenowarriors.toggle.Toggles.transformOf(p)) && look,
                            "on " + NcSkills.formActive(p) + " look " + look));
                }, 5));
                l.add(new Step(null, () -> PackTests.server(p -> { NcSkills.clear(p.getUUID()); com.xenowarriors.skill.UltForms.resetForTest(p); p.removeAllEffects(); }), 15));
                l.add(new Step(null, () -> PackTests.check("new " + t.getId() + ": the form's look goes when it ends", ClientCharacterData.outfit(mc.player) == 0, "outfit " + ClientCharacterData.outfit(mc.player)), 2));
            }
        }
        // Kyle's buff and Chase's Hidden Will on the wheel
        l.add(new Step(null, () -> { UiTest.character(CharacterType.CHASE); }, 30));
        l.add(new Step(null, () -> PackTests.server(p -> {
            boolean hidden = NcSkills.karmaHidden(p) && com.xenowarriors.you.YouDevil.masked(p);
            PackTests.check("new chase: Hidden Will starts on (Karma hidden)", hidden, "");
        }), 20));
        l.add(new Step(null, () -> PackTests.check("new chase: Hidden Will reaches the clients (no lock-on)", com.xenowarriors.client.NcClient.hidden(mc.player.getId()), ""), 2));
        l.add(new Step(null, () -> { UiTest.character(CharacterType.KYLE); }, 30));
        l.add(new Step(null, () -> PackTests.server(p -> com.xenowarriors.toggle.Toggles.use(p, com.xenowarriors.toggle.Toggles.Entry.MODIFICATION_SURGE)), 30));
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.check("new kyle: Modification Surge lights from the wheel", NcSkills.surge(p) && p.hasEffect(net.minecraft.world.effect.MobEffects.JUMP), "");
            com.xenowarriors.toggle.Toggles.use(p, com.xenowarriors.toggle.Toggles.Entry.MODIFICATION_SURGE);
        }), 10));
        // all seven together (you as Mikiko, so none of them is taken)
        l.add(new Step(null, () -> UiTest.character(CharacterType.MIKIKO), 30));
        int[] ids = new int[NEW.length];
        l.add(new Step(null, () -> PackTests.server(p -> {
            reset(p); PackTests.clear(p); p.removeAllEffects();
            for (int i = 0; i < NEW.length; i++) {
                var b = com.xenowarriors.bot.PartyBots.spawn(p, NEW[i], null);
                if (b == null) continue;
                b.setMode(com.xenowarriors.bot.PartyBotEntity.NEUTRAL);
                b.setNoAi(true);
                b.moveTo(PackTests.HOME[0] + (i - 3) * 1.15, PackTests.HOME[1], PackTests.HOME[2] + 4.5, 180F, 0F);
                b.setYRot(180F); b.setYHeadRot(180F); b.yBodyRot = 180F;
                ids[i] = b.getId();
            }
            p.connection.teleport(PackTests.HOME[0], PackTests.HOME[1], PackTests.HOME[2] - 1.5, 0F, 5F);
        }), 50));
        l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = true; }, 10));
        l.add(new Step("new_lineup", () -> {}, 5));
        l.add(new Step(null, () -> PackTests.server(p -> {
            int n = 0;
            for (int id : ids) if (p.serverLevel().getEntity(id) instanceof com.xenowarriors.bot.PartyBotEntity) n++;
            PackTests.check("new: all seven come as bots", n == NEW.length, "bots " + n);
            PackTests.clear(p);
        }), 5));
        l.add(new Step(null, () -> { mc.options.hideGui = false; mc.setScreen(new com.xenowarriors.client.screen.CharacterSelectScreen()); }, 40));
        l.add(new Step("new_select", () -> {}, 5));
        l.add(new Step(null, () -> mc.setScreen(null), 5));
    }
}
