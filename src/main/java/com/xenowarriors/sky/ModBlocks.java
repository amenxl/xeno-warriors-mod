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

    private ModBlocks() {}
}
