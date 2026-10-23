package com.xenowarriors.corrupted;

import com.xenowarriors.sky.ModBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/** What Corrupted Ore turns the ground around it into. */
public final class CorruptionBlocks {
    public static final RegistryObject<Block> SOIL = ModBlocks.BLOCKS.register("corrupted_soil", () -> new CorruptedGroundBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(0.6F).sound(SoundType.SOUL_SOIL).randomTicks(), false));
    public static final RegistryObject<Block> STONE = ModBlocks.BLOCKS.register("corrupted_stone", () -> new CorruptedGroundBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_PURPLE).strength(1.5F, 6F).requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE).randomTicks(), true));

    private CorruptionBlocks() {}

    public static void init() {}
}
