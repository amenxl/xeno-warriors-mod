package com.xenowarriors.home;

import com.xenowarriors.XenoWarriors;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Block entities (the Home Chest). */
public final class HomeBlocks {
    private HomeBlocks() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, XenoWarriors.MOD_ID);

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<HomeChestBlockEntity>> HOME_CHEST = BLOCK_ENTITIES.register("home_chest",
            () -> BlockEntityType.Builder.of(HomeChestBlockEntity::new, com.xenowarriors.sky.ModBlocks.HOME_CHEST.get()).build(null));
}
