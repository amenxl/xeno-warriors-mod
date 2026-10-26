package com.xenowarriors.recarnet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.stream.Stream;

/** The normal overworld biomes everywhere, except Aurora City's biome within its radius of the center. */
public class RecarnetBiomeSource extends BiomeSource {
    public static final Codec<RecarnetBiomeSource> CODEC = RecordCodecBuilder.create(i -> i.group(
            BiomeSource.CODEC.fieldOf("base").forGetter(s -> s.base),
            Biome.CODEC.fieldOf("aurora").forGetter(s -> s.aurora),
            Codec.INT.fieldOf("radius").forGetter(s -> s.radius)).apply(i, RecarnetBiomeSource::new));
    private final BiomeSource base;
    private final Holder<Biome> aurora;
    private final int radius;

    public RecarnetBiomeSource(BiomeSource base, Holder<Biome> aurora, int radius) {
        this.base = base; this.aurora = aurora; this.radius = radius;
    }

    @Override protected Codec<? extends BiomeSource> codec() { return CODEC; }

    @Override protected Stream<Holder<Biome>> collectPossibleBiomes() { return Stream.concat(base.possibleBiomes().stream(), Stream.of(aurora)); }

    @Override
    public Holder<Biome> getNoiseBiome(int qx, int qy, int qz, Climate.Sampler sampler) {
        long x = (long) qx << 2, z = (long) qz << 2;
        if (x * x + z * z < (long) radius * radius) return aurora;
        return base.getNoiseBiome(qx, qy, qz, sampler);
    }
}
