package com.xenowarriors.vehicle;

import com.xenowarriors.sky.ModBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

public final class VehicleBlocks {
    public static final RegistryObject<Block> DEALER = ModBlocks.BLOCKS.register("vehicle_dealer", () -> new VehicleDealerBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(2.5F, 6F).sound(SoundType.METAL).noOcclusion()));

    private VehicleBlocks() {}

    public static void init() {}
}
