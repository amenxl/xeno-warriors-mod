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
