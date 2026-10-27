package com.xenowarriors.sr;

import com.mojang.serialization.Codec;
import com.xenowarriors.XenoWarriors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.registries.RegistryObject;

/**
 * A Sky Rebellion camp: two black tents, a campfire, supply barrels, a loot chest and a few SR members on watch.
 * Fits in one chunk.
 */
public class SrCampFeature extends Feature<NoneFeatureConfiguration> {
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> SR_CAMP = com.xenowarriors.world.SkyIslandFeature.FEATURES.register("sr_camp",
            () -> new SrCampFeature(NoneFeatureConfiguration.CODEC));
    private static final ResourceLocation LOOT = XenoWarriors.id("chests/sr_camp");

    public SrCampFeature(Codec<NoneFeatureConfiguration> c) { super(c); }

    public static void init() {}

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel l = ctx.level();
        BlockPos o = ctx.origin();
        int cx = (o.getX() & ~15) + 8, cz = (o.getZ() & ~15) + 8;
        int lo = 999, hi = -999;
        for (int dx = -6; dx <= 6; dx += 3) for (int dz = -6; dz <= 6; dz += 3) {
            int y = l.getHeight(Heightmap.Types.WORLD_SURFACE_WG, cx + dx, cz + dz);
            lo = Math.min(lo, y); hi = Math.max(hi, y);
        }
        if (hi - lo > 3) return false;
        BlockPos b = new BlockPos(cx, lo, cz);
        if (!l.getBlockState(b.below()).getFluidState().isEmpty()) return false;
        build(l, ctx.random(), b);
        return true;
    }

    private static void set(WorldGenLevel l, BlockPos p, BlockState s) { l.setBlock(p, s, 2); }

    public static void build(WorldGenLevel l, RandomSource r, BlockPos b) {
        // clear and level the ground
        for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) {
            set(l, b.offset(dx, -1, dz), (dx * dx + dz * dz < 20) ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState());
            for (int y = 0; y <= 5; y++) set(l, b.offset(dx, y, dz), Blocks.AIR.defaultBlockState());
            set(l, b.offset(dx, -2, dz), Blocks.DIRT.defaultBlockState());
        }
        set(l, b, Blocks.CAMPFIRE.defaultBlockState());
        for (Direction d : Direction.Plane.HORIZONTAL) set(l, b.relative(d, 2), Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, d.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X));
        tent(l, b.offset(-5, 0, -4));
        tent(l, b.offset(3, 0, -4));
        // supplies
        set(l, b.offset(-5, 0, 4), Blocks.BARREL.defaultBlockState());
        set(l, b.offset(-4, 0, 5), Blocks.BARREL.defaultBlockState());
        set(l, b.offset(-5, 1, 4), Blocks.BARREL.defaultBlockState());
        set(l, b.offset(4, 0, 4), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST));
        if (l.getBlockEntity(b.offset(4, 0, 4)) instanceof RandomizableContainerBlockEntity be) be.setLootTable(LOOT, r.nextLong());
        // the banner pole with the rebellion's sign
        for (int y = 0; y <= 3; y++) set(l, b.offset(0, y, 5), Blocks.DARK_OAK_FENCE.defaultBlockState());
        set(l, b.offset(0, 4, 5), Blocks.BLACK_WOOL.defaultBlockState());
        set(l, b.offset(0, 3, 4), Blocks.BLACK_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, Direction.NORTH));
        set(l, b.offset(0, 1, 4), Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.NORTH));
        if (l.getBlockEntity(b.offset(0, 1, 4)) instanceof SignBlockEntity s) {
            SignText t = new SignText().setMessage(0, Component.literal("SKY")).setMessage(1, Component.literal("REBELLION")).setMessage(2, Component.literal("For Father."));
            SignText.DIRECT_CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, t).result().ifPresent(tag -> {
                net.minecraft.nbt.CompoundTag nbt = new net.minecraft.nbt.CompoundTag(); nbt.put("front_text", tag); s.load(nbt);
            });
        }
        // members on watch
        int n = 3 + r.nextInt(2);
        for (int i = 0; i < n; i++) {
            SrMemberEntity m = SrEntities.SR_MEMBER.get().create(l.getLevel());
            if (m == null) continue;
            double a = i * Math.PI * 2 / n;
            BlockPos at = b.offset((int) Math.round(Math.cos(a) * 3.5), 0, (int) Math.round(Math.sin(a) * 3.5));
            m.moveTo(at.getX() + .5, at.getY(), at.getZ() + .5, r.nextFloat() * 360F, 0);
            m.finalizeSpawn(l, l.getCurrentDifficultyAt(at), MobSpawnType.STRUCTURE, null, null);
            m.setup(r.nextInt(2), b);
            l.addFreshEntityWithPassengers(m);
        }
        // sometimes one of the named members leads the camp (Senju, the leader himself, is the rarest)
        if (r.nextFloat() < 0.4F) {
            SrMemberEntity boss = SrEntities.SR_MEMBER.get().create(l.getLevel());
            if (boss != null) {
                int pick = r.nextInt(13);
                int idx = pick == 0 ? 0 : 1 + (pick - 1) % (SrMemberEntity.NAMED.length - 1);
                boss.moveTo(b.getX() + .5, b.getY(), b.getZ() + 1.5, r.nextFloat() * 360F, 0);
                boss.finalizeSpawn(l, l.getCurrentDifficultyAt(b), MobSpawnType.STRUCTURE, null, null);
                boss.setup(2 + idx, b);
                l.addFreshEntityWithPassengers(boss);
            }
        }
    }

    /** A small black A-frame tent, 3 wide, 4 deep, opening south. */
    private static void tent(WorldGenLevel l, BlockPos c) {
        BlockState w = Blocks.BLACK_WOOL.defaultBlockState(), g = Blocks.GRAY_WOOL.defaultBlockState();
        for (int z = 0; z < 4; z++) {
            for (int x = 0; x <= 2; x++) set(l, c.offset(x, -1, z), Blocks.SPRUCE_PLANKS.defaultBlockState());
            set(l, c.offset(0, 0, z), z == 3 ? Blocks.AIR.defaultBlockState() : w);
            set(l, c.offset(2, 0, z), z == 3 ? Blocks.AIR.defaultBlockState() : w);
            set(l, c.offset(0, 1, z), z == 3 ? Blocks.AIR.defaultBlockState() : g);
            set(l, c.offset(2, 1, z), z == 3 ? Blocks.AIR.defaultBlockState() : g);
            set(l, c.offset(1, 2, z), w);
        }
        for (int x = 0; x <= 2; x++) set(l, c.offset(x, 0, 0), w);
        set(l, c.offset(1, 1, 0), w);
        set(l, c.offset(1, 0, 1), Blocks.BLACK_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
        set(l, c.offset(1, 0, 2), Blocks.BLACK_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
    }
}
