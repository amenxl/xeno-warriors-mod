package com.xenowarriors.client.test;

import com.xenowarriors.bot.BotBags;
import com.xenowarriors.bot.BotInventory;
import com.xenowarriors.bot.BotMenu;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterInventories;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.client.bot.BotInventoryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.io.File;
import java.util.*;

/**
 * Bots' extra inventory tabs, tidying, and DragonMineZ item missions counting bots' things (focus "botbag", and
 * "dmzbotbag" with DragonMineZ for the mission part). Each case logs "XW-TEST botbag ... -> PASS/FAIL".
 */
public final class BotBagTests {
    private BotBagTests() {}

    public static boolean handles(String focus) { return focus != null && focus.replaceFirst("^dmz", "").startsWith("botbag"); }

    private static int pass, fail;

    private static void log(String s) { UiTest.log("botbag " + s); }

    private static void check(String what, boolean ok, String detail) {
        if (ok) pass++; else fail++;
        log(what + ": " + detail + " -> " + (ok ? "PASS" : "FAIL"));
    }

    private static PartyBotEntity bot(ServerPlayer p, CharacterType t) {
        for (PartyBotEntity b : PartyBots.bots(p)) if (b.character() == t) return b;
        return null;
    }

    private static int bots(ServerPlayer p, CharacterType t) { int n = 0; for (PartyBotEntity b : PartyBots.bots(p)) if (b.character() == t) n++; return n; }

    private static int range(PartyBotEntity b, Item it, int from, int to) {
        int n = 0;
        for (int i = from; i < to; i++) if (b.inventory.getItem(i).is(it)) n += b.inventory.getItem(i).getCount();
        return n;
    }

    private static int extra(PartyBotEntity b, Item it) { return b == null ? -1 : range(b, it, BotInventory.BAG, BotInventory.SIZE); }
    private static int bag(PartyBotEntity b, Item it) { return b == null ? -1 : range(b, it, 0, BotInventory.BAG); }
    private static int onPlayer(ServerPlayer p, Item it) { return p.getInventory().countItem(it); }

    // every copy the player owns anywhere (on them, their saves, live bots) - the no-dupe/no-loss check
    private static final Set<String> SNAPSHOT_KEYS = Set.of("xw_bots", "xw_bots_live");

    private static int walk(Tag t, String id, boolean skipSnapshots) {
        int n = 0;
        if (t instanceof CompoundTag c) {
            if (id.equals(c.getString("id")) && c.contains("Count")) n += c.getByte("Count");
            for (String k : c.getAllKeys()) if (!skipSnapshots || !SNAPSHOT_KEYS.contains(k)) n += walk(c.get(k), id, skipSnapshots);
        } else if (t instanceof ListTag l) for (Tag e : l) n += walk(e, id, skipSnapshots);
        return n;
    }

    private static int owned(ServerPlayer p, String id) {
        int n = walk(p.getInventory().save(new ListTag()), id, true) + walk(p.getEnderChestInventory().createTag(), id, true) + walk(p.getPersistentData(), id, true);
        for (PartyBotEntity b : PartyBots.bots(p)) n += walk(b.record(), id, true);
        return n;
    }

    private static String owned(ServerPlayer p) {
        return "diamond=" + owned(p, "minecraft:diamond") + " emerald=" + owned(p, "minecraft:emerald") + " gold=" + owned(p, "minecraft:gold_ingot");
    }

