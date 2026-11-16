package com.xenowarriors.client.test;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.client.compat.epicfight.EfBotBodies;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * (Pack test, focus "packefbots") Bots on Epic Fight's body (the user's video: bot Kyrie in a plain skin with parts
 * missing, the bot menu showing only wings and legs). A bot of each look stands in front, out of and in Epic Fight's
 * body, still and mid-swing, and in its bot menu.
 */
final class PackEfBotTests {
    private PackEfBotTests() {}

    private static PartyBotEntity bot(net.minecraft.server.level.ServerPlayer p, CharacterType t) {
        for (PartyBotEntity b : PartyBots.bots(p)) if (b.character() == t) return b;
        return null;
    }

    private static PartyBotEntity clientBot(Minecraft mc, CharacterType t) {
        if (mc.level == null) return null;
        for (var e : mc.level.entitiesForRendering()) if (e instanceof PartyBotEntity b && b.character() == t) return b;
        return null;
    }

    static void build(Minecraft mc, List<Step> l) {
        for (CharacterType t : new CharacterType[]{CharacterType.KYRIE, CharacterType.RHALIX, CharacterType.MIKIKO}) one(mc, l, t);
        // a real fight: the bot (on Epic Fight's body) goes for a zombie - it still lands its hits, and its swings play Epic Fight's combo
        int[] z = {-1}; float[] hp = {0}; int[] sw = {0};
        int[] ch = {0};
        l.add(new Step(null, () -> { mc.setScreen(null); EfBotBodies.off = false; sw[0] = EfBotBodies.swings; EfBotBodies.PLAYED.clear(); ch[0] = com.xenowarriors.combat.BotCombo.chained; PackTests.server(p -> {
            PartyBotEntity b = bot(p, CharacterType.MIKIKO);
            if (b == null) return;
            b.setNoAi(false);
            var zz = net.minecraft.world.entity.EntityType.ZOMBIE.create(p.serverLevel());
            zz.moveTo(b.getX() + 3, b.getY(), b.getZ(), 0, 0);
            zz.addTag("xw_pack"); zz.setPersistenceRequired();
            zz.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200); zz.setHealth(200);
            p.serverLevel().addFreshEntity(zz);
            z[0] = zz.getId(); hp[0] = zz.getHealth();
            b.setTarget(zz);
        }); }, 40));
        for (int i = 0; i < 14; i++) {
            int ii = i;
            l.add(new Step(i == 0 || i % 2 == 1 ? null : "pack_efbots_fight_" + ii, () -> {
                var b = clientBot(mc, CharacterType.MIKIKO);
                if (b == null || mc.player == null) return;
                mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON);
                var d = b.position().subtract(mc.player.position());
                mc.player.setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z))); mc.player.setXRot(10F);
                var a = com.xenowarriors.client.bot.BotAvatars.existing(b);
                PackTests.log("efbots fight " + ii + ": swinging=" + b.swinging + " combo=" + com.xenowarriors.client.combo.ComboClient.swing(b)
                        + " posed=" + com.xenowarriors.client.EntityPoses.posed(b) + "/" + (a != null && com.xenowarriors.client.EntityPoses.posed(a))
                        + " draw " + (a == null ? "no avatar" : com.xenowarriors.client.compat.epicfight.EpicFightCompat.debugDraw(a)) + " comboHits " + EfBotBodies.comboHits);
            }, 6));
        }
        l.add(new Step("pack_efbots_fight", () -> PackTests.server(p -> {
            var e = p.serverLevel().getEntity(z[0]);
            float now = e instanceof net.minecraft.world.entity.LivingEntity le ? le.getHealth() : -1;
            PackTests.check("efbots: a bot on Epic Fight's body lands its hits, with Epic Fight's combo", (e == null || now < hp[0]) && EfBotBodies.swings - sw[0] >= 3,
                    "zombie hp " + hp[0] + " -> " + now + ", Epic Fight swings played " + (EfBotBodies.swings - sw[0]) + " (combo hits " + EfBotBodies.comboHits + ")");
            PackTests.check("efbots: the bot chains its whole combo (not one hit, stop, again)", EfBotBodies.PLAYED.size() >= 3 && com.xenowarriors.combat.BotCombo.chained - ch[0] >= 4,
                    "combo hits played " + EfBotBodies.PLAYED + ", follow-up hits " + (com.xenowarriors.combat.BotCombo.chained - ch[0]) + ", strings carried on after knockback " + com.xenowarriors.combat.BotCombo.resumed);
            if (e != null) e.discard();
        }), 3));
        l.add(new Step(null, () -> PackTests.check("efbots: bots started on Epic Fight's body and swung its combos",
                EfBotBodies.started > 0 && EfBotBodies.swings > 0, "started " + EfBotBodies.started + " swings " + EfBotBodies.swings + " last error " + EfBotBodies.lastError), 2));
        l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 5));
    }

    private static void one(Minecraft mc, List<Step> l, CharacterType t) {
        String n = t.getId();
        l.add(new Step(null, () -> { mc.setScreen(null); UiTest.character(CharacterType.MIKIKO == t ? CharacterType.KYRIE : CharacterType.MIKIKO); EfBotBodies.off = true; }, 20));
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            for (PartyBotEntity b : PartyBots.bots(p)) if (b.character() != t) b.discard();
            p.getPersistentData().remove("xw_bot_rejoin_" + n);
            PartyBotEntity b = bot(p, t);
            if (b == null) b = PartyBots.spawn(p, t, null);
            if (b == null) { PackTests.check("efbots " + n + " spawn", false, "no bot"); return; }
            b.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            b.setNoAi(true);
            var front = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(2.5));
            b.moveTo(front.x, p.getY(), front.z, p.getYRot() + 180F, 0F);
            b.setYHeadRot(p.getYRot() + 180F); b.yBodyRot = p.getYRot() + 180F;
        }), 30));
        l.add(new Step(null, () -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 10));
        l.add(new Step("pack_efbots_" + n + "_plain", () -> {}, 3));
        l.add(new Step(null, () -> EfBotBodies.off = false, 25));
        l.add(new Step("pack_efbots_" + n + "_ef", () -> {
            var b = clientBot(mc, t);
            var a = b == null ? null : com.xenowarriors.client.bot.BotAvatars.existing(b);
            PackTests.log("efbots " + n + ": bot " + b + " avatar " + a + (a == null ? "" : " skin " + a.getSkinTextureLocation() + " model " + a.getModelName()
                    + " draw " + com.xenowarriors.client.compat.epicfight.EpicFightCompat.debugDraw(a)) + " started " + EfBotBodies.started + " err " + EfBotBodies.lastError);
        }, 3));
        l.add(new Step(null, () -> { var b = clientBot(mc, t); if (b != null) b.swing(InteractionHand.MAIN_HAND); }, 4));
        l.add(new Step("pack_efbots_" + n + "_swing", () -> {}, 12));
        l.add(new Step(null, () -> { var b = clientBot(mc, t); if (b != null) mc.setScreen(new com.xenowarriors.client.bot.BotScreen(
                new com.xenowarriors.network.OpenBotMenuPacket(b.getId(), n, 0, 30, false, new String[0], new int[0]))); }, 10));
        l.add(new Step("pack_efbots_" + n + "_menu", () -> {}, 3));
        l.add(new Step(null, () -> { mc.setScreen(null); EfBotBodies.off = true; }, 5));
        l.add(new Step(null, () -> { var b = clientBot(mc, t); if (b != null) mc.setScreen(new com.xenowarriors.client.bot.BotScreen(
                new com.xenowarriors.network.OpenBotMenuPacket(b.getId(), n, 0, 30, false, new String[0], new int[0]))); }, 10));
        l.add(new Step("pack_efbots_" + n + "_menu_plain", () -> {}, 3));
        l.add(new Step(null, () -> mc.setScreen(null), 3));
    }
}
