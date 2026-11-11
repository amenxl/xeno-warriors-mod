package com.xenowarriors.client.test;

import com.xenowarriors.bot.BotBags;
import com.xenowarriors.bot.BotInventory;
import com.xenowarriors.bot.BotMenu;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.bot.BotInventoryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;

/**
 * UI test (focus "botbig", from {@link DmzBiomeTests}): the bots' three extra tabs at 54 slots each. Sizes; the screen
 * at 854x480 (GUI scale auto) with all six rows of a tab and the bag's four; old saves with 27-slot tabs (bot record,
 * entity save, waiting in the owner's save) loading tab for tab with exact counts; items in the new slots 28-54 surviving
 * relog, a crash snapshot and dismiss / re-add exactly; picking up into the very last slot; and Sort filling past slot 27.
 * Each check logs "XW-TEST botbig ... -> PASS/FAIL".
 */
final class BotBigBagTests {
    private BotBigBagTests() {}

    private static int pass, fail;
    private static void log(String s) { UiTest.log("botbig " + s); }
    private static void check(String what, boolean ok, String detail) {
        if (ok) pass++; else fail++;
        log(what + ": " + detail + " -> " + (ok ? "PASS" : "FAIL"));
    }

    private static PartyBotEntity bot(ServerPlayer p) {
        for (PartyBotEntity b : PartyBots.bots(p)) if (b.character() == CharacterType.RHALIX) return b;
        return null;
    }

    private static int bots(ServerPlayer p) { int n = 0; for (PartyBotEntity b : PartyBots.bots(p)) if (b.character() == CharacterType.RHALIX) n++; return n; }

    private static PartyBotEntity fresh(ServerPlayer p) {
        p.getPersistentData().remove("xw_bot_rejoin_rhalix");
        PartyBotEntity b = PartyBots.spawn(p, CharacterType.RHALIX, null);
        if (b != null) b.moveTo(p.getX() + 2, p.getY(), p.getZ() + 2, 0, 0);
        return b;
    }

    private static void dismiss(ServerPlayer p) {
        for (PartyBotEntity b : new ArrayList<>(PartyBots.bots(p))) if (b.character() == CharacterType.RHALIX) { PartyBots.checkIn(b, p); PartyBots.forgetFake(b.getUUID()); b.discard(); }
    }

    private static BotInventoryScreen screen(Minecraft mc) { return mc.screen instanceof BotInventoryScreen s ? s : null; }

    // ---- the old (27 a tab) layout, as v0.218 saved it: slot -> item, count
    private static final int[] OLD_SLOT = {0, 26, 27, 53, 60, 80};
    private static final Item[] OLD_ITEM = {Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT, Items.APPLE, Items.IRON_INGOT, Items.COBBLESTONE};
    private static final int[] OLD_COUNT = {10, 5, 7, 3, 12, 64};

    private static ListTag oldList() {
        ListTag l = new ListTag();
        for (int i = 0; i < OLD_SLOT.length; i++) {
            CompoundTag t = new ItemStack(OLD_ITEM[i], OLD_COUNT[i]).save(new CompoundTag());
            t.putByte("Slot", (byte) OLD_SLOT[i]);
            l.add(t);
        }
        return l;
    }

    /** Old slot s of tab 1-3 is slot s of the same (bigger) tab, with the same count; nothing else anywhere in the tabs. */
    private static String migrated(PartyBotEntity r) {
        if (r == null) return "no bot";
        StringBuilder bad = new StringBuilder();
        int stacks = 0;
        for (int i = BotInventory.BAG; i < BotInventory.SIZE; i++) if (!r.inventory.getItem(i).isEmpty()) stacks++;
        for (int i = 0; i < OLD_SLOT.length; i++) {
            int tab = OLD_SLOT[i] / 27 + 1, at = BotInventory.tabStart(tab) + OLD_SLOT[i] % 27;
            ItemStack s = r.inventory.getItem(at);
            if (!s.is(OLD_ITEM[i]) || s.getCount() != OLD_COUNT[i] || r.inventory.total(OLD_ITEM[i]) != OLD_COUNT[i])
                bad.append(" old ").append(OLD_SLOT[i]).append("->tab ").append(tab).append(" slot ").append(OLD_SLOT[i] % 27 + 1).append(" has ").append(s).append(" total ").append(r.inventory.total(OLD_ITEM[i]));
        }
        if (stacks != OLD_SLOT.length) bad.append(" stacks in tabs=").append(stacks);
        return bad.length() == 0 ? "OK" : bad.toString().trim();
    }

    // ---- items in the new slots
    private static final int[] NEW_AT = {BotInventory.tabStart(1) + 53, BotInventory.tabStart(2) + 40, BotInventory.tabStart(3) + 53, BotInventory.tabStart(1) + 27};
    private static final Item[] NEW_ITEM = {Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT, Items.REDSTONE};
    private static final int[] NEW_COUNT = {9, 4, 33, 61};

