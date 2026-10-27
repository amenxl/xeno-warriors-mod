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

    /** A long metal pole: Epic Fight's spear moves; Mikiko's last hit of a combo sends out a gust of wind. */
    public static final RegistryObject<Item> METAL_POLE = ITEMS.register("metal_pole",
            () -> new net.minecraft.world.item.SwordItem(net.minecraft.world.item.Tiers.IRON, 3, -2.8F, new Item.Properties()));

    public static final RegistryObject<Item> HUMAN_SPAWN_EGG = ITEMS.register("human_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(com.xenowarriors.npc.ModNpcs.HUMAN, 0xE8C8A8, 0x2A2A44, new Item.Properties()));
    public static final RegistryObject<Item> SKY_CRYSTAL = ITEMS.register("sky_crystal", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> SKY_ORE = ITEMS.register("sky_ore",
            () -> new net.minecraft.world.item.BlockItem(com.xenowarriors.sky.ModBlocks.SKY_ORE.get(), new Item.Properties()));
    public static final RegistryObject<Item> HEALING_POD = ITEMS.register("healing_pod",
            () -> new net.minecraft.world.item.BlockItem(com.xenowarriors.sky.ModBlocks.HEALING_POD.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> SKY_HELMET = ITEMS.register("sky_helmet",
            () -> new com.xenowarriors.sky.SkyArmorItem(net.minecraft.world.item.ArmorItem.Type.HELMET, new Item.Properties()));
    public static final RegistryObject<Item> SKY_CHESTPLATE = ITEMS.register("sky_chestplate",
            () -> new com.xenowarriors.sky.SkyArmorItem(net.minecraft.world.item.ArmorItem.Type.CHESTPLATE, new Item.Properties()));
    public static final RegistryObject<Item> SKY_LEGGINGS = ITEMS.register("sky_leggings",
            () -> new com.xenowarriors.sky.SkyArmorItem(net.minecraft.world.item.ArmorItem.Type.LEGGINGS, new Item.Properties()));
    public static final RegistryObject<Item> SKY_BOOTS = ITEMS.register("sky_boots",
            () -> new com.xenowarriors.sky.SkyArmorItem(net.minecraft.world.item.ArmorItem.Type.BOOTS, new Item.Properties()));
    public static final RegistryObject<Item> KARMA_ALTAR = ITEMS.register("karma_altar",
            () -> new net.minecraft.world.item.BlockItem(com.xenowarriors.altar.ModAltar.ALTAR.get(), new Item.Properties()));
    public static final RegistryObject<Item> CORRUPTED_SOIL = ITEMS.register("corrupted_soil",
            () -> new net.minecraft.world.item.BlockItem(com.xenowarriors.corrupted.CorruptionBlocks.SOIL.get(), new Item.Properties()));
    public static final RegistryObject<Item> CORRUPTED_STONE = ITEMS.register("corrupted_stone",
            () -> new net.minecraft.world.item.BlockItem(com.xenowarriors.corrupted.CorruptionBlocks.STONE.get(), new Item.Properties()));
    public static final RegistryObject<Item> CORRUPTED_SHARD = ITEMS.register("corrupted_shard", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> CORRUPTED_ORE = ITEMS.register("corrupted_ore",
            () -> new net.minecraft.world.item.BlockItem(com.xenowarriors.sky.ModBlocks.CORRUPTED_ORE.get(), new Item.Properties()));
    public static final RegistryObject<Item> CORRUPTED_HELMET = ITEMS.register("corrupted_helmet",
            () -> new com.xenowarriors.sky.CorruptedArmorItem(net.minecraft.world.item.ArmorItem.Type.HELMET, new Item.Properties()));
    public static final RegistryObject<Item> CORRUPTED_CHESTPLATE = ITEMS.register("corrupted_chestplate",
            () -> new com.xenowarriors.sky.CorruptedArmorItem(net.minecraft.world.item.ArmorItem.Type.CHESTPLATE, new Item.Properties()));
    public static final RegistryObject<Item> CORRUPTED_LEGGINGS = ITEMS.register("corrupted_leggings",
            () -> new com.xenowarriors.sky.CorruptedArmorItem(net.minecraft.world.item.ArmorItem.Type.LEGGINGS, new Item.Properties()));
    public static final RegistryObject<Item> CORRUPTED_BOOTS = ITEMS.register("corrupted_boots",
            () -> new com.xenowarriors.sky.CorruptedArmorItem(net.minecraft.world.item.ArmorItem.Type.BOOTS, new Item.Properties()));
    public static final RegistryObject<Item> MAGICAL_ORB = ITEMS.register("magical_orb", () -> new com.xenowarriors.item.recarnet.MagicalOrbItem(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistryObject<Item> DATA_CAPSULE = ITEMS.register("data_capsule", () -> new com.xenowarriors.item.recarnet.DataCapsuleItem(new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> TRACKER_PENDANT = ITEMS.register("tracker_pendant", () -> new com.xenowarriors.item.recarnet.TrackerPendantItem(new Item.Properties().stacksTo(1)));
    /** Senju's Blade: the SR leader's long sword (he drops it sometimes). */
    public static final RegistryObject<Item> SENJU_BLADE = ITEMS.register("senju_blade", () -> new net.minecraft.world.item.SwordItem(net.minecraft.world.item.Tiers.NETHERITE, 5, -2.6F,
            new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC).fireResistant()));
    /** Logan's long sword and Keita's slim curved one (they drop them sometimes). */
    public static final RegistryObject<Item> LOGAN_BLADE = ITEMS.register("logan_blade", () -> new net.minecraft.world.item.SwordItem(net.minecraft.world.item.Tiers.DIAMOND, 5, -2.7F,
            new Item.Properties().rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> KEITA_BLADE = ITEMS.register("keita_blade", () -> new net.minecraft.world.item.SwordItem(net.minecraft.world.item.Tiers.DIAMOND, 4, -2.2F,
            new Item.Properties().rarity(net.minecraft.world.item.Rarity.RARE)));
    /** Zane's broom staff, Max's serrated knife and Sam's bat. */
    public static final RegistryObject<Item> ZANE_BROOM = ITEMS.register("zane_broom", () -> new net.minecraft.world.item.SwordItem(net.minecraft.world.item.Tiers.IRON, 3, -2.6F,
            new Item.Properties().rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> MAX_KNIFE = ITEMS.register("max_knife", () -> new net.minecraft.world.item.SwordItem(net.minecraft.world.item.Tiers.DIAMOND, 2, -1.8F,
            new Item.Properties().rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> SAM_BAT = ITEMS.register("sam_bat", () -> new net.minecraft.world.item.SwordItem(net.minecraft.world.item.Tiers.IRON, 5, -3.0F,
            new Item.Properties().rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> FOX_KING_CROWN = ITEMS.register("fox_king_crown", () -> new com.xenowarriors.item.recarnet.FoxKingCrownItem(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistryObject<Item> FACILITY_MAP = ITEMS.register("facility_map", () -> new com.xenowarriors.item.recarnet.FacilityMapItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> VR_CONSOLE = ITEMS.register("vr_console", () -> new net.minecraft.world.item.BlockItem(com.xenowarriors.sky.ModBlocks.VR_CONSOLE.get(), new Item.Properties()));
    public static final RegistryObject<Item> BOUNTY_BOARD = ITEMS.register("bounty_board", () -> new net.minecraft.world.item.BlockItem(com.xenowarriors.sky.ModBlocks.BOUNTY_BOARD.get(), new Item.Properties()));
    public static final RegistryObject<Item> COLLECTION_BOOK = ITEMS.register("collection_book", () -> new net.minecraft.world.item.BlockItem(com.xenowarriors.sky.ModBlocks.COLLECTION_BOOK.get(), new Item.Properties()));
    public static final RegistryObject<Item> DATA_ORE = ITEMS.register("data_ore", () -> new net.minecraft.world.item.BlockItem(com.xenowarriors.sky.ModBlocks.DATA_ORE.get(), new Item.Properties()));
    public static final RegistryObject<Item> DATA_SHARD = ITEMS.register("data_shard", () -> new com.xenowarriors.ultima.DataShardItem(new Item.Properties()));
    /** Human modification chip (Gov C2 tech) - made from Data Shards; for modded characters like Kyle later. */
    public static final RegistryObject<Item> MOD_CHIP = ITEMS.register("mod_chip", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> ROOM_TECH = ITEMS.register("room_tech", () -> new com.xenowarriors.room.RoomTechItem(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistryObject<Item> RECARNET_PASS = ITEMS.register("recarnet_pass", () -> new com.xenowarriors.recarnet.RecarnetPassItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> AURORA_MEDICINE = ITEMS.register("aurora_medicine", () -> new com.xenowarriors.virus.AuroraMedicineItem(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> PHONE = ITEMS.register("phone", () -> new PhoneItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> BRIEFCASE = ITEMS.register("briefcase", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> SKYLANDER_SPAWN_EGG = ITEMS.register("skylander_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(com.xenowarriors.npc.ModNpcs.SKYLANDER, 0xF2F2FF, 0x5EA8E8, new Item.Properties()));
    public static final RegistryObject<Item> DARK_POSSESSED_SPAWN_EGG = ITEMS.register("dark_possessed_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(com.xenowarriors.npc.ModNpcs.DARK_POSSESSED, 0x141016, 0xE01818, new Item.Properties()));

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

    /** Music disc: "Virtual Grind". */
    public static final RegistryObject<Item> MUSIC_DISC_VIRTUAL_GRIND = ITEMS.register("music_disc_virtual_grind",
            () -> new net.minecraft.world.item.RecordItem(10, com.xenowarriors.ModSounds.VIRTUAL_GRIND,
                    new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE), 1807));

    /** Music disc: "Corrupted Pulse". */
    public static final RegistryObject<Item> MUSIC_DISC_CORRUPTED_PULSE = ITEMS.register("music_disc_corrupted_pulse",
            () -> new net.minecraft.world.item.RecordItem(5, com.xenowarriors.ModSounds.CORRUPTED_PULSE,
                    new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE), 2923));

    /** Music disc: "Max's Chill Day". */
    public static final RegistryObject<Item> MUSIC_DISC_MAXS_CHILL_DAY = ITEMS.register("music_disc_maxs_chill_day",
            () -> new net.minecraft.world.item.RecordItem(2, com.xenowarriors.ModSounds.MAXS_CHILL_DAY,
                    new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE), 1413));

    /** Music disc: "Crown for Heroine". */
    public static final RegistryObject<Item> MUSIC_DISC_CROWN_FOR_HEROINE = ITEMS.register("music_disc_crown_for_heroine",
            () -> new net.minecraft.world.item.RecordItem(8, com.xenowarriors.ModSounds.CROWN_FOR_HEROINE,
                    new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE), 516));

    // ---------------------------------------------------------------- the mod's own creative tab
    public static final RegistryObject<Item> CORRUPTED_VILLAGER_SPAWN_EGG = ITEMS.register("corrupted_villager_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(com.xenowarriors.corrupted.ModCorrupted.CORRUPTED_VILLAGER,
                    0x3B2A4A, 0xB04CFF, new Item.Properties()));
    public static final RegistryObject<Item> C_SOUL_SPAWN_EGG = ITEMS.register("c_soul_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(com.xenowarriors.corrupted.ModCorrupted.C_SOUL,
                    0x0A0010, 0xFF3060, new Item.Properties()));
    public static final RegistryObject<Item> CORRUPTED_SOUL_SPAWN_EGG = ITEMS.register("corrupted_soul_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(com.xenowarriors.corrupted.ModCorrupted.CORRUPTED_SOUL,
                    0x14081F, 0xD77BFF, new Item.Properties()));

    public static final DeferredRegister<net.minecraft.world.item.CreativeModeTab> TABS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, XenoWarriors.MOD_ID);

    /**
     * "Xeno Warriors": every item from this mod, filled in automatically from the item list (so anything new
     * shows up here by itself), with all the Karma hoodie colors.
     */
    public static final RegistryObject<net.minecraft.world.item.CreativeModeTab> TAB = TABS.register("xenowarriors",
            () -> net.minecraft.world.item.CreativeModeTab.builder()
                    .title(net.minecraft.network.chat.Component.translatable("itemGroup.xenowarriors"))
                    .icon(() -> new net.minecraft.world.item.ItemStack(HEKUBLADE.get()))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .displayItems((params, out) -> {
                        for (RegistryObject<Item> entry : ITEMS.getEntries()) {
                            Item item = entry.get();
                            if (item == PHONE.get()) {
                                for (int st = 0; st < PhoneItem.STYLES.length; st++)
                                    for (int color : PhoneItem.COLORS) out.accept(PhoneItem.make(item, st, color));
                            } else if (item == KARMA_HOODIE.get()) {
                                for (int color : KarmaHoodieItem.PRESETS) out.accept(KarmaHoodieItem.colored(item, color));
                            } else {
                                out.accept(item);
                            }
                        }
                    })
                    .build());

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
            event.accept(MUSIC_DISC_VIRTUAL_GRIND);
            event.accept(MUSIC_DISC_CORRUPTED_PULSE);
            event.accept(MUSIC_DISC_MAXS_CHILL_DAY);
            event.accept(MUSIC_DISC_CROWN_FOR_HEROINE);
        }
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            for (int color : KarmaHoodieItem.PRESETS) event.accept(KarmaHoodieItem.colored(KARMA_HOODIE.get(), color));
            event.accept(SR_HOODIE);
            event.accept(SENJU_BLADE);
            event.accept(LOGAN_BLADE);
            event.accept(KEITA_BLADE);
            event.accept(ZANE_BROOM);
            event.accept(MAX_KNIFE);
            event.accept(SAM_BAT);
        }
    }
}
