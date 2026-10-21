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

    /** Music disc: "Step Into This New World, Recarnet!" (2:44). */
    public static final RegistryObject<Item> MUSIC_DISC_STEP_INTO_THIS_NEW_WORLD = ITEMS.register("music_disc_step_into_this_new_world",
            () -> new net.minecraft.world.item.RecordItem(15, com.xenowarriors.ModSounds.STEP_INTO_THIS_NEW_WORLD,
                    new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC), 3289));

    /** Music disc: "The Sky Rebellion is Approaching!". */
    public static final RegistryObject<Item> MUSIC_DISC_THE_SKY_REBELLION = ITEMS.register("music_disc_the_sky_rebellion_is_approaching",
            () -> new net.minecraft.world.item.RecordItem(13, com.xenowarriors.ModSounds.THE_SKY_REBELLION,
                    new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE), 2883));

    /** Music disc: "It's Time to Get Groovy". */
    public static final RegistryObject<Item> MUSIC_DISC_GET_GROOVY = ITEMS.register("music_disc_its_time_to_get_groovy",
            () -> new net.minecraft.world.item.RecordItem(9, com.xenowarriors.ModSounds.GET_GROOVY,
                    new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE), 1554));

    /** Music disc: "Gravity Glitch Duel". */
    public static final RegistryObject<Item> MUSIC_DISC_GRAVITY_GLITCH_DUEL = ITEMS.register("music_disc_gravity_glitch_duel",
            () -> new net.minecraft.world.item.RecordItem(12, com.xenowarriors.ModSounds.GRAVITY_GLITCH_DUEL,
                    new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE), 3998));

    /** Music disc: "Lunar Ascendence". */
    public static final RegistryObject<Item> MUSIC_DISC_LUNAR_ASCENDENCE = ITEMS.register("music_disc_lunar_ascendence",
            () -> new net.minecraft.world.item.RecordItem(6, com.xenowarriors.ModSounds.LUNAR_ASCENDENCE,
                    new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE), 3306));

    /** Music disc: "Smoke Break". */
    public static final RegistryObject<Item> MUSIC_DISC_SMOKE_BREAK = ITEMS.register("music_disc_smoke_break",
            () -> new net.minecraft.world.item.RecordItem(3, com.xenowarriors.ModSounds.SMOKE_BREAK,
                    new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE), 458));

    /** Music disc: "Mimicry Eye Catcher". */
    public static final RegistryObject<Item> MUSIC_DISC_MIMICRY_EYE_CATCHER = ITEMS.register("music_disc_mimicry_eye_catcher",
            () -> new net.minecraft.world.item.RecordItem(7, com.xenowarriors.ModSounds.MIMICRY_EYE_CATCHER,
                    new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE), 308));

    /** Music disc: "SkyBound Pursuit". */
    public static final RegistryObject<Item> MUSIC_DISC_SKYBOUND_PURSUIT = ITEMS.register("music_disc_skybound_pursuit",
            () -> new net.minecraft.world.item.RecordItem(14, com.xenowarriors.ModSounds.SKYBOUND_PURSUIT,
                    new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE), 2546));

    private ModItems() {}

    public static void addToTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(KARMA_POTION);
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(FOX_WHISTLE);
            event.accept(MUSIC_DISC_ZACK_EYECATCHER);
            event.accept(MUSIC_DISC_THINKING_OF_YOU);
            event.accept(MUSIC_DISC_STEP_INTO_THIS_NEW_WORLD);
            event.accept(MUSIC_DISC_THE_SKY_REBELLION);
            event.accept(MUSIC_DISC_GET_GROOVY);
            event.accept(MUSIC_DISC_GRAVITY_GLITCH_DUEL);
            event.accept(MUSIC_DISC_LUNAR_ASCENDENCE);
            event.accept(MUSIC_DISC_SMOKE_BREAK);
            event.accept(MUSIC_DISC_MIMICRY_EYE_CATCHER);
            event.accept(MUSIC_DISC_SKYBOUND_PURSUIT);
        }
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            for (int color : KarmaHoodieItem.PRESETS) event.accept(KarmaHoodieItem.colored(KARMA_HOODIE.get(), color));
            event.accept(SR_HOODIE);
        }
    }
}