    private static String exact(PartyBotEntity r) {
        if (r == null) return "no bot";
        StringBuilder bad = new StringBuilder();
        for (int i = 0; i < NEW_AT.length; i++) {
            ItemStack s = r.inventory.getItem(NEW_AT[i]);
            if (!s.is(NEW_ITEM[i]) || s.getCount() != NEW_COUNT[i] || r.inventory.total(NEW_ITEM[i]) != NEW_COUNT[i])
                bad.append(" slot ").append(NEW_AT[i]).append(" has ").append(s).append(" total ").append(r.inventory.total(NEW_ITEM[i]));
        }
        return bad.length() == 0 ? "OK" : bad.toString().trim();
    }

    private static CompoundTag saveAndReadBack(ServerPlayer p) {
        p.server.saveEverything(true, false, true);
        try {
            File level = p.server.getWorldPath(LevelResource.LEVEL_DATA_FILE).toFile();
            CompoundTag host = level.exists() ? NbtIo.readCompressed(level).getCompound("Data").getCompound("Player") : new CompoundTag();
            if (!host.isEmpty() && p.server.isSingleplayerOwner(p.getGameProfile())) return host;
            File own = p.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(p.getStringUUID() + ".dat").toFile();
            if (own.exists()) return NbtIo.readCompressed(own);
        } catch (Exception e) { log("reading the save failed: " + e); }
        return null;
    }

    private static final Object[] HOLD = new Object[2];

