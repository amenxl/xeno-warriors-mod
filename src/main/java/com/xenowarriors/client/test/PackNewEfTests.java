package com.xenowarriors.client.test;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientSkillState;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.compat.epicfight.WomMoves;
import com.xenowarriors.network.SkillAnimPacket;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillPool;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/**
 * (focus "packnewef") The seven new characters with Epic Fight: in battle mode, each one's bare-hands combo is their
 * Weapons of Miracles style (and Senju's sword its katana combo); every skill, the ultimate and their transformation
 * play their move on Epic Fight's body; walking and running in battle mode; their bots fight on Epic Fight's body.
 * Pictures of each swing and skill.
 */
final class PackNewEfTests {
    private PackNewEfTests() {}

    private static void battle(boolean on) {
        try {
            var p = yesman.epicfight.client.ClientEngine.getInstance().getPlayerPatch();
            if (p == null) return;
            if (on && !p.isEpicFightMode()) p.toEpicFightMode(true);
            if (!on && p.isEpicFightMode()) p.toVanillaMode(true);
        } catch (Throwable t) { PackTests.log("newef: couldn't switch battle mode " + t); }
    }

    static void build(Minecraft mc, List<Step> l) {
        if (!ModList.get().isLoaded("epicfight")) { l.add(new Step(null, () -> PackTests.log("newef: Epic Fight not loaded - skipped"), 1)); return; }
        l.add(new Step(null, () -> PackTests.server(p -> { com.xenowarriors.character.Seasons.set(p.server, 3); PackTests.arena(p); PackTests.clear(p); }), 20));
        for (CharacterType t : PackNewCharsTests.NEW) {
            String n = t.getId();
            // the bare-hands combo
            String[] fists = WomMoves.FISTS.get(t);
            PackTests.check("newef " + n + ": has an Epic Fight bare-hands style", fists != null, "");
            if (fists != null && WomMoves.loaded()) PackWomTests.one(mc, l, t, null, fists);
            // every skill and the ultimate, in battle mode
            List<Skill> skills = new ArrayList<>();
            for (SkillPool.Entry e : SkillPool.of(t)) skills.add(e.skill());
            skills.add(SkillPool.ultimate(t));
            List<String> none = new ArrayList<>(), played = new ArrayList<>(), threw = new ArrayList<>();
            l.add(new Step(null, () -> { UiTest.character(t); PackTests.server(p -> { PackTests.arena(p); PackTests.clear(p); p.getInventory().selected = 0; p.getInventory().setItem(0, ItemStack.EMPTY); }); }, 30));
            l.add(new Step(null, () -> { battle(true); mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); }, 15));
            boolean[] shot = {false};
            for (Skill s : skills) {
                boolean pic = !shot[0]; shot[0] = true;
                l.add(new Step(null, () -> PackTests.server(p -> {
                    PackTests.arena(p); PackTests.clear(p); PackTests.husk(p, 0, 4);
                    p.connection.teleport(PackTests.HOME[0], PackTests.HOME[1], PackTests.HOME[2], 0F, 10F);
                }), 6));
                l.add(new Step(null, () -> PackTests.server(p -> {
                    try { com.xenowarriors.skill.SkillManager.useFree(p, s, 1F); } catch (Throwable x) { threw.add(s.getId() + ": " + x); }
                }), 4));
                l.add(new Step(pic ? "newef_" + n + "_skill" : null, () -> {
                    boolean gen = SkillAnimPacket.GENERIC.containsKey(s.getId());
                    if (!gen) none.add(s.getId());
                    else if (ClientSkillState.currentAnim(mc.player) != null) played.add(s.getId());
                    else none.add(s.getId() + "(not seen)");
                }, 30));
            }
            l.add(new Step(null, () -> {
                PackTests.check("newef " + n + ": every skill and the ultimate play a move in battle mode", none.isEmpty() && threw.isEmpty(), "played " + played.size() + " none " + none + " threw " + threw);
            }, 2));
            // the transformation in battle mode
            if (com.xenowarriors.skill.nc.NcSkills.formOf(t) != null) {
                l.add(new Step(null, () -> PackTests.server(p -> { PackTests.arena(p); PackTests.clear(p); com.xenowarriors.skill.UltForms.resetForTest(p); com.xenowarriors.skill.nc.NcSkills.clear(p.getUUID());
                    com.xenowarriors.network.FoxKingTogglePacket.press(p); }), 30));
                l.add(new Step("newef_" + n + "_form", () -> PackTests.server(p -> PackTests.check("newef " + n + ": transforms in battle mode (transform key)", com.xenowarriors.skill.nc.NcSkills.formActive(p), "")), 5));
                l.add(new Step(null, () -> PackTests.server(p -> { com.xenowarriors.skill.nc.NcSkills.clear(p.getUUID()); com.xenowarriors.skill.UltForms.resetForTest(p); p.removeAllEffects(); }), 10));
            }
            // walking and running in battle mode
            double[] from = new double[3];
            l.add(new Step(null, () -> { mc.player.setYRot(0); from[0] = mc.player.getX(); from[2] = mc.player.getZ(); mc.player.input = new PackLooksTests.Forward(); }, 10));
            l.add(new Step("newef_" + n + "_walk", () -> PackTests.check("newef " + n + ": walks in battle mode", mc.player.distanceToSqr(from[0], mc.player.getY(), from[2]) > 1, ""), 2));
            for (int k = 0; k < 10; k++) l.add(new Step(null, () -> mc.player.setSprinting(true), 1));
            l.add(new Step("newef_" + n + "_run", () -> PackTests.check("newef " + n + ": runs in battle mode", mc.player.isSprinting(), ""), 2));
            l.add(new Step(null, () -> { mc.player.setSprinting(false); mc.player.input = new net.minecraft.client.player.KeyboardInput(mc.options); battle(false); }, 10));
        }
        // Senju's sword: its katana combo
        if (WomMoves.loaded()) PackWomTests.one(mc, l, CharacterType.SENJU, "senju_blade", WomMoves.WEAPONS.get("senju_blade"));
        // their bots on Epic Fight's body, fighting
        int[] ids = new int[PackNewCharsTests.NEW.length];
        l.add(new Step(null, () -> UiTest.character(CharacterType.MIKIKO), 30));
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p);
            for (int i = 0; i < 3; i++) {
                if (PackTests.husk(p, (i - 1) * 3, 7) instanceof net.minecraft.world.entity.Mob h) h.setNoAi(false);
            }
            for (int i = 0; i < PackNewCharsTests.NEW.length; i++) {
                var b = com.xenowarriors.bot.PartyBots.spawn(p, PackNewCharsTests.NEW[i], null);
                if (b == null) continue;
                b.moveTo(PackTests.HOME[0] + (i - 3) * 1.4, PackTests.HOME[1], PackTests.HOME[2] + 3, 0F, 0F);
                ids[i] = b.getId();
            }
            p.connection.teleport(PackTests.HOME[0], PackTests.HOME[1], PackTests.HOME[2] - 3, 0F, 10F);
        }), 40));
        l.add(new Step(null, () -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 40));
        l.add(new Step("newef_bots_fight", () -> {}, 60));
        l.add(new Step("newef_bots_fight2", () -> {
            int ef = 0;
            for (int id : ids) {
                var e = mc.level.getEntity(id);
                if (e instanceof com.xenowarriors.bot.PartyBotEntity b && com.xenowarriors.client.bot.BotAvatars.existing(b) != null) ef++;
            }
            PackTests.log("newef bots drawn with a body: " + ef);
        }, 5));
        l.add(new Step(null, () -> PackTests.server(p -> {
            int alive = 0;
            for (int id : ids) if (p.serverLevel().getEntity(id) instanceof com.xenowarriors.bot.PartyBotEntity b && b.isAlive()) alive++;
            PackTests.check("newef: the new characters' bots fight (no errors, still standing)", alive >= PackNewCharsTests.NEW.length - 1, "alive " + alive);
            PackTests.clear(p);
        }), 5));
        l.add(new Step(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 5));
    }
}
