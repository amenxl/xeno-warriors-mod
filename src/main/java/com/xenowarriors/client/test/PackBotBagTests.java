package com.xenowarriors.client.test;

import com.xenowarriors.bot.BotBags;
import com.xenowarriors.bot.BotInventory;
import com.xenowarriors.bot.BotMenu;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.client.bot.BotInventoryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Map;

/**
 * Pack test part (run from {@link PackTests}): a bot's inventory screen with its extra tabs and the Sort button in the
 * real pack (JEI, Inventory Totem, Clumps, Equipment Compare... draw on and around inventory screens). Logs through PackTests.check.
 */
final class PackBotBagTests {
    private PackBotBagTests() {}

    private static Map<String, Integer> before;

    private static PartyBotEntity bot(net.minecraft.server.level.ServerPlayer p) {
        for (PartyBotEntity b : PartyBots.bots(p)) if (b.character() == CharacterType.RHALIX) return b;
        return null;
    }

    static void build(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> { mc.setScreen(null); UiTest.character(CharacterType.MIKIKO); PackTests.server(p -> {
            p.getPersistentData().remove("xw_bot_rejoin_rhalix");
            if (bot(p) == null) PartyBots.spawn(p, CharacterType.RHALIX, null);
        }); }, 40));
        l.add(new Step(null, () -> PackTests.server(p -> {
            PartyBotEntity r = bot(p);
            if (r == null) { PackTests.check("botbag spawn", false, "no bot"); return; }
            r.inventory.clearContent();
            for (int i = 0; i < 6; i++) r.inventory.setItem(i * 7, new ItemStack(Items.COBBLESTONE, 10));
            r.inventory.setItem(BotInventory.tabStart(1) + 2, new ItemStack(Items.IRON_SWORD));
            r.inventory.setItem(BotInventory.tabStart(3) + 20, new ItemStack(Items.BREAD, 3));
            r.inventory.setItem(BotInventory.tabStart(1) + 53, new ItemStack(Items.REDSTONE, 9));   // (slot 54: the bigger tabs)
            r.inventory.setItem(9, new ItemStack(Items.TOTEM_OF_UNDYING));
            before = BotBags.census(r.inventory);
            PartyBots.openBag(p, r);
        }), 20));
        l.add(new Step("pack_botbag_bag", () -> PackTests.client("botbag screen", () -> {
            BotInventoryScreen s = mc.screen instanceof BotInventoryScreen x ? x : null;
            PackTests.check("botbag screen with tabs", s != null && s.getMenu().hasTabs(), "screen=" + (mc.screen == null ? "none" : mc.screen.getClass().getName()));
            if (s != null) s.press(1);
        }), 10));
        l.add(new Step("pack_botbag_extra1", () -> PackTests.client("botbag tab", () -> {
            BotInventoryScreen s = mc.screen instanceof BotInventoryScreen x ? x : null;
            PackTests.check("botbag extra tab 1", s != null && s.getMenu().tab() == 1 && s.getMenu().slots.get(BotMenu.GEAR + 2).getItem().is(Items.IRON_SWORD),
                    s == null ? "no screen" : "tab=" + s.getMenu().tab() + " slot=" + s.getMenu().slots.get(BotMenu.GEAR + 2).getItem());
            if (s != null) {
                var last = s.getMenu().slots.get(BotMenu.GEAR + 53);
                int h = mc.getWindow().getGuiScaledHeight();
                PackTests.check("botbag 54-slot tab", last.isActive() && last.getItem().is(Items.REDSTONE) && s.getGuiTop() - 14 >= 0 && s.getGuiTop() + s.getYSize() <= h,
                        "slot 54=" + last.getItem() + " screen top " + s.getGuiTop() + " height " + s.getYSize() + " in " + mc.getWindow().getGuiScaledWidth() + "x" + h);
            }
            if (s != null) { s.press(0); s.press(BotMenu.SORT); }
        }), 15));
        l.add(new Step("pack_botbag_sorted", () -> PackTests.server(p -> {
            PartyBotEntity r = bot(p);
            Map<String, Integer> now = r == null ? null : BotBags.census(r.inventory);
            int cob = 0; if (r != null) for (int i = 0; i < BotInventory.SIZE; i++) if (r.inventory.getItem(i).is(Items.COBBLESTONE)) cob++;
            PackTests.check("botbag sort keeps everything", now != null && now.equals(before) && cob == 1, "before=" + before + " after=" + now + " cobble stacks=" + cob);
            p.closeContainer();
        }), 10));
    }
}
