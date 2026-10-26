package com.xenowarriors.recarnet;

import com.mojang.serialization.Codec;
import com.xenowarriors.XenoWarriors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * The Recarnet dimension: the world of the show. Aurora City (Season 2) sits at its center inside its wall, with the
 * rest of the world around it - sky islands, Government facilities, Order hideouts, human towns (no Minecraft
 * villagers here: they're people), Sky Haters.
 */
public final class Recarnet {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, XenoWarriors.id("recarnet"));
    public static final DeferredRegister<Codec<? extends BiomeSource>> BIOME_SOURCES = DeferredRegister.create(Registries.BIOME_SOURCE, XenoWarriors.MOD_ID);
    public static final RegistryObject<Codec<? extends BiomeSource>> RECARNET_SOURCE = BIOME_SOURCES.register("recarnet", () -> RecarnetBiomeSource.CODEC);

    /** Aurora City: wall radius and street level. */
    public static final int CITY_R = 208, BASE_Y = 72, BLEND = 18;

    private Recarnet() {}

    public static boolean is(Level l) { return l.dimension() == LEVEL; }

    public static double dist(BlockPos p) { return Math.sqrt((double) p.getX() * p.getX() + (double) p.getZ() * p.getZ()); }

    public static boolean inAurora(Level l, BlockPos p) { return is(l) && dist(p) < CITY_R; }
}
