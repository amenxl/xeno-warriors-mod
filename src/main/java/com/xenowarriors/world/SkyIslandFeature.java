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
        // (above 192, where water springs can't form: nothing pours off them)
        int top = Math.min(level.getMaxBuildHeight() - 30, 198 + r.nextInt(40));
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
                    // (no stone or plain dirt inside: springs of water or lava can't form in it)
                    BlockState s = y == surfaceBump ? Blocks.GRASS_BLOCK.defaultBlockState()
                            : y > surfaceBump - 3 ? Blocks.COARSE_DIRT.defaultBlockState()
                            : r.nextInt(28) == 0 ? com.xenowarriors.sky.ModBlocks.SKY_ORE.get().defaultBlockState()       // Sky Ore, only up here
                            : r.nextInt(5) == 0 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
                    setBlock(level, c.offset(dx, y, dz), s);
                }
                BlockPos above = c.offset(dx, surfaceBump + 1, dz);
                if (f > 0.15 && r.nextInt(7) == 0) setBlock(level, above, Blocks.GRASS.defaultBlockState());
                else if (f > 0.2 && r.nextInt(30) == 0)
                    setBlock(level, above, (r.nextBoolean() ? Blocks.DANDELION : Blocks.POPPY).defaultBlockState());
            }
        }
        // a little home now and then, with a chest of whatever its owner left behind
        boolean house = radius >= 8 && r.nextFloat() < 0.6F;
        if (house) house(level, c.above(), r);
        // a tree or two
        int trees = radius >= 9 ? 1 + r.nextInt(2) : r.nextInt(2);
        for (int t = 0; t < trees; t++) {
            int tx = r.nextInt(radius) - radius / 2, tz = r.nextInt(radius) - radius / 2;
            if (house && Math.abs(tx) <= 4 && Math.abs(tz) <= 4) continue;       // not through the house
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

    /** A 5x5 cottage: plank walls on log corners, a door gap, windows, a stepped roof, a bed and a chest. */
    private void house(WorldGenLevel level, BlockPos floor, RandomSource r) {
        BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState(), log = Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
        BlockState glass = Blocks.GLASS_PANE.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        // floor (also fills any dips under it)
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            setBlock(level, floor.offset(dx, -1, dz), planks);
            for (int y = -2; y >= -4; y--) if (level.isEmptyBlock(floor.offset(dx, y, dz))) setBlock(level, floor.offset(dx, y, dz), Blocks.COBBLESTONE.defaultBlockState());
        }
        for (int y = 0; y <= 2; y++) {
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2, corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                BlockPos p = floor.offset(dx, y, dz);
                if (!edge) setBlock(level, p, air);
                else if (corner) setBlock(level, p, log);
                else if (dz == -2 && dx == 0 && y < 2) setBlock(level, p, air);                     // doorway (north)
                else if (y == 1 && (dx == 0 || dz == 0)) setBlock(level, p, glass);                // windows
                else setBlock(level, p, planks);
            }
        }
        // roof: a flat layer of slabs over a plank ceiling
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2) setBlock(level, floor.offset(dx, 3, dz), planks);
            setBlock(level, floor.offset(dx, Math.abs(dx) <= 2 && Math.abs(dz) <= 2 ? 4 : 3, dz), Blocks.SPRUCE_SLAB.defaultBlockState());
        }
        // inside: a chest, a lantern, a crafting table
        BlockPos chest = floor.offset(1, 0, 1);
        setBlock(level, chest, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, net.minecraft.core.Direction.NORTH));
        net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity.setLootTable(level, r, chest,
                new net.minecraft.resources.ResourceLocation(r.nextBoolean() ? "minecraft:chests/village/village_plains_house" : "minecraft:chests/simple_dungeon"));
        setBlock(level, floor.offset(-1, 0, 1), Blocks.CRAFTING_TABLE.defaultBlockState());
        setBlock(level, floor.offset(0, 2, 0), Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
        // clear in front of the door
        setBlock(level, floor.offset(0, 0, -3), air);
        setBlock(level, floor.offset(0, 1, -3), air);
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
