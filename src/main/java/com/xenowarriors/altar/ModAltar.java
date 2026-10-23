package com.xenowarriors.altar;

import com.xenowarriors.fox.ModMenus;
import com.xenowarriors.sky.ModBlocks;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.RegistryObject;

public final class ModAltar {
    public static final RegistryObject<Block> ALTAR = ModBlocks.BLOCKS.register("karma_altar", () -> new KarmaAltarBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(5F, 1200F).requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST).lightLevel(s -> 7).noOcclusion()));
    public static final RegistryObject<MenuType<KarmaAltarMenu>> MENU = ModMenus.MENUS.register("karma_altar",
            () -> IForgeMenuType.create(KarmaAltarMenu::fromNetwork));

    private ModAltar() {}

    /** Forces the class to load so the entries above are registered. */
    public static void init() {}
}
