package com.xenowarriors.recarnet;

import com.mojang.serialization.Codec;
import com.xenowarriors.character.Seasons;
import com.xenowarriors.world.Clip;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.registries.RegistryObject;

/**
 * Aurora City, drawn one chunk at a time (runs in every chunk of the Aurora biome). The ground is flattened to street
 * level; a grid of 48-block city blocks with 7-wide streets (sidewalks, lane lines, street lights, cameras on poles);
 * each block is a blue-glass skyscraper, a beige mid-rise, apartments, a park, a Government facility or (Season 3) an
 * Order of Assassins cell. The center is Aurora Plaza with its fountain, there's a domed stadium, and a wall all the
 * way around with four gates.
 */
public class AuroraCityFeature extends Feature<NoneFeatureConfiguration> {
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> AURORA_CITY = com.xenowarriors.world.SkyIslandFeature.FEATURES.register("aurora_city",
            () -> new AuroraCityFeature(NoneFeatureConfiguration.CODEC));
    static final int CELL = 48, ROAD = 7, R = Recarnet.CITY_R, B = Recarnet.BASE_Y;
    static final int TOWER = 0, MIDRISE = 1, PARK = 2, APART = 3, GOV = 4, ORDER = 5, PLAZA = 6, DOME = 7, HOTEL = 8;

    public AuroraCityFeature(Codec<NoneFeatureConfiguration> c) { super(c); }

    public static void init() {}

    static long hash(long seed, int a, int b, int salt) {
        long h = seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
        h ^= h >>> 31; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 29;
        return h & Long.MAX_VALUE;
    }

    /** What stands on a city block. */
    static int type(long seed, int cx, int cz, boolean s3) {
        if (cx == 0 && cz == 0) return PLAZA;
        if (cx == -2 && cz == 1) return DOME;
        if (cx == AuroraLandmarks.HOTEL_CX && cz == AuroraLandmarks.HOTEL_CZ) return HOTEL;
        double d = Math.sqrt(Math.pow(cx * CELL + 27, 2) + Math.pow(cz * CELL + 27, 2));
        if (d > R - 30) return PARK;
        int k = (int) (hash(seed, cx, cz, 1) % 100);
        if (k < 33) return TOWER;
        if (k < 53) return MIDRISE;
        if (k < 63) return PARK;
        if (k < 75) return APART;
        if (k < 87) return GOV;
        if (k < 93) return s3 ? ORDER : MIDRISE;
        return TOWER;
    }

    private static void set(WorldGenLevel l, BlockPos.MutableBlockPos p, int x, int y, int z, BlockState s) { l.setBlock(p.set(x, y, z), s, 2); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel l = ctx.level();
        int x0 = ctx.origin().getX() & ~15, z0 = ctx.origin().getZ() & ~15;
        long seed = l.getSeed();
        boolean s3 = l.getServer() != null && Seasons.unlocked(l.getServer(), 3);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        boolean any = false;
        for (int x = x0; x < x0 + 16; x++) for (int z = z0; z < z0 + 16; z++) any |= column(l, p, seed, s3, x, z);
        if (!any) return false;
        // Government facilities and Order cells: the whole building, clipped to this chunk
        int cx = Math.floorDiv(x0, CELL), cz = Math.floorDiv(z0, CELL);
        int t = type(seed, cx, cz, s3);
        if (t == GOV || t == ORDER) {
            BlockPos c = new BlockPos(cx * CELL + 27, B + 1, cz * CELL + 27);
            Clip.set(x0, z0, x0 + 15, z0 + 15);
            try {
                RandomSource r = RandomSource.create(hash(seed, cx, cz, 7));
                if (t == GOV) com.xenowarriors.gov.GovFacilityFeature.build(l, r, c);
                else com.xenowarriors.s3.OrderHideoutFeature.build(l, r, c);
            } finally { Clip.clear(); }
        }
        AuroraLandmarks.build(l, x0, z0);                                  // Gale's Hotel, Yuri's HQ
        return true;
    }

