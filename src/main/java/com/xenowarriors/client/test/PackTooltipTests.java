package com.xenowarriors.client.test;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Pack test part 4 (run from {@link PackTests}): Equipment Compare (+ Iceberg) - client-only tooltip mods that draw the
 * worn / held item's tooltip next to the hovered one's while Shift is held. Every Xeno Warriors item's tooltip is drawn
 * in the inventory screen with comparisons switched on (wearing our Sky Armor chestplate, holding the Senju Blade), so
 * each of ours is drawn both as the hovered item and against an equipped one; then a screenshot of an armor and a sword
 * comparison. Logs "XW-TEST pack tooltip ..." through PackTests.check.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PackTooltipTests {
    private PackTooltipTests() {}

    private static final Deque<ItemStack> QUEUE = new ArrayDeque<>();
    private static final List<String> ERRORS = new ArrayList<>();
    private static int drawn, total;
    private static ItemStack show = ItemStack.EMPTY;
    private static boolean on;

    static boolean ecLoaded() { return ModList.get().isLoaded("equipmentcompare"); }

    /** Equipment Compare's "Shift is held" switch (by reflection; the mod is optional). */
    private static String compare(boolean active) {
        try {
            Class.forName("com.anthonyhilyard.equipmentcompare.EquipmentCompare").getField("comparisonsActive").setBoolean(null, active);
            return "comparisonsActive=" + active;
        } catch (Throwable t) { return ecLoaded() ? "equipmentcompare loaded, switch not found: " + t : "equipmentcompare not loaded"; }
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (!on || !(event.getScreen() instanceof InventoryScreen)) return;
        Minecraft mc = Minecraft.getInstance();
        int x = event.getScreen().width / 2 - 40, y = event.getScreen().height / 2 - 30;
        if (!QUEUE.isEmpty()) {
            for (int i = 0; i < 20 && !QUEUE.isEmpty(); i++) {
                ItemStack s = QUEUE.poll();
                try {
                    event.getGuiGraphics().renderTooltip(mc.font, s, x, y);
                    drawn++;
                } catch (Throwable t) {
                    ERRORS.add(BuiltInRegistries.ITEM.getKey(s.getItem()) + ": " + t);
                    XenoWarriors.LOGGER.error("XW-TEST pack tooltip " + BuiltInRegistries.ITEM.getKey(s.getItem()), t);
                }
            }
        } else if (!show.isEmpty()) {
            try { event.getGuiGraphics().renderTooltip(mc.font, show, x, y); }
            catch (Throwable t) { ERRORS.add("show " + BuiltInRegistries.ITEM.getKey(show.getItem()) + ": " + t); show = ItemStack.EMPTY; }
        }
    }

    static void build(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> PackTests.server(p -> {
            p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.SKY_CHESTPLATE.get()));
            p.getInventory().selected = 0;
            p.getInventory().setItem(0, new ItemStack(ModItems.SENJU_BLADE.get()));
        }), 10));
        l.add(new Step(null, () -> PackTests.client("tooltip open", () -> {
            QUEUE.clear(); ERRORS.clear(); drawn = 0; show = ItemStack.EMPTY;
            for (Item it : BuiltInRegistries.ITEM) if (XenoWarriors.MOD_ID.equals(BuiltInRegistries.ITEM.getKey(it).getNamespace())) QUEUE.add(new ItemStack(it));
            total = QUEUE.size();
            mc.options.hideGui = false;
            mc.setScreen(new InventoryScreen(mc.player));
            PackTests.log("tooltip: " + total + " items, " + compare(true) + " iceberg=" + ModList.get().isLoaded("iceberg"));
            on = true;
        }), 5));
        for (int k = 0; k < 40; k++) l.add(new Step(null, () -> {}, 5));       // (software rendering: a few frames a second)
        l.add(new Step(null, () -> PackTests.client("tooltip all", () -> {
            if (!(mc.screen instanceof InventoryScreen)) { mc.setScreen(new InventoryScreen(mc.player)); compare(true); }
            PackTests.check("tooltip every item", QUEUE.isEmpty() && drawn == total && ERRORS.isEmpty(),
                    "drawn " + drawn + "/" + total + " left=" + QUEUE.size() + " errors=" + ERRORS.size() + (ERRORS.isEmpty() ? "" : " " + ERRORS.subList(0, Math.min(5, ERRORS.size()))));
            show = new ItemStack(ModItems.FLOWER_CHESTPLATE.get());
        }), 30));
        l.add(new Step("pack_tooltip_armor", () -> {}, 2));
        l.add(new Step(null, () -> show = new ItemStack(ModItems.HEKUBLADE.get()), 30));
        l.add(new Step("pack_tooltip_sword", () -> {}, 2));
        l.add(new Step(null, () -> PackTests.client("tooltip end", () -> {
            PackTests.check("tooltip comparisons drawn", ERRORS.isEmpty() && mc.screen instanceof InventoryScreen, "errors=" + ERRORS + " screen=" + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName()));
            on = false; show = ItemStack.EMPTY; compare(false);
            mc.setScreen(null);
        }), 5));
        l.add(new Step(null, () -> PackTests.server(p -> { p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY); p.getInventory().setItem(0, ItemStack.EMPTY); }), 5));
    }
}
