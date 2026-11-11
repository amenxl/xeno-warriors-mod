package com.xenowarriors.client.test;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientSettings;
import com.xenowarriors.client.PopupFeed;
import com.xenowarriors.client.PreviewPose;
import com.xenowarriors.client.mikiko.FoxAuraClient;
import com.xenowarriors.compat.DmzFusion;
import com.xenowarriors.compat.DmzNews;
import com.xenowarriors.compat.DmzPerCharacter;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Consumer;

/**
 * Focus "dmzuifix": (1) the character select screen's previews don't wear your armor / hold your items / copy your
 * pose, (2) DragonMineZ bot / mission news goes to small see-through pop-up cards (or chat with the setting),
 * (3) fusing with a bot gives exactly DragonMineZ's player + player fusion stats, (4) Mikiko as a Human in Shiyoken /
 * Shin Shiyoken gets the fox aura. Logged as "XW-TEST uifix ...".
 */
public final class UiFixTests {
    private UiFixTests() {}

    private static Dmz6Tests.Adder S;
    private static Consumer<Consumer<ServerPlayer>> SERVER;
    private static Consumer<String> LOG;
    private static Consumer<CharacterType> CHAR;

    public static boolean handles(String focus) { return focus != null && focus.replaceFirst("^dmz", "").startsWith("uifix"); }

    static void log(String s) { LOG.accept("uifix " + s); }
    static void server(Consumer<ServerPlayer> f) { SERVER.accept(f); }
    static void step(String shot, Runnable a, int delay) { S.add(shot, a, delay); }

    /** DragonMineZ's screens, and the select screen the login asked for (it waits until no screen is open). */
    static void closeScreens(Minecraft mc) {
        HuntDmzTests.noDmzScreen(mc);
        if (mc.screen instanceof com.xenowarriors.client.screen.CharacterSelectScreen) mc.setScreen(null);
    }

    static Object call(Object o, String m, Object... args) { return HuntDmzTests.call(o, m, args); }