    /** One column of the city (or of the blend ring outside the wall). */
    private boolean column(WorldGenLevel l, BlockPos.MutableBlockPos p, long seed, boolean s3, int x, int z) {
        double d = Math.sqrt((double) x * x + (double) z * z);
        if (d > R + Recarnet.BLEND) return false;
        int top = l.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
        int ground = d <= R ? B : (int) Math.round(Mth.lerp((d - R) / Recarnet.BLEND, B, Math.max(top, 63)));
        int clearTo = Math.max(top, ground) + 3;
        for (int y = ground + 1; y <= clearTo; y++) set(l, p, x, y, z, Blocks.AIR.defaultBlockState());
        for (int y = ground - 1; y >= ground - 45 && y > l.getMinBuildHeight(); y--) {
            BlockState s = l.getBlockState(p.set(x, y, z));
            if (!s.isAir() && s.getFluidState().isEmpty() && y < top - 1) break;
            set(l, p, x, y, z, y > ground - 4 && d > R ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState());
        }
        if (d > R) { set(l, p, x, ground, z, Blocks.GRASS_BLOCK.defaultBlockState()); return true; }
        if (d > R - 3) { wall(l, p, x, z, d); return true; }
        int lx = Math.floorMod(x, CELL), lz = Math.floorMod(z, CELL);
        if (lx < ROAD || lz < ROAD) { road(l, p, seed, x, z, lx, lz); return true; }
        int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
        int t = type(seed, cx, cz, s3);
        long h = hash(seed, cx, cz, 2);
        switch (t) {
            case TOWER -> tower(l, p, h, x, z, lx, lz);
            case MIDRISE -> midrise(l, p, h, x, z, lx, lz, false);
            case APART -> midrise(l, p, h, x, z, lx, lz, true);
            case PARK -> park(l, p, seed, cx, cz, x, z, lx, lz);
            case PLAZA -> plaza(l, p, x, z, lx, lz);
            case DOME -> dome(l, p, x, z, lx, lz);
            default -> set(l, p, x, B, z, (lx + lz) % 2 == 0 ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.POLISHED_ANDESITE.defaultBlockState());
        }
        return true;
    }

    private void wall(WorldGenLevel l, BlockPos.MutableBlockPos p, int x, int z, double d) {
        boolean gate = Math.abs(x) <= 4 || Math.abs(z) <= 4;
        set(l, p, x, B, z, Blocks.POLISHED_ANDESITE.defaultBlockState());
        for (int y = 1; y <= 13; y++) {
            if (gate && y <= 8) continue;
            BlockState s = y == 13 ? Blocks.GRAY_CONCRETE.defaultBlockState() : y == 7 || y == 8 ? Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState()
                    : y == 1 ? Blocks.GRAY_CONCRETE.defaultBlockState() : Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
            if (gate && y == 9) s = Blocks.CYAN_CONCRETE.defaultBlockState();
            set(l, p, x, B + y, z, s);
        }
        if (!gate && ((x * 7 + z * 13) & 31) == 0) set(l, p, x, B + 14, z, Blocks.SEA_LANTERN.defaultBlockState());
    }

