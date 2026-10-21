package com.xenowarriors.item;

import com.xenowarriors.XenoWarriors;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, XenoWarriors.MOD_ID);

    public static final RegistryObject<Item> KARMA_POTION = ITEMS.register("karma_potion",
            () -> new KarmaPotionItem(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Item> HEKUBLADE = ITEMS.register("hekublade",
            () -> new HekubladeItem(new Item.Properties().stacksTo(1).fireResistant()));

    public static final RegistryObject<Item> KARMA_HOODIE = ITEMS.register("karma_hoodie",
            () -> new KarmaHoodieItem(new Item.Properties()));

    private ModItems() {}

    public static void addToTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(KARMA_POTION);
        }
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            for (int color : KarmaHoodieItem.PRESETS) event.accept(KarmaHoodieItem.colored(KARMA_HOODIE.get(), color));
        }
    }
}