    public static void build(Minecraft mc, String focus, Dmz6Tests.Adder steps, Consumer<Consumer<ServerPlayer>> server, Consumer<CharacterType> character, Consumer<String> log) {
        S = steps; SERVER = server; LOG = log; CHAR = character;
        boolean dmz = net.minecraftforge.fml.ModList.get().isLoaded("dragonminez");
        step(null, () -> { mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE); mc.getToasts().clear(); mc.setScreen(null);
            CHAR.accept(CharacterType.MIKIKO); }, 60);
        preview(mc);
        if (dmz) {
            step(null, () -> { closeScreens(mc); server(p -> {
                HuntDmzTests.make(p, "saiyan", 40);
                DmzPerCharacter.markCreatedForTest(p);
                HuntDmzTests.protect(p);
                p.serverLevel().setDayTime(6000);
                log("setup dmz=" + dmz);
            }); }, 40);
            news(mc);
            fusion(mc);
            fox(mc);
        } else log("no DragonMineZ: only the preview test ran");
        step(null, () -> log("done"), 20);
    }

    // ---------------------------------------------------------------- 1: the select screen's previews

    private static void preview(Minecraft mc) {
        step(null, () -> server(p -> {
            p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
            p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
            p.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
            p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
            p.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        }), 20);
        step(null, () -> { mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND); mc.setScreen(new com.xenowarriors.client.screen.CharacterSelectScreen()); }, 30);
        step("uifix_select", () -> {}, 3);
        step(null, () -> {
            int worn = 0;
            for (EquipmentSlot s : EquipmentSlot.values()) if (!mc.player.getItemBySlot(s).isEmpty()) worn++;
            log("preview: player wears/holds " + worn + " items; drawn on the previews: " + PreviewPose.lastItemsOn
                    + " last idle=" + PreviewPose.lastIdle + " (expect 6 and 0) -> " + (worn == 6 && PreviewPose.lastItemsOn == 0 ? "PASS" : "FAIL"));
            mc.setScreen(null);
            server(p -> { for (EquipmentSlot s : EquipmentSlot.values()) p.setItemSlot(s, ItemStack.EMPTY); });
        }, 10);
    }

    // ---------------------------------------------------------------- 2: news as small pop-ups

    private static void news(Minecraft mc) {
        int[] before = new int[2];
        step(null, () -> {
            closeScreens(mc);
            ClientSettings.setDmzNewsInChat(false);
            before[0] = PopupFeed.newsCards; before[1] = PopupFeed.newsChat;
        }, 40);
        step(null, () -> server(p -> {
            PartyBotEntity b = HuntDmzTests.ensureBot(p, CharacterType.RHALIX);
            DmzNews.bot(p, b, "Training", Component.literal("Rhalix (bot) trained up to level 12 (spent 4,200 TP)").withStyle(net.minecraft.ChatFormatting.AQUA));
            DmzNews.bot(p, b, "Technique learned", Component.literal("Rhalix (bot) learned Kamehameha from Master Roshi (1,500 TP)"));
            DmzNews.bot(p, b, "Mission complete", Component.literal("Rhalix (bot) completed: Saiyan Saga - Raditz (+2,000 TP)").withStyle(net.minecraft.ChatFormatting.GOLD));
            log("news: sent " + DmzNews.sent + " last=" + DmzNews.last);
        }), 8);
        step("uifix_news", () -> closeScreens(mc), 3);
        step(null, () -> {
            int cards = PopupFeed.newsCards - before[0], chat = PopupFeed.newsChat - before[1];
            log("news: popups=" + cards + " shown=" + PopupFeed.newsShown() + " chat=" + chat + " (expect 3/3/0) -> " + (cards == 3 && chat == 0 ? "PASS" : "FAIL"));
            ClientSettings.setDmzNewsInChat(true);
            server(p -> DmzNews.bot(p, HuntDmzTests.bot(p, CharacterType.RHALIX), "Bot talked", Component.literal("Rhalix (bot) talked to Goku")));
        }, 10);
        step(null, () -> {
            int chat = PopupFeed.newsChat - before[1];
            String lastChat = PopupFeed.lastNewsChat;
            log("news: with the chat setting: chat=" + chat + " last chat line='" + lastChat + "' -> " + (chat == 1 && lastChat.contains("talked to Goku") ? "PASS" : "FAIL"));
            ClientSettings.setDmzNewsInChat(false);
        }, 10);
    }

    // ---------------------------------------------------------------- 3: bot fusion stats

    private static void fusion(Minecraft mc) {
        step(null, () -> server(p -> {
            PartyBotEntity b = HuntDmzTests.ensureBot(p, CharacterType.RHALIX);
            if (b == null) { log("fusion: no bot FAIL"); return; }
            // uneven stats on the bot so every stat's bonus is different (STR 30, SKP 45, RES 60, VIT 75, PWR 90, ENE 105)
            String key = DmzPerCharacter.botKey(b);
            CompoundTag tag = DmzPerCharacter.saved(p, key);
            Object d = DmzPerCharacter.blank(PartyBots.standIn(b));
            if (tag != null && d != null && DmzPerCharacter.loadData(d, tag.copy())) {
                Object st = call(d, "getStats");
                String[] set = {"setStrength", "setStrikePower", "setResistance", "setVitality", "setKiPower", "setEnergy"};
                for (int i = 0; i < set.length; i++) call(st, set[i], 30 + 15 * i);
                DmzPerCharacter.store(p, key, DmzPerCharacter.saveData(d));
            }
            // a stale fusion bonus (as a crash could leave) must not stack on top
            Object me = DmzPerCharacter.live(p);
            call(call(me, "getBonusStats"), "addBonus", "STR", "FusionBonus", "+", 999.0);
            HuntDmzTests.clearFusionCd(p);
            HuntDmzTests.giveEarring(b);
            b.moveTo(p.getX() + 2, p.getY(), p.getZ() + 1, 0, 0);
            PartyBots.action(p, b.getId(), PartyBots.ACT_FUSE, 0);
            boolean f = DmzFusion.fused(p);
            log("fusion: potara fused=" + f + " check: " + DmzFusion.lastCheck + " -> " + (f && DmzFusion.lastCheck.startsWith("OK") ? "PASS" : "FAIL"));
            log("fusion: " + DmzFusion.statsLine(me));
            if (f) DmzFusion.end(p, "test", false);
            Object me2 = DmzPerCharacter.live(p);
            log("fusion: after it ended: " + DmzFusion.statsLine(me2));
        }), 40);
    }

    // ---------------------------------------------------------------- 4: the Shiyoken fox aura

    /** Puts the live DMZ character in this form, with full ki and power release (so DragonMineZ keeps it on). */
    static void form(ServerPlayer p, String race, String form) {
        Object d = DmzPerCharacter.live(p);
        Object ch = call(d, "getCharacter"), res = call(d, "getResources");
        call(ch, "setRace", race);
        if (form == null) call(ch, "clearActiveForm"); else call(ch, "setActiveForm", "legendaryforms", form);
        call(res, "setPowerRelease", 100);
        call(res, "setCurrentEnergy", 1.0E6F);
        DmzPerCharacter.syncAll(p);
    }

    private static void fox(Minecraft mc) {
        boolean[] hid = new boolean[1];
        step(null, () -> server(p -> {
            for (PartyBotEntity b : new java.util.ArrayList<>(PartyBots.bots(p))) { PartyBots.forgetFake(b.getUUID()); b.discard(); }   // (clear view)
            HuntDmzTests.make(p, "human", 40);
            form(p, "human", "shiyoken");
            Object ch = call(DmzPerCharacter.live(p), "getCharacter");
            p.serverLevel().setDayTime(18000);
            p.setYRot(0); p.setXRot(0);
            log("fox: server form=" + call(ch, "getActiveForm") + " race=" + call(ch, "getRaceName"));
        }), 30);
        for (int i = 0; i < 3; i++) step(null, () -> server(p -> form(p, "human", "shiyoken")), 5);
        step(null, () -> {
            closeScreens(mc);
            hid[0] = mc.options.hideGui;
            mc.options.hideGui = true;
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            String fm = com.xenowarriors.client.compat.DmzForms.form(mc.player), race = com.xenowarriors.client.compat.DmzForms.race(mc.player);
            int lvl = FoxAuraClient.level(mc.player);
            log("fox: client form=" + fm + " race=" + race + " level=" + lvl + " (expect 1) -> " + (lvl == 1 ? "PASS" : "FAIL"));
            if (lvl != 1) FoxAuraClient.forceLevel = 1;           // (DragonMineZ undid the form: still show the look)
        }, 20);
        step("uifix_fox1", () -> closeScreens(mc), 3);
        step(null, () -> {
            log("fox: level 1 drew auras=" + FoxAuraClient.drawnAuras + " foxes=" + FoxAuraClient.drawnFoxes + " orbs=" + FoxAuraClient.drawnOrbs
                    + " heads=" + FoxAuraClient.drawnHeads + " trim=" + FoxAuraClient.trimDrawn + " -> " + (FoxAuraClient.drawnFoxes == 4 && FoxAuraClient.drawnOrbs == 0 ? "PASS" : "FAIL"));
            FoxAuraClient.forceLevel = -1;
            server(p -> form(p, "human", "shin_shiyoken"));
        }, 5);
        for (int i = 0; i < 3; i++) step(null, () -> server(p -> form(p, "human", "shin_shiyoken")), 5);
        step(null, () -> {
            closeScreens(mc);
            int lvl = FoxAuraClient.level(mc.player);
            log("fox: shin shiyoken level=" + lvl + " (expect 2) -> " + (lvl == 2 ? "PASS" : "FAIL"));
            if (lvl != 2) FoxAuraClient.forceLevel = 2;
        }, 20);
        step("uifix_fox2", () -> closeScreens(mc), 3);
        step(null, () -> {
            log("fox: level 2 drew foxes=" + FoxAuraClient.drawnFoxes + " orbs=" + FoxAuraClient.drawnOrbs + " heads=" + FoxAuraClient.drawnHeads
                    + " -> " + (FoxAuraClient.drawnFoxes == 7 && FoxAuraClient.drawnOrbs == 5 && FoxAuraClient.drawnHeads == 1 ? "PASS" : "FAIL"));
            FoxAuraClient.forceLevel = -1;
            // not Human: no fox aura
            server(p -> form(p, "saiyan", "shin_shiyoken"));
        }, 5);
        for (int i = 0; i < 3; i++) step(null, () -> server(p -> form(p, "saiyan", "shin_shiyoken")), 5);
        step(null, () -> {
            int lvl = FoxAuraClient.level(mc.player);
            log("fox: as a Saiyan level=" + lvl + " (expect 0) -> " + (lvl == 0 ? "PASS" : "FAIL"));
            long t0 = System.nanoTime();
            for (int i = 0; i < 1000; i++) FoxAuraClient.level(mc.player);
            log("fox: level() cost " + (System.nanoTime() - t0) / 1000 + " ns per call");
            mc.options.hideGui = hid[0];
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            server(p -> form(p, "human", null));
        }, 20);
    }
}
