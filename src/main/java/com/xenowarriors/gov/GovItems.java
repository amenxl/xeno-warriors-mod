package com.xenowarriors.gov;

import com.xenowarriors.item.ModItems;
import com.xenowarriors.sky.ModBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/** Government shop and loot: the supply terminal, the facility vault, the keycard and the human mod injectors. */
public final class GovItems {
    public static final RegistryObject<Block> TERMINAL = ModBlocks.BLOCKS.register("gov_terminal", () -> new GovTerminalBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(3F, 8F).sound(SoundType.METAL).noOcclusion().lightLevel(s -> 7)));
    public static final RegistryObject<Block> VAULT = ModBlocks.BLOCKS.register("gov_vault", () -> new GovVaultBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(50F, 1200F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops()));

    public static final RegistryObject<Item> TERMINAL_ITEM = ModItems.ITEMS.register("gov_terminal", () -> new BlockItem(TERMINAL.get(), new Item.Properties()));
    public static final RegistryObject<Item> VAULT_ITEM = ModItems.ITEMS.register("gov_vault", () -> new BlockItem(VAULT.get(), new Item.Properties()));
    public static final RegistryObject<Item> KEYCARD = ModItems.ITEMS.register("gov_keycard", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> SPEED_MOD = ModItems.ITEMS.register("speed_mod", () -> new GovModItem(GovModItem.SPEED, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> JUMP_MOD = ModItems.ITEMS.register("jump_mod", () -> new GovModItem(GovModItem.JUMP, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> SCANNER_MOD = ModItems.ITEMS.register("scanner_mod", () -> new GovModItem(GovModItem.SCANNER, new Item.Properties().stacksTo(16)));
    /** The Karma Scanner visor (worn on your head): Karma Levels of whoever you look at. */
    public static final RegistryObject<Item> KARMA_SCANNER = ModItems.ITEMS.register("karma_scanner", () -> new KarmaScanner.Item(KarmaScanner.Tier.BASIC, new Item.Properties().stacksTo(1)));
    /** The stronger tiers: Advanced (5 million, 64 blocks), Elite (500 million, 96), Omni (no limit, 128, never breaks). */
    public static final RegistryObject<Item> ADVANCED_KARMA_SCANNER = ModItems.ITEMS.register("advanced_karma_scanner",
            () -> new KarmaScanner.Item(KarmaScanner.Tier.ADVANCED, new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.UNCOMMON)));
    public static final RegistryObject<Item> ELITE_KARMA_SCANNER = ModItems.ITEMS.register("elite_karma_scanner",
            () -> new KarmaScanner.Item(KarmaScanner.Tier.ELITE, new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));
    public static final RegistryObject<Item> OMNI_KARMA_SCANNER = ModItems.ITEMS.register("omni_karma_scanner",
            () -> new KarmaScanner.Item(KarmaScanner.Tier.OMNI, new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC).fireResistant()));

    /** The scanner item for a tier. */
    public static Item scanner(KarmaScanner.Tier t) {
        return switch (t) { case BASIC -> KARMA_SCANNER.get(); case ADVANCED -> ADVANCED_KARMA_SCANNER.get(); case ELITE -> ELITE_KARMA_SCANNER.get(); case OMNI -> OMNI_KARMA_SCANNER.get(); };
    }
    public static final RegistryObject<Item> PLATING_MOD = ModItems.ITEMS.register("plating_mod", () -> new GovModItem(GovModItem.PLATING, new Item.Properties().stacksTo(16)));

    private GovItems() {}

    public static void init() {}
}
