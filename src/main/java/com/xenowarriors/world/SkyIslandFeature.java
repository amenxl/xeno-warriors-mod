package com.xenowarriors.world;

import com.mojang.serialization.Codec;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.npc.ModNpcs;
import com.xenowarriors.npc.SkylanderEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sky islands: lumps of land hanging high over the Overworld - grass on top, dirt and stone tapering to a point
 * underneath, a tree or two, and now and then a Skylander or two resting on them.
 */
public class SkyIslandFeature extends Feature<NoneFeatureConfiguration> {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES, XenoWarriors.MOD_ID);
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> SKY_ISLAND = FEATURES.register("sky_island",
            () -> new SkyIslandFeature(NoneFeatureConfiguration.CODEC));

    public SkyIslandFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource r = ctx.random();
        BlockPos origin = ctx.origin();
        int top = Math.min(level.getMaxBuildHeight() - 20, 170 + r.nextInt(51));
        int radius = 6 + r.nextInt(7);
        BlockPos c = new BlockPos(origin.getX(), top, origin.getZ());
        // a lumpy outline: radius wobbles around the edge
        float[] wob = new float[16];
        for (int i = 0; i < wob.length; i++) wob[i] = 0.8F + r.nextFloat() * 0.35F;
        for (int dx = -radius - 2; dx <= radius + 2; dx++) {
            for (int dz = -radius - 2; dz <= radius + 2; dz++) {
                double ang = Math.atan2(dz, dx);
                int i = Math.floorMod((int) Math.round(ang / (Math.PI * 2) * wob.length), wob.length);
                double edge = radius * wob[i];
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > edge) continue;
                double f = 1.0 - d / edge;
                int depth = 1 + (int) (Math.pow(f, 0.7) * radius * 1.1) + r.nextInt(2);
                int surfaceBump = f > 0.4 && r.nextInt(6) == 0 ? 1 : 0;
                for (int y = surfaceBump; y >= -depth; y--) {
                    BlockState s = y == surfaceBump ? Blocks.GRASS_BLOCK.defaultBlockState()
                            : y > surfaceBump - 3 ? Blocks.DIRT.defaultBlockState()
                            : r.nextInt(12) == 0 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.STONE.defaultBlockState();
                    setBlock(level, c.offset(dx, y, dz), s);
                }
                BlockPos above = c.offset(dx, surfaceBump + 1, dz);
                if (f > 0.15 && r.nextInt(7) == 0) setBlock(level, above, Blocks.GRASS.defaultBlockState());
                else if (f > 0.2 && r.nextInt(30) == 0)
                    setBlock(level, above, (r.nextBoolean() ? Blocks.DANDELION : Blocks.POPPY).defaultBlockState());
            }
        }
        // a tree or two
        int trees = radius >= 9 ? 1 + r.nextInt(2) : r.nextInt(2);
        for (int t = 0; t < trees; t++) {
            int tx = r.nextInt(radius) - radius / 2, tz = r.nextInt(radius) - radius / 2;
            BlockPos base = c.offset(tx, 1, tz);
            while (!level.getBlockState(base.below()).is(Blocks.GRASS_BLOCK) && base.getY() > top - 2) base = base.below();
            if (!level.getBlockState(base.below()).is(Blocks.GRASS_BLOCK)) continue;
            tree(level, base, r);
        }
        // Skylanders resting up here
        if (r.nextFloat() < 0.35F) {
            int n = 1 + r.nextInt(2);
            for (int k = 0; k < n; k++) {
                SkylanderEntity sk = ModNpcs.SKYLANDER.get().create(level.getLevel());
                if (sk == null) continue;
                sk.moveTo(c.getX() + 0.5 + r.nextInt(5) - 2, top + 2, c.getZ() + 0.5 + r.nextInt(5) - 2, r.nextFloat() * 360F, 0F);
                sk.finalizeSpawn(level, level.getCurrentDifficultyAt(c), MobSpawnType.STRUCTURE, null, null);
                sk.setPersistenceRequired();
                level.addFreshEntityWithPassengers(sk);
            }
        }
        return true;
    }

    private void tree(WorldGenLevel level, BlockPos base, RandomSource r) {
        int h = 4 + r.nextInt(2);
        for (int y = 0; y < h; y++) setBlock(level, base.above(y), Blocks.OAK_LOG.defaultBlockState());
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE, 1);
        for (int dy = h - 2; dy <= h + 1; dy++) {
            int rad = dy >= h ? 1 : 2;
            for (int dx = -rad; dx <= rad; dx++) for (int dz = -rad; dz <= rad; dz++) {
                if (Math.abs(dx) == rad && Math.abs(dz) == rad && (dy >= h || r.nextBoolean())) continue;
                BlockPos p = base.offset(dx, dy, dz);
                if (level.isEmptyBlock(p)) setBlock(level, p, leaves);
            }
        }
    }
}