    private void road(WorldGenLevel l, BlockPos.MutableBlockPos p, long seed, int x, int z, int lx, int lz) {
        boolean cross = lx < ROAD && lz < ROAD;
        int a = lx < ROAD ? lx : lz, along = lx < ROAD ? lz : lx;          // across / along the street
        BlockState g;
        if (cross) g = (lx == 1 || lx == 5 || lz == 1 || lz == 5) && (lx + lz) % 2 == 0 ? Blocks.WHITE_CONCRETE.defaultBlockState() : Blocks.GRAY_CONCRETE.defaultBlockState();
        else if (a == 0 || a == 6) g = Blocks.SMOOTH_STONE.defaultBlockState();
        else if (a == 3 && along % 6 < 3) g = Blocks.YELLOW_CONCRETE.defaultBlockState();
        else if ((a == 1 || a == 5) && along >= 7 && along <= 9) g = Blocks.WHITE_CONCRETE.defaultBlockState();   // crosswalk
        else g = Blocks.GRAY_CONCRETE.defaultBlockState();
        set(l, p, x, B, z, g);
        // street lights on the sidewalks, some with a camera on top (Aurora watches its streets)
        if (!cross && (a == 0 || a == 6) && along % 16 == 10) {
            for (int y = 1; y <= 4; y++) set(l, p, x, B + y, z, Blocks.IRON_BARS.defaultBlockState());
            set(l, p, x, B + 5, z, Blocks.SEA_LANTERN.defaultBlockState());
            if (hash(seed, x, z, 3) % 3 == 0) {
                Direction face = lx < ROAD ? (a == 0 ? Direction.EAST : Direction.WEST) : (a == 0 ? Direction.SOUTH : Direction.NORTH);
                set(l, p, x, B + 6, z, Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, face));
            }
        }
    }

    private void tower(WorldGenLevel l, BlockPos.MutableBlockPos p, long h, int x, int z, int lx, int lz) {
        int m = 3 + (int) (h % 5), height = 28 + (int) ((h >> 8) % 46);
        int a = ROAD + m, b = CELL - 1 - m;
        boolean in = lx >= a && lx <= b && lz >= a && lz <= b;
        set(l, p, x, B, z, in ? Blocks.SMOOTH_STONE.defaultBlockState() : (lx + lz) % 3 == 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState());
        if (!in) return;
        boolean ex = lx == a || lx == b, ez = lz == a || lz == b, edge = ex || ez, corner = ex && ez;
        int mid = (a + b) / 2;
        boolean door = lz == a && Math.abs(lx - mid) <= 1;
        boolean darkGlass = ((h >> 16) & 1) == 0;
        for (int y = 1; y <= height; y++) {
            BlockState s;
            if (edge) {
                if (door && y <= 3) s = y == 3 ? Blocks.CYAN_CONCRETE.defaultBlockState() : Blocks.AIR.defaultBlockState();
                else if (corner) s = Blocks.WHITE_CONCRETE.defaultBlockState();
                else if (y % 5 == 0) s = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
                else s = darkGlass ? Blocks.BLUE_STAINED_GLASS.defaultBlockState() : Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
            } else if (y % 5 == 0) s = Blocks.SMOOTH_STONE.defaultBlockState();
            else if (y % 5 == 4 && (lx - a) % 6 == 3 && (lz - a) % 6 == 3) s = Blocks.SEA_LANTERN.defaultBlockState();
            else s = Blocks.AIR.defaultBlockState();
            set(l, p, x, B + y, z, s);
        }
        set(l, p, x, B + height + 1, z, edge ? Blocks.SMOOTH_STONE_SLAB.defaultBlockState() : Blocks.GRAY_CONCRETE.defaultBlockState());
        if (lx == mid && lz == mid) {                                     // antenna with a light on top
            for (int y = 2; y <= 8; y++) set(l, p, x, B + height + y, z, Blocks.IRON_BARS.defaultBlockState());
            set(l, p, x, B + height + 9, z, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT, true));
        }
    }

    private void midrise(WorldGenLevel l, BlockPos.MutableBlockPos p, long h, int x, int z, int lx, int lz, boolean apartments) {
        int m = 4 + (int) (h % 4), height = (apartments ? 14 : 9) + (int) ((h >> 8) % 12);
        int a = ROAD + m, b = CELL - 1 - m;
        boolean in = lx >= a && lx <= b && lz >= a && lz <= b;
        set(l, p, x, B, z, in ? Blocks.STRIPPED_BIRCH_WOOD.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState());
        if (!in) {                                                        // balconies on the apartments
            boolean bal = apartments && (lx == a - 1 || lx == b + 1 || lz == a - 1 || lz == b + 1) && !(lx < a - 1 || lx > b + 1 || lz < a - 1 || lz > b + 1);
            if (bal) for (int y = 4; y < height; y += 4) {
                if (((lx + lz) / 3) % 2 == 0) { set(l, p, x, B + y, z, Blocks.SPRUCE_SLAB.defaultBlockState()); set(l, p, x, B + y + 1, z, Blocks.SPRUCE_FENCE.defaultBlockState()); }
            }
            return;
        }
        int pick = (int) ((h >> 20) % 3);
        BlockState wallS = apartments ? (pick == 0 ? Blocks.WHITE_TERRACOTTA : pick == 1 ? Blocks.TERRACOTTA : Blocks.SMOOTH_SANDSTONE).defaultBlockState()
                : (pick == 0 ? Blocks.SMOOTH_SANDSTONE : pick == 1 ? Blocks.BIRCH_PLANKS : Blocks.WHITE_TERRACOTTA).defaultBlockState();
        BlockState band = apartments ? Blocks.BROWN_TERRACOTTA.defaultBlockState() : Blocks.CUT_SANDSTONE.defaultBlockState();
        boolean ex = lx == a || lx == b, ez = lz == a || lz == b, edge = ex || ez, corner = ex && ez;
        int mid = (a + b) / 2, along = ex ? lz : lx;
        for (int y = 1; y <= height; y++) {
            BlockState s;
            if (edge) {
                if (lz == a && Math.abs(lx - mid) <= 1 && y <= 2) s = Blocks.AIR.defaultBlockState();
                else if (corner || y % 4 == 0) s = band;
                else if ((y % 4 == 2 || y % 4 == 3) && along % 3 != 0) s = Blocks.GLASS_PANE.defaultBlockState();
                else s = wallS;
            } else s = y % 4 == 0 ? Blocks.OAK_PLANKS.defaultBlockState() : y % 4 == 3 && (lx - a) % 5 == 2 && (lz - a) % 5 == 2 ? Blocks.GLOWSTONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
            set(l, p, x, B + y, z, s);
        }
        set(l, p, x, B + height + 1, z, edge ? Blocks.SANDSTONE_SLAB.defaultBlockState() : Blocks.SMOOTH_SANDSTONE.defaultBlockState());
        if (!edge && ((lx * 31 + lz * 17 + h) % 47) == 0) set(l, p, x, B + height + 2, z, Blocks.IRON_BLOCK.defaultBlockState());   // AC units
    }

    private void park(WorldGenLevel l, BlockPos.MutableBlockPos p, long seed, int cx, int cz, int x, int z, int lx, int lz) {
        boolean path = Math.abs(lx - 27) <= 1 || Math.abs(lz - 27) <= 1;
        set(l, p, x, B, z, path ? Blocks.GRAVEL.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState());
        set(l, p, x, B - 1, z, Blocks.DIRT.defaultBlockState());
        if (path) {
            if ((lx == 26 || lx == 28) && lz % 10 == 2 && Math.abs(lz - 27) > 2) set(l, p, x, B + 1, z, Blocks.SPRUCE_STAIRS.defaultBlockState());   // benches
            return;
        }
        // trees: 5 per park at fixed spots in each quarter
        for (int i = 0; i < 5; i++) {
            long th = hash(seed, cx, cz, 10 + i);
            int tx = 10 + (int) (th % 34), tz = 10 + (int) ((th >> 12) % 34);
            if (Math.abs(tx - 27) <= 2 || Math.abs(tz - 27) <= 2) continue;
            int dx = lx - tx, dz = lz - tz, dd = dx * dx + dz * dz;
            if (dd == 0) { for (int y = 1; y <= 5; y++) set(l, p, x, B + y, z, Blocks.OAK_LOG.defaultBlockState()); set(l, p, x, B + 6, z, Blocks.OAK_LEAVES.defaultBlockState()); set(l, p, x, B + 7, z, Blocks.OAK_LEAVES.defaultBlockState()); return; }
            if (dd <= 5) { for (int y = 4; y <= 6; y++) if (dd <= 2 || y == 5) set(l, p, x, B + y, z, Blocks.OAK_LEAVES.defaultBlockState()); return; }
            if (dd <= 8) { set(l, p, x, B + 5, z, Blocks.OAK_LEAVES.defaultBlockState()); return; }
        }
        long fh = hash(seed, x, z, 4) % 23;
        if (fh == 0) set(l, p, x, B + 1, z, Blocks.POPPY.defaultBlockState());
        else if (fh == 1) set(l, p, x, B + 1, z, Blocks.CORNFLOWER.defaultBlockState());
        else if (fh < 6) set(l, p, x, B + 1, z, Blocks.GRASS.defaultBlockState());
    }

    private void plaza(WorldGenLevel l, BlockPos.MutableBlockPos p, int x, int z, int lx, int lz) {
        int dx = lx - 27, dz = lz - 27;
        double r = Math.sqrt(dx * dx + dz * dz);
        set(l, p, x, B, z, ((int) r) % 4 == 0 ? Blocks.CYAN_TERRACOTTA.defaultBlockState() : Blocks.SMOOTH_QUARTZ.defaultBlockState());
        if (r < 1.5) { for (int y = 1; y <= 4; y++) set(l, p, x, B + y, z, Blocks.QUARTZ_PILLAR.defaultBlockState()); set(l, p, x, B + 5, z, Blocks.SEA_LANTERN.defaultBlockState()); set(l, p, x, B + 6, z, Blocks.WATER.defaultBlockState()); }
        else if (r < 5.5) set(l, p, x, B, z, Blocks.WATER.defaultBlockState());
        else if (r < 6.5) { set(l, p, x, B + 1, z, Blocks.QUARTZ_SLAB.defaultBlockState()); }
        else if (dx == -10 && dz == 0) set(l, p, x, B + 1, z, com.xenowarriors.sky.ModBlocks.BOUNTY_BOARD.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, net.minecraft.core.Direction.EAST));    // city Bounty Board
        else if (Math.abs(r - 15) < 0.6 && (dx + dz) % 4 == 0) { set(l, p, x, B, z, Blocks.GRASS_BLOCK.defaultBlockState()); set(l, p, x, B + 1, z, Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState()); }
        else if ((Math.abs(dx) == 18 || Math.abs(dz) == 18) && (dx + dz) % 6 == 0) { for (int y = 1; y <= 3; y++) set(l, p, x, B + y, z, Blocks.IRON_BARS.defaultBlockState()); set(l, p, x, B + 4, z, Blocks.SEA_LANTERN.defaultBlockState()); }
    }

    private void dome(WorldGenLevel l, BlockPos.MutableBlockPos p, int x, int z, int lx, int lz) {
        double R = 19, dx = lx - 27, dz = lz - 27, r = Math.sqrt(dx * dx + dz * dz);
        if (r > R) { set(l, p, x, B, z, Blocks.SMOOTH_STONE.defaultBlockState()); return; }
        int hs = 5 + (int) Math.round(Math.sqrt(R * R - r * r) * 0.55);
        boolean wallRing = r > R - 1.2;
        set(l, p, x, B, z, r < R - 6 ? (((int) dx) / 3 % 2 == 0 ? Blocks.GREEN_CONCRETE : Blocks.LIME_CONCRETE).defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState());
        if (wallRing) {
            for (int y = 1; y <= hs; y++) set(l, p, x, B + y, z, (Math.abs(dz) < 2 && dx < 0 && y <= 3) ? Blocks.AIR.defaultBlockState()
                    : y == 3 || y == 4 ? Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState() : Blocks.WHITE_CONCRETE.defaultBlockState());
            return;
        }
        if (r >= R - 6) {                                                  // stands
            int steps = (int) (r - (R - 6));
            for (int y = 1; y <= steps; y++) set(l, p, x, B + y, z, Blocks.SMOOTH_QUARTZ.defaultBlockState());
            if (steps > 0 && ((int) (Math.atan2(dz, dx) * 20)) % 3 == 0) set(l, p, x, B + steps + 1, z, Blocks.BLUE_CARPET.defaultBlockState());
        }
        set(l, p, x, B + hs, z, ((int) r) % 4 == 0 ? Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState() : Blocks.WHITE_CONCRETE.defaultBlockState());
        if (r < 2) set(l, p, x, B + hs - 1, z, Blocks.SEA_LANTERN.defaultBlockState());
    }
}