    private static void clear(ServerPlayer p) {
        for (PartyBotEntity b : new ArrayList<>(PartyBots.bots(p))) { PartyBots.checkIn(b, p); PartyBots.forgetFake(b.getUUID()); b.discard(); }
        for (CharacterType t : CharacterType.PLAYABLE) {
            p.getPersistentData().remove("xw_bot_rejoin_" + t.getId());
            if (t != CharacterType.MIKIKO) CharacterInventories.checkOut(p, t.getId());   // (a clean start for the bots' own saves)
            BotBags.take(p, t.getId());
        }
        p.getPersistentData().remove("xw_bots");
        for (Item it : new Item[]{Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT, Items.STICK, Items.APPLE})
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(it)) p.getInventory().setItem(i, ItemStack.EMPTY);
    }

    private static PartyBotEntity fresh(ServerPlayer p, CharacterType t) {
        p.getPersistentData().remove("xw_bot_rejoin_" + t.getId());
        PartyBotEntity b = PartyBots.spawn(p, t, null);
        if (b != null) b.moveTo(p.getX() + 2, p.getY(), p.getZ() + 2, 0, 0);
        return b;
    }

    // ------------------------------------------------------------------ a crash (as BotSaveTests)

    private static CompoundTag saveAndReadBack(ServerPlayer p) {
        p.server.saveEverything(true, false, true);
        try {
            File level = p.server.getWorldPath(LevelResource.LEVEL_DATA_FILE).toFile();
            CompoundTag host = level.exists() ? NbtIo.readCompressed(level).getCompound("Data").getCompound("Player") : new CompoundTag();
            if (!host.isEmpty() && p.server.isSingleplayerOwner(p.getGameProfile())) return host;
            File own = p.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(p.getStringUUID() + ".dat").toFile();
            if (own.exists()) return NbtIo.readCompressed(own);
        } catch (Exception e) { log("crash FAIL reading the save: " + e); }
        return null;
    }

    private static void crashAndRestart(ServerPlayer p, CompoundTag disk) {
        if (disk == null) { check("crash", false, "nothing on disk"); return; }
        for (PartyBotEntity b : new ArrayList<>(PartyBots.bots(p))) { PartyBots.forgetFake(b.getUUID()); b.discard(); }
        PartyBots.resetForCrashTest(p);
        p.load(disk.copy());
        PartyBots.onLogin(new PlayerEvent.PlayerLoggedInEvent(p));
    }

    // ------------------------------------------------------------------ the test

    private static final int[] N = new int[3];
    private static final Object[] HOLD = new Object[4];

    /** Rhalix's extra tabs as expected: 20 diamonds (tab 2), 5 emeralds (tab 3), 7 gold (put in by shift-click) - and nothing extra anywhere. */
    private static boolean extrasOk(ServerPlayer p, PartyBotEntity r) {
        return r != null && extra(r, Items.DIAMOND) == 20 && extra(r, Items.EMERALD) == 5 && extra(r, Items.GOLD_INGOT) == 7
                && owned(p, "minecraft:diamond") == N[0] && owned(p, "minecraft:emerald") == N[1] && owned(p, "minecraft:gold_ingot") == N[2];
    }

    private static String extras(PartyBotEntity r) {
        return r == null ? "no bot" : "extra diamond=" + extra(r, Items.DIAMOND) + " emerald=" + extra(r, Items.EMERALD) + " gold=" + extra(r, Items.GOLD_INGOT)
                + " | bag apple=" + bag(r, Items.APPLE) + " stick=" + bag(r, Items.STICK);
    }

    private static BotInventoryScreen screen(Minecraft mc) { return mc.screen instanceof BotInventoryScreen s ? s : null; }

    public static void build(Minecraft mc, List<Step> l, String focus) {
        boolean dmz = focus.startsWith("dmz");
        l.add(new Step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null); mc.options.hideGui = false; UiTest.character(CharacterType.MIKIKO);
            UiTest.server(p -> { p.setGameMode(GameType.SURVIVAL); p.setHealth(p.getMaxHealth()); BotBags.AUTO = false; clear(p); }); }, 40));
        l.add(new Step(null, () -> UiTest.server(p -> { fresh(p, CharacterType.RHALIX); p.getInventory().setItem(9, new ItemStack(Items.GOLD_INGOT, 7)); }), 40));

        // ---- 1. the tabs: only on a bot's inventory screen; shift-click into the tab shown; hidden slots can't be used
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity r = bot(p, CharacterType.RHALIX);
            if (r == null) { check("tabs", false, "no bot"); return; }
            r.inventory.clearContent();
            r.inventory.setItem(BotInventory.tabStart(2), new ItemStack(Items.DIAMOND, 20));
            r.inventory.setItem(BotInventory.tabStart(3) + 4, new ItemStack(Items.EMERALD, 5));
            r.inventory.setItem(0, new ItemStack(Items.APPLE, 3));
            r.inventory.setItem(30, new ItemStack(Items.STICK, 1));
            PartyBots.openBag(p, r);
        }), 20));
        l.add(new Step("botbag_tab_bag", () -> {
            BotInventoryScreen s = screen(mc);
            check("tabs shown", s != null && s.getMenu().hasTabs() && s.getMenu().tab() == 0,
                    "screen=" + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName()) + (s == null ? "" : " hasTabs=" + s.getMenu().hasTabs() + " tab=" + s.getMenu().tab()));
            check("player's own inventory has no extra tabs", mc.player.inventoryMenu.slots.size() == 46, "inventory menu slots=" + mc.player.inventoryMenu.slots.size());
        }, 5));
        l.add(new Step(null, () -> { BotInventoryScreen s = screen(mc); if (s != null) s.press(2); }, 10));
        l.add(new Step("botbag_tab_extra2", () -> {
            BotInventoryScreen s = screen(mc);
            if (s == null) { check("tab 2", false, "screen gone"); return; }
            BotMenu m = s.getMenu();
            Slot first = m.slots.get(BotMenu.GEAR), hidden = m.slots.get(BotMenu.GEAR + 30);
            check("tab 2 shows extra tab 2", m.tab() == 2 && first.getItem().is(Items.DIAMOND) && first.getItem().getCount() == 20 && !hidden.isActive(),
                    "tab=" + m.tab() + " first slot=" + first.getItem() + " 4th row active=" + hidden.isActive());
            int from = -1;
            for (Slot sl : m.slots) if (sl.container == mc.player.getInventory() && sl.getContainerSlot() == 9) from = sl.index;
            mc.gameMode.handleInventoryMouseClick(m.containerId, from, 0, ClickType.QUICK_MOVE, mc.player);    // (shift-click your 7 gold in)
            mc.gameMode.handleInventoryMouseClick(m.containerId, BotMenu.GEAR + 30, 0, ClickType.PICKUP, mc.player);   // (a hidden slot: nothing)
        }, 10));
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity r = bot(p, CharacterType.RHALIX);
            check("shift-click into the tab shown", r != null && range(r, Items.GOLD_INGOT, BotInventory.tabStart(2), BotInventory.tabStart(3)) == 7 && onPlayer(p, Items.GOLD_INGOT) == 0,
                    r == null ? "no bot" : "gold in tab 2=" + range(r, Items.GOLD_INGOT, BotInventory.tabStart(2), BotInventory.tabStart(3)) + " on you=" + onPlayer(p, Items.GOLD_INGOT));
            check("hidden slot untouched", r != null && r.inventory.getItem(30).is(Items.STICK) && p.containerMenu.getCarried().isEmpty(),
                    r == null ? "" : "bag slot 30=" + r.inventory.getItem(30) + " carried=" + p.containerMenu.getCarried());
            p.closeContainer();
            N[0] = owned(p, "minecraft:diamond"); N[1] = owned(p, "minecraft:emerald"); N[2] = owned(p, "minecraft:gold_ingot");
            log("owned now " + owned(p) + " | rhalix " + extras(r));
            // a Journey teammate: no extra tabs
            PartyBotEntity j = PartyBots.spawn(p, CharacterType.MAX, new CompoundTag());
            if (j != null) { j.getPersistentData().putBoolean("xw_journey_bot", true); HOLD[0] = j; PartyBots.openBag(p, j); }
        }), 15));
        l.add(new Step(null, () -> {
            BotInventoryScreen s = screen(mc);
            check("Journey teammate has no extra tabs", s != null && !s.getMenu().hasTabs(), s == null ? "no screen" : "hasTabs=" + s.getMenu().hasTabs());
            UiTest.server(p -> { p.closeContainer(); if (HOLD[0] instanceof PartyBotEntity j) { PartyBots.forgetFake(j.getUUID()); j.discard(); } HOLD[0] = null; });
        }, 10));

        // ---- 2. relog
        l.add(new Step(null, () -> UiTest.server(p -> { PartyBots.onLogout(new PlayerEvent.PlayerLoggedOutEvent(p)); PartyBots.onLogin(new PlayerEvent.PlayerLoggedInEvent(p)); }), 60));
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity r = bot(p, CharacterType.RHALIX);
            check("relog", extrasOk(p, r) && bots(p, CharacterType.RHALIX) == 1 && r.inventory.getItem(BotInventory.tabStart(2)).is(Items.DIAMOND),
                    "bots=" + bots(p, CharacterType.RHALIX) + " " + extras(r) + " | owned " + owned(p) + " (was " + N[0] + "/" + N[1] + "/" + N[2] + ")");
        }), 5));

        // ---- 3. crash: the snapshot written before each player save has the extra tabs
        l.add(new Step(null, () -> UiTest.server(p -> crashAndRestart(p, saveAndReadBack(p))), 80));
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity r = bot(p, CharacterType.RHALIX);
            check("crash snapshot", extrasOk(p, r) && bots(p, CharacterType.RHALIX) == 1, "bots=" + bots(p, CharacterType.RHALIX) + " " + extras(r) + " | owned " + owned(p));
        }), 5));
        l.add(new Step(null, () -> UiTest.server(p -> crashAndRestart(p, saveAndReadBack(p))), 80));
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity r = bot(p, CharacterType.RHALIX);
            check("crash again (no doubles)", extrasOk(p, r) && bots(p, CharacterType.RHALIX) == 1, "bots=" + bots(p, CharacterType.RHALIX) + " " + extras(r) + " | owned " + owned(p));
        }), 5));

        // ---- 4. dismissed and added again: the tabs wait in your save (not in the character's inventory), then come back
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity r = bot(p, CharacterType.RHALIX);
            if (r != null) { PartyBots.checkIn(r, p); PartyBots.forgetFake(r.getUUID()); r.discard(); }
            int st = BotBags.stored(p, "rhalix", Items.DIAMOND), inChar = walk(CharacterInventories.saved(p, "rhalix"), "minecraft:diamond", false);
            check("dismissed: tabs kept for the bot", st == 20 && BotBags.stored(p, "rhalix", Items.GOLD_INGOT) == 7 && inChar == 0 && onPlayer(p, Items.DIAMOND) == 0
                    && owned(p, "minecraft:diamond") == N[0], "stored diamond=" + st + " gold=" + BotBags.stored(p, "rhalix", Items.GOLD_INGOT) + " in rhalix's own save=" + inChar + " on you=" + onPlayer(p, Items.DIAMOND) + " | owned " + owned(p));
            fresh(p, CharacterType.RHALIX);
        }), 30));
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity r = bot(p, CharacterType.RHALIX);
            check("re-added: tabs back", extrasOk(p, r) && BotBags.stored(p, "rhalix", Items.DIAMOND) == 0 && bag(r, Items.APPLE) == 3, extras(r) + " stored now=" + BotBags.stored(p, "rhalix", Items.DIAMOND) + " | owned " + owned(p));
        }), 5));

        // ---- 5. you play that character: its tabs stay with the bot (not on you), and are back when it's a bot again
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity r = bot(p, CharacterType.RHALIX);
            if (r != null) { PartyBots.checkIn(r, p); PartyBots.forgetFake(r.getUUID()); r.discard(); }
            com.xenowarriors.character.CharacterManager.setCharacter(p, CharacterType.RHALIX);
        }), 30));
        l.add(new Step(null, () -> UiTest.server(p -> {
            check("playing it: tabs not on you", onPlayer(p, Items.DIAMOND) == 0 && onPlayer(p, Items.GOLD_INGOT) == 0 && onPlayer(p, Items.APPLE) == 3 && BotBags.stored(p, "rhalix", Items.DIAMOND) == 20
                    && owned(p, "minecraft:diamond") == N[0], "on you diamond=" + onPlayer(p, Items.DIAMOND) + " gold=" + onPlayer(p, Items.GOLD_INGOT) + " apple(its bag)=" + onPlayer(p, Items.APPLE)
                    + " stored diamond=" + BotBags.stored(p, "rhalix", Items.DIAMOND) + " | owned " + owned(p));
            com.xenowarriors.character.CharacterManager.setCharacter(p, CharacterType.MIKIKO);
        }), 30));
        l.add(new Step(null, () -> UiTest.server(p -> fresh(p, CharacterType.RHALIX)), 30));
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity r = bot(p, CharacterType.RHALIX);
            check("a bot again: tabs back", extrasOk(p, r) && bag(r, Items.APPLE) == 3, extras(r) + " | owned " + owned(p));
        }), 5));

        // ---- 6. down and bled out: its things (tabs too) go home, and come back with it
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity r = bot(p, CharacterType.RHALIX);
            if (r == null) return;
            r.die(r.damageSources().generic());
            PartyBots.bleedOutForTest(r);
            check("bled out: tabs home", bots(p, CharacterType.RHALIX) == 0 && BotBags.stored(p, "rhalix", Items.DIAMOND) == 20 && owned(p, "minecraft:diamond") == N[0],
                    "bots=" + bots(p, CharacterType.RHALIX) + " stored=" + BotBags.stored(p, "rhalix", Items.DIAMOND) + " | owned " + owned(p));
            fresh(p, CharacterType.RHALIX);
        }), 30));
        l.add(new Step(null, () -> UiTest.server(p -> check("after bleed-out, re-added", extrasOk(p, bot(p, CharacterType.RHALIX)), extras(bot(p, CharacterType.RHALIX)) + " | owned " + owned(p))), 5));

        // ---- 7. a Journey puts bots away (with their things) and brings them back
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBots.stashAll(p);
            check("stashed for a Journey", bots(p, CharacterType.RHALIX) == 0 && owned(p, "minecraft:diamond") == N[0], "bots=" + bots(p, CharacterType.RHALIX) + " | owned " + owned(p));
            PartyBots.unstash(p);
        }), 30));
        l.add(new Step(null, () -> UiTest.server(p -> check("back from the Journey stash", extrasOk(p, bot(p, CharacterType.RHALIX)) && bots(p, CharacterType.RHALIX) == 1,
                extras(bot(p, CharacterType.RHALIX)) + " | owned " + owned(p))), 5));

        // ---- 8. errands use the extra tabs: eating, picking up when the bag is full, crafting from them
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity r = bot(p, CharacterType.RHALIX);
            if (r == null) return;
            r.inventory.clearContent();
            for (EquipmentSlot es : EquipmentSlot.values()) r.setItemSlot(es, ItemStack.EMPTY);
            r.inventory.setItem(BotInventory.tabStart(3) + 10, new ItemStack(Items.BREAD, 4));
            r.setHealth(r.getMaxHealth() * 0.3F);
            HOLD[1] = r.getHealth();
        }), 110));
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity r = bot(p, CharacterType.RHALIX);
            if (r == null) return;
            check("eats from an extra tab", extra(r, Items.BREAD) < 4 && r.getHealth() > (Float) HOLD[1], "bread left=" + extra(r, Items.BREAD) + " hp " + (Float) HOLD[1] + " -> " + r.getHealth());
            r.setHealth(r.getMaxHealth());
            r.inventory.clearContent();
            for (int i = 0; i < BotInventory.BAG; i++) r.inventory.setItem(i, new ItemStack(Items.COBBLESTONE, 64));   // (its bag full)
            r.setNoAi(true);
            r.moveTo(p.getX() + 6, p.getY(), p.getZ() + 6, 0, 0);
            ItemEntity it = new ItemEntity(p.level(), r.getX(), r.getY() + 0.3, r.getZ(), new ItemStack(Items.IRON_INGOT, 12));
            it.setPickUpDelay(0);
            p.level().addFreshEntity(it);
        }), 100));
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity r = bot(p, CharacterType.RHALIX);
            if (r == null) return;
            check("bag full: pickups go into the extra tabs", extra(r, Items.IRON_INGOT) == 12, "iron in extra tabs=" + extra(r, Items.IRON_INGOT) + " cobble in bag=" + bag(r, Items.COBBLESTONE));
            ItemStack left = r.inventory.addItem(new ItemStack(Items.DIRT, 64 * 3));
            check("full only when every tab is full", left.isEmpty(), "dirt that didn't fit=" + left.getCount());
            r.setNoAi(false);
            r.moveTo(p.getX() + 1.5, p.getY(), p.getZ() + 3, 180, 0);
            r.inventory.clearContent();
            r.inventory.setItem(BotInventory.tabStart(1) + 5, new ItemStack(Items.IRON_INGOT, 8));      // materials only in the extra tabs
            r.inventory.setItem(BotInventory.tabStart(3) + 26, new ItemStack(Items.CRAFTING_TABLE));
            com.xenowarriors.bot.BotCrafting.clearQuiet(r);
            var ideas = com.xenowarriors.bot.BotCrafting.ideas(r);
            var pick = com.xenowarriors.bot.BotCrafting.suggest(r, p, true);
            log("craft from extra tabs: ideas=" + ideas.size() + " pick=" + (pick == null ? "nothing" : pick.kind + " " + pick.result + " missing=" + pick.missing));
            if (pick != null) p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), "xenobot craft " + r.getId() + " " + com.xenowarriors.bot.BotCrafting.offerToken(r));
        }), 20));
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity r = bot(p, CharacterType.RHALIX);
            if (r == null) return;
            int iron = r.inventory.total(Items.IRON_INGOT);
            StringBuilder worn = new StringBuilder();
            for (ItemStack a : r.armour()) if (!a.isEmpty()) worn.append(a.getItem()).append(' ');
            boolean made = iron < 8;
            check("crafts with materials from the extra tabs", made, "iron left=" + iron + " (was 8) wearing " + worn.toString().trim());
        }), 5));

        // ---- 9. tidying: paused while you look; "Sort" does it; then by itself once you close it - nothing lost, nothing worn moved
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity r = bot(p, CharacterType.RHALIX);
            if (r == null) return;
            mess(r);
            HOLD[2] = BotBags.census(r.inventory);
            HOLD[3] = gear(r);
            N[0] = BotBags.sorts;
            BotBags.AUTO = true;
            PartyBots.openBag(p, r);
        }), 160));
        l.add(new Step("botbag_before_sort_bag", () -> UiTest.server(p -> {
            PartyBotEntity r = bot(p, CharacterType.RHALIX);
            check("no tidying while its inventory is open", BotBags.sorts == N[0], "sorts " + N[0] + " -> " + BotBags.sorts + " (open " + (r != null && r.inventory.beingViewed()) + ")");
        }), 5));
        l.add(new Step(null, () -> { BotInventoryScreen s = screen(mc); if (s != null) s.press(1); }, 10));
        l.add(new Step("botbag_before_sort_extra1", () -> {}, 3));
        l.add(new Step(null, () -> { BotInventoryScreen s = screen(mc); if (s != null) { s.press(0); s.press(BotMenu.SORT); } }, 15));
        l.add(new Step("botbag_after_sort_bag", () -> UiTest.server(p -> verifySorted(bot(p, CharacterType.RHALIX), "Sort button")), 5));
        l.add(new Step(null, () -> { BotInventoryScreen s = screen(mc); if (s != null) s.press(1); }, 10));
        l.add(new Step("botbag_after_sort_extra1", () -> {}, 3));
        l.add(new Step(null, () -> { BotInventoryScreen s = screen(mc); if (s != null) s.press(2); }, 10));
        l.add(new Step("botbag_after_sort_extra2", () -> {}, 3));
        l.add(new Step(null, () -> UiTest.server(p -> {
            p.closeContainer();
            PartyBotEntity r = bot(p, CharacterType.RHALIX);
            if (r == null) return;
            mess(r);
            HOLD[2] = BotBags.census(r.inventory);
            N[0] = BotBags.sorts;
        }), 170));
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity r = bot(p, CharacterType.RHALIX);
            check("tidies by itself when idle", BotBags.sorts > N[0], "sorts " + N[0] + " -> " + BotBags.sorts);
            verifySorted(r, "by itself");
            BotBags.AUTO = false;
        }), 5));

        if (dmz) DmzItemTests.build(mc, l, BotBagTests::check, BotBagTests::log);

        l.add(new Step(null, () -> UiTest.server(p -> { BotBags.AUTO = true; clear(p); log("summary pass=" + pass + " fail=" + fail); log("DONE"); }), 10));
    }

    /** A messy inventory: part stacks everywhere, gear in the extra tabs, cobble in the bag. */
    private static void mess(PartyBotEntity r) {
        r.inventory.clearContent();
        for (EquipmentSlot es : EquipmentSlot.values()) r.setItemSlot(es, ItemStack.EMPTY);
        r.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        r.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        int[] cob = {0, 5, 10, 40, 50, 60, 80, 90, 100, 104};
        for (int s : cob) r.inventory.setItem(s, new ItemStack(Items.COBBLESTONE, 20));
        r.inventory.setItem(70, new ItemStack(Items.DIAMOND_SWORD));
        r.inventory.setItem(45, new ItemStack(Items.BOW));
        r.inventory.setItem(33, new ItemStack(Items.ARROW, 16));
        r.inventory.setItem(99, new ItemStack(Items.ARROW, 16));
        r.inventory.setItem(88, new ItemStack(Items.BREAD, 5));
        r.inventory.setItem(3, new ItemStack(Items.TORCH, 10));
        r.inventory.setItem(2, new ItemStack(Items.IRON_ORE, 7));
        r.inventory.setItem(20, new ItemStack(Items.RAW_IRON, 9));
        r.inventory.setItem(1, new ItemStack(Items.LEATHER_CHESTPLATE));
        r.inventory.setItem(110, new ItemStack(Items.SHIELD));
        r.inventory.setItem(111, new ItemStack(Items.TOTEM_OF_UNDYING));
        r.inventory.setItem(12, new ItemStack(Items.DIRT, 30));
        r.inventory.setItem(13, new ItemStack(Items.OAK_LOG, 12));
        r.inventory.setItem(115, new ItemStack(Items.FLOWER_POT, 3));
        r.inventory.setItem(116, new ItemStack(Items.COAL, 11));
        r.inventory.setItem(64, new ItemStack(Items.APPLE, 2));
    }

    private static String gear(PartyBotEntity r) {
        StringBuilder sb = new StringBuilder();
        for (EquipmentSlot es : EquipmentSlot.values()) sb.append(es.getName()).append('=').append(r.getItemBySlot(es).getItem()).append(' ');
        return sb.toString().trim();
    }

    private static void verifySorted(PartyBotEntity r, String how) {
        if (r == null) { check("tidy " + how, false, "no bot"); return; }
        Map<String, Integer> now = BotBags.census(r.inventory);
        boolean same = now.equals(HOLD[2]);
        List<Integer> cobble = new ArrayList<>();
        for (int i = 0; i < BotInventory.SIZE; i++) if (r.inventory.getItem(i).is(Items.COBBLESTONE)) cobble.add(r.inventory.getItem(i).getCount());
        boolean merged = cobble.equals(List.of(64, 64, 64, 8));
        boolean kept = bag(r, Items.DIAMOND_SWORD) == 1 && bag(r, Items.BOW) == 1 && bag(r, Items.ARROW) == 32 && bag(r, Items.SHIELD) == 1
                && bag(r, Items.TOTEM_OF_UNDYING) == 1 && bag(r, Items.BREAD) == 5 && bag(r, Items.TORCH) == 10;
        boolean away = extra(r, Items.COBBLESTONE) == 200 && extra(r, Items.DIRT) == 30 && extra(r, Items.IRON_ORE) == 7 && extra(r, Items.LEATHER_CHESTPLATE) == 1;
        // categories in order through the extra tabs: blocks, materials, food, gear, other
        StringBuilder order = new StringBuilder();
        int lastCat = -1; boolean ordered = true;
        for (int i = BotInventory.BAG; i < BotInventory.SIZE; i++) {
            ItemStack s = r.inventory.getItem(i);
            if (s.isEmpty()) continue;
            int c = s.is(Items.COBBLESTONE) || s.is(Items.DIRT) || s.is(Items.OAK_LOG) ? 0 : s.is(Items.IRON_ORE) || s.is(Items.RAW_IRON) || s.is(Items.COAL) ? 1 : s.is(Items.APPLE) ? 2 : s.is(Items.LEATHER_CHESTPLATE) ? 3 : 4;
            if (c < lastCat) ordered = false;
            if (c != lastCat) order.append(c);
            lastCat = c;
        }
        boolean gearSame = gear(r).equals(HOLD[3]);
        check("tidy " + how, same && merged && kept && away && ordered && gearSame,
                "same items=" + same + " cobble stacks=" + cobble + " kept in bag=" + kept + " rest in tabs=" + away + " category order=" + order + (ordered ? "" : " (out of order)")
                        + " worn/held unchanged=" + gearSame + (same ? "" : " before=" + HOLD[2] + " after=" + now));
    }
}
