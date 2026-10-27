package com.xenowarriors.sky;

import com.xenowarriors.XenoWarriors;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, XenoWarriors.MOD_ID);

    /** Sky Ore: only in the sky islands; glows faintly; Karma Sensing can trace it. */
    public static final RegistryObject<Block> SKY_ORE = BLOCKS.register("sky_ore", () -> new DropExperienceBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(4.5F, 6F).requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST).lightLevel(s -> 5), UniformInt.of(3, 7)));

    /** Corrupted Ore: deep underground, veined with corrupted-soul purple; Karma Sensing traces it (it glows purple). */
    public static final RegistryObject<Block> CORRUPTED_ORE = BLOCKS.register("corrupted_ore", () -> new com.xenowarriors.corrupted.CorruptedOreBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(3.5F, 3F).requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE).lightLevel(s -> 3), UniformInt.of(2, 5)));

    /** Fox Shards: fox-orange crystal shards in the stone (and deepslate) of the overworld. */
    public static final RegistryObject<Block> FOX_SHARD_ORE = BLOCKS.register("fox_shard_ore", () -> new DropExperienceBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(3F, 3F).requiresCorrectToolForDrops()
                    .sound(SoundType.STONE).lightLevel(s -> 2), UniformInt.of(2, 5)));
    public static final RegistryObject<Block> DEEPSLATE_FOX_SHARD_ORE = BLOCKS.register("deepslate_fox_shard_ore", () -> new DropExperienceBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(4.5F, 3F).requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE).lightLevel(s -> 2), UniformInt.of(3, 6)));
    public static final RegistryObject<Block> FOX_SHARD_BLOCK = BLOCKS.register("fox_shard_block", () -> new Block(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(3F, 4F).requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST).lightLevel(s -> 7)));

    /** Data Ore: green-veined, found deep; Ultima's Data Sense finds it. Drops Data Shards. */
    public static final RegistryObject<Block> DATA_ORE = BLOCKS.register("data_ore", () -> new DropExperienceBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(4F, 5F).requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE).lightLevel(s -> 4), UniformInt.of(2, 5)));

    /** The Collection Book on its stand (Mountain Village Element School). */
    public static final RegistryObject<Block> COLLECTION_BOOK = BLOCKS.register("collection_book", () -> new com.xenowarriors.collection.CollectionBookBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).noOcclusion()));

    /** Yuri's VR console (VR training + challenges). */
    public static final RegistryObject<Block> VR_CONSOLE = BLOCKS.register("vr_console", () -> new com.xenowarriors.vr.VrConsoleBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GREEN).strength(3F).sound(SoundType.METAL).noOcclusion().lightLevel(s -> 9)));

    /** Bounty Board (village plazas, or craft one). */
    public static final RegistryObject<Block> BOUNTY_BOARD = BLOCKS.register("bounty_board", () -> new com.xenowarriors.bounty.BountyBoardBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2F).sound(SoundType.WOOD).noOcclusion()));

    /** Government healing pod (2 tall). Breaks loose only with a diamond pickaxe or better. */
    public static final RegistryObject<Block> HEALING_POD = BLOCKS.register("healing_pod", () -> new com.xenowarriors.gov.HealingPodBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(6F, 9F).requiresCorrectToolForDrops()
                    .sound(SoundType.METAL).lightLevel(s -> 10).noOcclusion()));

    private ModBlocks() {}
}
