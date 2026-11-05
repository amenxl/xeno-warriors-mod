package com.xenowarriors.client.test;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.BotCrafting;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.KarmaSense;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;

/**
 * UI tests for "bots craft on their own / only ask for what you have" and Karma Sense's strength-sized auras.
 * Focus "sense" (all), "sense_craft", "sense_aura"; "dmzsense" runs the aura part with DragonMineZ loaded.
 * Every result is an "XW-TEST sense ..." line.
 */
public final class SenseTests {
    private SenseTests() {}

    private static int X, Y, Z;
    private static final String TAG = "xw_sense";
    private static final int[] IDS = {-1, -1, -1};

    static void log(String s) { XenoWarriors.LOGGER.info("XW-TEST sense " + s); }

    public static boolean handles(String focus) { return focus != null && focus.replaceFirst("^dmz", "").startsWith("sense"); }

    private static String f1(double d) { return String.format(Locale.ROOT, "%.1f", d); }
    private static String id(ItemStack s) { return s.isEmpty() ? "empty" : net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem()).getPath(); }

    private static void fill(ServerLevel l, int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        for (BlockPos q : BlockPos.betweenClosed(Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1))) l.setBlock(q, s, 2);
    }

    private static void clearAll(ServerPlayer p) {
        for (PartyBotEntity b : p.serverLevel().getEntitiesOfClass(PartyBotEntity.class, p.getBoundingBox().inflate(80))) { PartyBots.forgetFake(b.getUUID()); b.discard(); }
        for (PartyBotEntity b : PartyBots.bots(p)) { PartyBots.forgetFake(b.getUUID()); b.discard(); }
        p.serverLevel().getEntitiesOfClass(Entity.class, p.getBoundingBox().inflate(80), e -> e.getTags().contains(TAG) || e instanceof ItemEntity).forEach(Entity::discard);
        for (String c : new String[]{"kaeru", "rhalix", "ultima", "yuri", "mikiko"}) p.getPersistentData().remove("xw_bot_rejoin_" + c);
    }

    private static void arena(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        X = p.getBlockX(); Z = p.getBlockZ(); Y = Math.min(lv.getMaxBuildHeight() - 40, p.getBlockY() + 40);
        fill(lv, X - 30, Y, Z - 30, X + 30, Y + 22, Z + 30, Blocks.AIR.defaultBlockState());
        fill(lv, X - 30, Y - 1, Z - 30, X + 30, Y - 1, Z + 30, Blocks.SMOOTH_STONE.defaultBlockState());
        p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        p.getAbilities().flying = false; p.onUpdateAbilities();
        p.teleportTo(lv, X + 0.5, Y, Z + 0.5, 0, 10);
        lv.setDayTime(6000);
        lv.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING).set(false, p.server);
        clearAll(p);
        log("arena at " + X + " " + Y + " " + Z);
    }

    private static PartyBotEntity bot(ServerPlayer p, CharacterType t) {
        for (PartyBotEntity b : PartyBots.bots(p)) if (b.character() == t) return b;
        return null;
    }

    private static PartyBotEntity spawnBot(ServerPlayer p, CharacterType t, double x, double z) {
        PartyBotEntity b = bot(p, t);
        if (b == null) b = PartyBots.spawn(p, t, null);
        if (b == null) { log("spawn " + t.getId() + " FAILED"); return null; }
        b.inventory.clearContent();
        for (EquipmentSlot es : EquipmentSlot.values()) b.setItemSlot(es, ItemStack.EMPTY);
        b.moveTo(x, Y, z, 180, 0);
        b.setTarget(null);
        b.setMode(PartyBotEntity.NEUTRAL);
        var hp = b.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(200);
        b.setHealth(b.getMaxHealth());
        b.lastHurtAt = -100000;
        BotCrafting.clearQuiet(b);
        return b;
    }

    private static void noDmzScreen(Minecraft mc) {
        if (mc.screen != null && mc.screen.getClass().getName().startsWith("com.dragonminez")) mc.setScreen(null);
    }

    public static void build(Minecraft mc, List<Step> l, String focus) {
        String f = focus.replaceFirst("^dmz", "");
        boolean all = f.equals("sense");
        l.add(new Step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null); mc.options.hideGui = false; UiTest.character(CharacterType.MIKIKO); UiTest.hold(null);
            UiTest.server(SenseTests::arena); }, 40));
        // (DragonMineZ: its character creator would sit on top of everything - count the character as made)
        l.add(new Step(null, () -> { UiTest.server(com.xenowarriors.compat.DmzPerCharacter::markCreatedForTest); noDmzScreen(mc); }, 20));
        l.add(new Step(null, () -> noDmzScreen(mc), 5));
        if (all || f.equals("sense_craft")) craft(mc, l);
        if (all || f.equals("sense_aura")) aura(mc, l, focus.startsWith("dmz"));
        l.add(new Step(null, () -> UiTest.server(p -> { clearAll(p); log("done"); }), 10));
    }

    // ---------------------------------------------------------------- bots craft without asking; ask only for what you have

    private static void craft(Minecraft mc, List<Step> l) {
        final int[] req0 = new int[1];
        l.add(new Step(null, () -> UiTest.server(p -> {
            clearAll(p);
            p.getInventory().clearContent();
            p.teleportTo(p.serverLevel(), X + 0.5, Y, Z + 0.5, 0, 15);
            PartyBotEntity b = spawnBot(p, CharacterType.KAERU, X + 1.5, Z + 3.5);
            if (b == null) return;
            b.inventory.addItem(new ItemStack(Items.IRON_INGOT, 8)); b.inventory.addItem(new ItemStack(Items.CRAFTING_TABLE));
            b.autoCrafted = 0;
            req0[0] = BotCrafting.requests;
            log("craft setup: 8 iron + a crafting table in Kaeru's bag, nothing worn, danger=" + com.xenowarriors.bot.BotErrands.danger(b));
        }), 240));
        l.add(new Step("sense_craft_auto", () -> {}, 3));
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity b = bot(p, CharacterType.KAERU);
            if (b == null) { log("craft auto: NO BOT"); return; }
            int asked = BotCrafting.requests - req0[0];
            boolean pass = b.autoCrafted > 0 && BotCrafting.offerToken(b) == 0 && asked == 0;
            log("craft auto (12 s, no button pressed): autoCrafted=" + b.autoCrafted + " chest=" + id(b.getItemBySlot(EquipmentSlot.CHEST)) + " head=" + id(b.getItemBySlot(EquipmentSlot.HEAD))
                    + " iron left=" + b.inventory.countItem(Items.IRON_INGOT) + " open offer token=" + BotCrafting.offerToken(b) + " requests sent=" + asked + " -> " + (pass ? "PASS" : "FAIL"));

            // one short of a diamond sword (has 1 diamond + a stick), and the owner has no diamond: no request
            BotCrafting.clearQuiet(b);
            b.inventory.clearContent();
            for (EquipmentSlot es : EquipmentSlot.values()) b.setItemSlot(es, ItemStack.EMPTY);
            b.inventory.addItem(new ItemStack(Items.DIAMOND)); b.inventory.addItem(new ItemStack(Items.STICK)); b.inventory.addItem(new ItemStack(Items.CRAFTING_TABLE));
            p.getInventory().clearContent();
            p.getInventory().add(new ItemStack(Items.BREAD, 4));
            StringBuilder sb = new StringBuilder();
            for (var i : BotCrafting.ideas(b)) sb.append(id(i.result)).append(i.missing > 0 ? "(-" + i.missing + " " + id(i.missingItem) + ")" : "").append(' ');
            int r1 = BotCrafting.requests;
            var none = BotCrafting.suggest(b, p, true);
            boolean pass2 = none == null && BotCrafting.requests == r1 && BotCrafting.offerToken(b) == 0;
            log("craft lacks: ideas " + sb.toString().trim() + " | owner has a diamond=" + p.getInventory().countItem(Items.DIAMOND) + " -> acted on=" + (none == null ? "nothing" : none.kind + ":" + id(none.result))
                    + " requests sent=" + (BotCrafting.requests - r1) + " -> " + (pass2 ? "PASS (no request)" : "FAIL"));

            // the owner picks up diamonds: now it asks
            BotCrafting.clearQuiet(b);
            p.getInventory().add(new ItemStack(Items.DIAMOND, 3));
            int r2 = BotCrafting.requests;
            var ask = BotCrafting.suggest(b, p, true);
            boolean pass3 = ask != null && ask.missing == 1 && BotCrafting.requests == r2 + 1 && BotCrafting.offerToken(b) > 0;
            log("craft has: owner has diamonds=" + p.getInventory().countItem(Items.DIAMOND) + " -> request=" + (ask == null ? "none" : ask.kind + ":" + id(ask.result) + " for " + id(ask.missingItem))
                    + " token=" + BotCrafting.offerToken(b) + " requests sent=" + (BotCrafting.requests - r2) + " -> " + (pass3 ? "PASS (asked)" : "FAIL"));
        }), 10));
        l.add(new Step(null, () -> mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen("")), 10));
        l.add(new Step("sense_craft_request", () -> {}, 3));
        l.add(new Step(null, () -> mc.setScreen(null), 3));
        // answered on the request card with the Accept key (Y)
        l.add(new Step(null, () -> net.minecraft.client.KeyMapping.click(com.xenowarriors.client.ModKeyMappings.ACCEPT_REQUEST.getKey()), 15));
        // (another mod may hold the same key in KeyMapping's map - e.g. with DragonMineZ - then the chat button's command instead)
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity b = bot(p, CharacterType.KAERU);
            if (b == null || BotCrafting.offerToken(b) == 0) { log("craft request answered with the Y key"); return; }
            log("craft request: Y key didn't reach the card here - using the [Give 1 & craft] button's command");
            p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), "xenobot give " + b.getId() + " " + BotCrafting.offerToken(b));
        }), 10));
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity b = bot(p, CharacterType.KAERU);
            if (b == null) return;
            boolean sword = b.getMainHandItem().is(Items.DIAMOND_SWORD) || b.inventory.countItem(Items.DIAMOND_SWORD) > 0;
            log("craft after [Give 1]: owner diamonds=" + p.getInventory().countItem(Items.DIAMOND) + " bot has diamond sword=" + sword + " -> " + (sword ? "PASS" : "FAIL"));
        }), 5));
    }

    // ---------------------------------------------------------------- Karma Sense auras: weak vs strong

    private static <T extends Mob> T mob(ServerPlayer p, EntityType<T> type, double x, double z) {
        T m = type.create(p.serverLevel());
        if (m == null) return null;
        m.moveTo(x, Y, z, 180, 0);
        m.setYHeadRot(180);
        m.setNoAi(true);
        m.setPersistenceRequired();
        m.addTag(TAG);
        p.serverLevel().addFreshEntity(m);
        return m;
    }

    private static void aura(Minecraft mc, List<Step> l, boolean dmz) {
        l.add(new Step(null, () -> { noDmzScreen(mc); mc.options.setCameraType(CameraType.FIRST_PERSON); UiTest.noToasts(mc); mc.gui.getChat().clearMessages(false); UiTest.server(p -> {
            clearAll(p);
            p.getInventory().clearContent();
            ServerLevel lv = p.serverLevel();
            p.teleportTo(lv, X + 0.5, Y, Z + 0.5, 8, -14);
            // a wall in front of the chicken: it's sensed through it
            fill(lv, X - 6, Y, Z + 5, X - 2, Y + 2, Z + 5, Blocks.STONE_BRICKS.defaultBlockState());
            var c = mob(p, EntityType.CHICKEN, X - 3.5, Z + 7.5);
            var z = mob(p, EntityType.ZOMBIE, X + 0.5, Z + 9.5);
            var w = mob(p, EntityType.WITHER, X + 10.5, Z + 24.5);
            IDS[0] = c == null ? -1 : c.getId(); IDS[1] = z == null ? -1 : z.getId(); IDS[2] = w == null ? -1 : w.getId();
            log("aura setup: chicken (behind a wall), zombie, Wither (boss); you=" + f1(com.xenowarriors.karma.KarmaSensing.strength(p))
                    + " chicken=" + (c == null ? "-" : f1(com.xenowarriors.karma.KarmaSensing.strength(c))) + " zombie=" + (z == null ? "-" : f1(com.xenowarriors.karma.KarmaSensing.strength(z)))
                    + " wither=" + (w == null ? "-" : f1(com.xenowarriors.karma.KarmaSensing.strength(w)) + " boss=" + com.xenowarriors.karma.KarmaSensing.boss(w)));
        }); }, 30));
        l.add(new Step(null, () -> UiTest.server(p -> {
            com.xenowarriors.karma.Karma.add(p, 100F);
            com.xenowarriors.karma.KarmaSensing.use(p, true);
            com.xenowarriors.karma.KarmaSensing.release(p);
        }), 30));
        // before: the old look (outline + sparks only)
        l.add(new Step(null, () -> { KarmaSense.aurasOff = true; noDmzScreen(mc); UiTest.noToasts(mc); mc.gui.getChat().clearMessages(false); }, 5));
        l.add(new Step("sense_aura_before" + (dmz ? "_dmz" : ""), () -> {}, 3));
        l.add(new Step(null, () -> KarmaSense.aurasOff = false, 4));
        l.add(new Step("sense_aura_after" + (dmz ? "_dmz" : ""), () -> {}, 3));
        l.add(new Step(null, () -> {
            String[] names = {"chicken", "zombie", "wither"};
            float[][] sz = new float[3][];
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 3; i++) {
                Entity e = mc.level == null ? null : mc.level.getEntity(IDS[i]);
                if (e == null) { sb.append(names[i]).append("=missing "); continue; }
                sz[i] = KarmaSense.auraSize(e);
                sb.append(names[i]).append(": sensed=").append(KarmaSense.isSensed(e)).append(" rel=").append(f1(KarmaSense.relative(e))).append(" boss=").append(KarmaSense.boss(e))
                        .append(" radius=").append(f1(sz[i][0])).append(" height=").append(f1(sz[i][1])).append(" t=").append(String.format(Locale.ROOT, "%.2f", sz[i][2])).append(" | ");
            }
            log("aura sizes: " + sb + "drawn last frame=" + KarmaSense.aurasDrawn);
            boolean pass = sz[0] != null && sz[2] != null && sz[2][1] > sz[0][1] * 2.5F && sz[2][0] > sz[0][0] * 3F && (sz[1] == null || sz[2][1] > sz[1][1]) && KarmaSense.aurasDrawn >= 3;
            log("aura weak vs strong: wither/chicken height x" + (sz[0] == null || sz[2] == null ? "?" : f1(sz[2][1] / sz[0][1])) + " radius x" + (sz[0] == null || sz[2] == null ? "?" : f1(sz[2][0] / sz[0][0]))
                    + " -> " + (pass ? "PASS" : "FAIL"));
        }, 3));
        // third person, from behind and above: the scale of it
        l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); UiTest.server(p -> p.teleportTo(p.serverLevel(), X - 2.5, Y + 1, Z - 9.5, 12, -6)); }, 8));
        l.add(new Step("sense_aura_after_wide" + (dmz ? "_dmz" : ""), () -> {}, 3));
        // at night
        l.add(new Step(null, () -> UiTest.server(p -> p.serverLevel().setDayTime(18000)), 10));
        l.add(new Step("sense_aura_after_night" + (dmz ? "_dmz" : ""), () -> {}, 3));
        l.add(new Step(null, () -> KarmaSense.aurasOff = true, 4));
        l.add(new Step("sense_aura_before_night" + (dmz ? "_dmz" : ""), () -> {}, 3));
        l.add(new Step(null, () -> { KarmaSense.aurasOff = false; mc.options.setCameraType(CameraType.FIRST_PERSON); UiTest.server(p -> p.serverLevel().setDayTime(6000)); }, 3));
    }
}
