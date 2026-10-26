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
            Codec.INT.fieldOf("radius").forGetter(s -> s.radius),
            Biome.CODEC.fieldOf("village").forGetter(s -> s.village),
            Biome.CODEC.fieldOf("canyon").forGetter(s -> s.canyon)).apply(i, RecarnetBiomeSource::new));
    private final BiomeSource base;
    private final Holder<Biome> aurora, village, canyon;
    private final int radius;

    public RecarnetBiomeSource(BiomeSource base, Holder<Biome> aurora, int radius, Holder<Biome> village, Holder<Biome> canyon) {
        this.base = base; this.aurora = aurora; this.radius = radius; this.village = village; this.canyon = canyon;
    }

    @Override protected Codec<? extends BiomeSource> codec() { return CODEC; }

    @Override protected Stream<Holder<Biome>> collectPossibleBiomes() { return Stream.concat(base.possibleBiomes().stream(), Stream.of(aurora, village, canyon)); }

    @Override
    public Holder<Biome> getNoiseBiome(int qx, int qy, int qz, Climate.Sampler sampler) {
        long x = (long) qx << 2, z = (long) qz << 2;
        if (x * x + z * z < (long) radius * radius) return aurora;
        long vx = x - Recarnet.VILLAGE_X, vz = z - Recarnet.VILLAGE_Z;
        if (vx * vx + vz * vz < (long) (Recarnet.VILLAGE_R + Recarnet.VILLAGE_BLEND + 4) * (Recarnet.VILLAGE_R + Recarnet.VILLAGE_BLEND + 4)) return village;
        long cx = x - Recarnet.CANYON_X, cz = z - Recarnet.CANYON_Z;
        if (cx * cx + cz * cz < (long) Recarnet.CANYON_R * Recarnet.CANYON_R) return canyon;
        return base.getNoiseBiome(qx, qy, qz, sampler);
    }
}