    static void build(Minecraft mc, Consumer<UiTest.Step> l) {
        l.accept(new UiTest.Step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null); mc.options.hideGui = false; mc.options.guiScale().set(0); mc.resizeDisplay();
            UiTest.character(CharacterType.MIKIKO);
            UiTest.server(p -> { p.setGameMode(GameType.SURVIVAL); p.setHealth(p.getMaxHealth()); BotBags.AUTO = false; dismiss(p); BotBags.take(p, "rhalix"); }); }, 40));
        l.accept(new UiTest.Step(null, () -> UiTest.server(p -> {
            PartyBotEntity r = fresh(p);
            check("sizes", BotInventory.TAB == 54 && BotInventory.TABS == 3 && BotInventory.SIZE == 36 + 3 * 54 && r != null && r.inventory.getContainerSize() == 198,
                    "tab=" + BotInventory.TAB + " tabs=" + BotInventory.TABS + " size=" + BotInventory.SIZE + " bot container=" + (r == null ? -1 : r.inventory.getContainerSize()));
            if (r == null) return;
            r.inventory.clearContent();
            r.inventory.setItem(0, new ItemStack(Items.APPLE, 3));
            r.inventory.setItem(35, new ItemStack(Items.STICK, 2));
            for (int i = 0; i < NEW_AT.length; i++) r.inventory.setItem(NEW_AT[i], new ItemStack(NEW_ITEM[i], NEW_COUNT[i]));
            r.inventory.setItem(BotInventory.tabStart(1), new ItemStack(Items.BREAD, 5));
            PartyBots.openBag(p, r);
        }), 20));

        // ---- 1. the screen at 854x480: the bag (4 rows shown), then an extra tab (all 6 rows)
        l.accept(new UiTest.Step("botbig_bag_854", () -> {
            BotInventoryScreen s = screen(mc);
            int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
            log("window " + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight() + " gui scale " + mc.getWindow().getGuiScale() + " -> " + w + "x" + h);
            if (s == null) { check("screen", false, "no bot screen (" + mc.screen + ")"); return; }
            BotMenu m = s.getMenu();
            int top = s.getGuiTop(), left = s.getGuiLeft();
            boolean fits = top - 14 >= 0 && top + s.getYSize() <= h && left >= 0 && left + s.getXSize() <= w;
            check("fits at 854x480", fits && mc.getWindow().getWidth() <= 854 && mc.getWindow().getHeight() <= 480,
                    "screen " + s.getXSize() + "x" + s.getYSize() + " at " + left + "," + top + " (tab row from " + (top - 14) + ") in " + w + "x" + h);
            check("menu slots", m.slots.size() == BotMenu.GEAR + 54 + 36, "slots=" + m.slots.size());
            int active = 0; for (int i = BotMenu.GEAR; i < BotMenu.GEAR + 54; i++) if (m.slots.get(i).isActive()) active++;
            check("bag tab shows 36", m.tab() == 0 && active == 36 && m.slots.get(BotMenu.GEAR + 35).getItem().is(Items.STICK), "active=" + active + " slot 36=" + m.slots.get(BotMenu.GEAR + 35).getItem());
            s.press(1);
        }, 10));
        l.accept(new UiTest.Step("botbig_extra1_854", () -> {
            BotInventoryScreen s = screen(mc);
            if (s == null) { check("extra tab", false, "screen gone"); return; }
            BotMenu m = s.getMenu();
            int active = 0; for (int i = BotMenu.GEAR; i < BotMenu.GEAR + 54; i++) if (m.slots.get(i).isActive()) active++;
            var s28 = m.slots.get(BotMenu.GEAR + 27).getItem(); var s54 = m.slots.get(BotMenu.GEAR + 53).getItem();
            check("extra tab shows 54 (6 rows)", m.tab() == 1 && active == 54 && m.slots.get(BotMenu.GEAR).getItem().is(Items.BREAD) && s28.is(Items.REDSTONE) && s54.is(Items.DIAMOND) && s54.getCount() == 9,
                    "active=" + active + " slot 1=" + m.slots.get(BotMenu.GEAR).getItem() + " slot 28=" + s28 + " slot 54=" + s54);
            int bottom = 0; for (var sl : m.slots) bottom = Math.max(bottom, s.getGuiTop() + sl.y + 16);
            check("every slot on screen", bottom <= mc.getWindow().getGuiScaledHeight(), "lowest slot ends at " + bottom + " of " + mc.getWindow().getGuiScaledHeight());
            s.press(3);
        }, 10));
        l.accept(new UiTest.Step("botbig_extra3_854", () -> {
            BotInventoryScreen s = screen(mc);
            if (s == null) return;
            var last = s.getMenu().slots.get(BotMenu.GEAR + 53).getItem();
            check("extra tab 3 slot 54", s.getMenu().tab() == 3 && last.is(Items.GOLD_INGOT) && last.getCount() == 33, "slot 54=" + last);
        }, 10));
        l.accept(new UiTest.Step(null, () -> UiTest.server(p -> {
            p.closeContainer();
            PartyBotEntity r = bot(p);
            check("start layout", "OK".equals(exact(r)), exact(r));
        }), 10));

        // ---- 2. relog, crash, dismiss and re-add: the new slots exactly
        l.accept(new UiTest.Step(null, () -> UiTest.server(p -> { PartyBots.onLogout(new PlayerEvent.PlayerLoggedOutEvent(p)); PartyBots.onLogin(new PlayerEvent.PlayerLoggedInEvent(p)); }), 60));
        l.accept(new UiTest.Step(null, () -> UiTest.server(p -> check("relog exact", "OK".equals(exact(bot(p))) && bots(p) == 1, exact(bot(p)) + " bots=" + bots(p))), 5));
        l.accept(new UiTest.Step(null, () -> UiTest.server(p -> {
            CompoundTag disk = saveAndReadBack(p);
            if (disk == null) { check("crash", false, "nothing on disk"); return; }
            for (PartyBotEntity b : new ArrayList<>(PartyBots.bots(p))) { PartyBots.forgetFake(b.getUUID()); b.discard(); }
            PartyBots.resetForCrashTest(p);
            p.load(disk.copy());
            PartyBots.onLogin(new PlayerEvent.PlayerLoggedInEvent(p));
        }), 80));
        l.accept(new UiTest.Step(null, () -> UiTest.server(p -> check("crash snapshot exact", "OK".equals(exact(bot(p))) && bots(p) == 1, exact(bot(p)) + " bots=" + bots(p))), 5));
        l.accept(new UiTest.Step(null, () -> UiTest.server(p -> {
            dismiss(p);
            int st = BotBags.stored(p, "rhalix", Items.GOLD_INGOT);
            check("dismissed: tabs wait in your save", st == 33 && BotBags.stored(p, "rhalix", Items.REDSTONE) == 61, "stored gold=" + st + " redstone=" + BotBags.stored(p, "rhalix", Items.REDSTONE));
            fresh(p);
        }), 30));
        l.accept(new UiTest.Step(null, () -> UiTest.server(p -> check("re-added exact", "OK".equals(exact(bot(p))) && BotBags.stored(p, "rhalix", Items.GOLD_INGOT) == 0, exact(bot(p)))), 5));

        // ---- 3. old saves (27 a tab): the bot's record, its entity save, and tabs waiting in your save
        l.accept(new UiTest.Step(null, () -> UiTest.server(p -> {
            PartyBotEntity r = bot(p);
            if (r == null) return;
            r.inventory.clearContent();
            CompoundTag rec = r.record();
            rec.put("xbag", oldList());
            r.loadRecord(rec);
            check("old record migrates", "OK".equals(migrated(r)), migrated(r));
            CompoundTag t = new CompoundTag();
            r.saveWithoutId(t);
            t.put("XInv", oldList());
            r.inventory.clearContent();
            r.readAdditionalSaveData(t);
            check("old entity save migrates", "OK".equals(migrated(r)), migrated(r));
            // a new save of it: written in the new layout, read back the same
            CompoundTag t2 = new CompoundTag();
            r.saveWithoutId(t2);
            r.inventory.clearContent();
            r.readAdditionalSaveData(t2);
            check("re-saved in the new layout", "OK".equals(migrated(r)) && t2.getList("XInv", 10).getCompound(0).contains(BotInventory.XSLOT), migrated(r));
            r.inventory.clearContent();
            dismiss(p);
            CompoundTag keep = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            CompoundTag bags = keep.getCompound("xw_botbags");
            bags.put("rhalix", oldList());
            keep.put("xw_botbags", bags);
            p.getPersistentData().put(Player.PERSISTED_NBT_TAG, keep);
            fresh(p);
        }), 30));
        l.accept(new UiTest.Step(null, () -> UiTest.server(p -> {
            PartyBotEntity r = bot(p);
            check("old tabs waiting in your save migrate", "OK".equals(migrated(r)) && BotBags.stored(p, "rhalix", Items.DIAMOND) == 0, migrated(r));
            // and an old waiting list merged with new tabs when it goes home again
            if (r != null) r.inventory.setItem(BotInventory.tabStart(3) + 50, new ItemStack(Items.REDSTONE, 2));
            dismiss(p);
            HOLD[0] = BotBags.stored(p, "rhalix", Items.DIAMOND) + "/" + BotBags.stored(p, "rhalix", Items.REDSTONE);
            fresh(p);
        }), 30));
        l.accept(new UiTest.Step(null, () -> UiTest.server(p -> {
            PartyBotEntity r = bot(p);
            boolean ok = r != null && r.inventory.total(Items.DIAMOND) == 10 && r.inventory.total(Items.COBBLESTONE) == 64 && r.inventory.getItem(BotInventory.tabStart(3) + 50).is(Items.REDSTONE);
            check("migrated tabs round trip home and back", ok, "stored then " + HOLD[0] + " | " + migrated(r));
        }), 5));

        // ---- 4. picking up fills every slot of every tab; Sort fills past slot 27
        l.accept(new UiTest.Step(null, () -> UiTest.server(p -> {
            PartyBotEntity r = bot(p);
            if (r == null) return;
            r.inventory.clearContent();
            for (int i = 0; i < BotInventory.SIZE - 1; i++) r.inventory.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            ItemStack left = r.inventory.addItem(new ItemStack(Items.DIRT, 70));
            check("the last slot (tab 3, 54) takes pickups", r.inventory.getItem(BotInventory.SIZE - 1).is(Items.DIRT) && r.inventory.getItem(BotInventory.SIZE - 1).getCount() == 64 && left.getCount() == 6,
                    "last slot=" + r.inventory.getItem(BotInventory.SIZE - 1) + " didn't fit=" + left.getCount());
            r.inventory.clearContent();
            for (int i = 0; i < 100; i++) r.inventory.setItem(i < 30 ? i : BotInventory.BAG + (i * 7) % BotInventory.EXTRA, new ItemStack(i % 2 == 0 ? Items.COBBLESTONE : Items.DIRT, 64));
            HOLD[1] = BotBags.census(r.inventory);
            PartyBots.openBag(p, r);
        }), 20));
        l.accept(new UiTest.Step(null, () -> { BotInventoryScreen s = screen(mc); if (s != null) { s.press(1); s.press(BotMenu.SORT); } }, 15));
        l.accept(new UiTest.Step("botbig_sorted_extra1", () -> UiTest.server(p -> {
            PartyBotEntity r = bot(p);
            if (r == null) return;
            int stacks = 0, tab1 = 0, past27 = 0, tab2 = 0;
            for (int i = 0; i < BotInventory.SIZE; i++) if (!r.inventory.getItem(i).isEmpty()) stacks++;
            for (int i = 0; i < 54; i++) {
                if (!r.inventory.getItem(BotInventory.tabStart(1) + i).isEmpty()) { tab1++; if (i >= 27) past27++; }
                if (!r.inventory.getItem(BotInventory.tabStart(2) + i).isEmpty()) tab2++;
            }
            boolean same = BotBags.census(r.inventory).equals(HOLD[1]);
            check("Sort fills past slot 27", same && stacks == 100 && tab1 == 54 && past27 == 27 && tab2 == 46,
                    "same items=" + same + " stacks=" + stacks + " tab 1=" + tab1 + " (slots 28-54: " + past27 + ") tab 2=" + tab2);
        }), 5));
        l.accept(new UiTest.Step(null, () -> UiTest.server(p -> {
            p.closeContainer();
            BotBags.AUTO = true;
            dismiss(p);
            BotBags.take(p, "rhalix");
            log("summary pass=" + pass + " fail=" + fail);
            log("DONE");
        }), 10));
    }
}
