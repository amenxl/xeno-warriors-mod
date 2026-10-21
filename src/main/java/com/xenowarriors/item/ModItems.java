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

    public static final RegistryObject<Item> SR_HOODIE = ITEMS.register("sr_hoodie",
            () -> new SrHoodieItem(new Item.Properties()));

    public static final RegistryObject<Item> FOX_WHISTLE = ITEMS.register("fox_whistle",
            () -> new FoxWhistleItem(new Item.Properties().stacksTo(1)));

    /** Music disc: "Zack Eyecatcher" (42.5 seconds). */
    public static final RegistryObject<Item> MUSIC_DISC_ZACK_EYECATCHER = ITEMS.register("music_disc_zack_eyecatcher",
            () -> new net.minecraft.world.item.RecordItem(11, com.xenowarriors.ModSounds.ZACK_EYECATCHER,
                    new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE), 850));

    /** Music disc: "Thinking of You" (3:15). */
    public static final RegistryObject<Item> MUSIC_DISC_THINKING_OF_YOU = ITEMS.register("music_disc_thinking_of_you",
            () -> new net.minecraft.world.item.RecordItem(4, com.xenowarriors.ModSounds.THINKING_OF_YOU,
                    new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE), 3902));

    private ModItems() {}

    public static void addToTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(KARMA_POTION);
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(FOX_WHISTLE);
            event.accept(MUSIC_DISC_ZACK_EYECATCHER);
            event.accept(MUSIC_DISC_THINKING_OF_YOU);
        }
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            for (int color : KarmaHoodieItem.PRESETS) event.accept(KarmaHoodieItem.colored(KARMA_HOODIE.get(), color));
            event.accept(SR_HOODIE);
        }
    }
}
