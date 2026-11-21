package com.xenowarriors.recarnet;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The Underclass City, south of Aurora's wall (the user's map of Section 1 and their picture of its streets): run-down,
 * patched-up concrete houses two to four storeys high on a grid of cracked streets, boarded, barred and lit windows,
 * empty lots with rubble, orange street lamps - and the Sky Patrol's hideout, a pale warehouse at its north edge, by
 * the road up to Aurora's south gate. Drawn a column at a time by Aurora's feature (Recarnet's biome source gives this
 * area Aurora's biome so that feature runs here).
 */
final class Underclass {
    private Underclass() {}

    static final int CELL = 24, ROAD = 4, B = Recarnet.BASE_Y;
    /** The Sky Patrol's hideout: this city block. */
    static final int SP_CX = Math.floorDiv(-45, CELL), SP_CZ = Math.floorDiv(Recarnet.UNDER_Z - Recarnet.UNDER_R + 22, CELL);

    private static BlockState b(Block k) { return k.defaultBlockState(); }

    private static void set(WorldGenLevel l, BlockPos.MutableBlockPos p, int x, int y, int z, BlockState s) { l.setBlock(p.set(x, y, z), s, 2); }

    static boolean column(WorldGenLevel l, BlockPos.MutableBlockPos p, long seed, int x, int z) {
        double d = Math.sqrt(Math.pow(x - Recarnet.UNDER_X, 2) + Math.pow(z - Recarnet.UNDER_Z, 2));
        boolean corridor = Math.abs(x - Recarnet.UNDER_X) <= 4 && d > Recarnet.UNDER_R;
        int top = l.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
        int ground = d <= Recarnet.UNDER_R || corridor ? B
                : (int) Math.round(Mth.lerp(Math.min(1, (d - Recarnet.UNDER_R) / Recarnet.UNDER_BLEND), B, Math.max(top, 63)));
        int clearTo = Math.max(top, ground) + 3;
        for (int y = ground + 1; y <= clearTo; y++) set(l, p, x, y, z, b(Blocks.AIR));
        for (int y = ground - 1; y >= ground - 40 && y > l.getMinBuildHeight(); y--) {
            BlockState s = l.getBlockState(p.set(x, y, z));
            if (!s.isAir() && s.getFluidState().isEmpty() && y < top - 1) break;
            set(l, p, x, y, z, y > ground - 4 ? b(Blocks.DIRT) : b(Blocks.STONE));
        }
        if (corridor) { road(l, p, x, z); return true; }
        if (d > Recarnet.UNDER_R) { set(l, p, x, ground, z, b(Blocks.GRASS_BLOCK)); return true; }
        int lx = Math.floorMod(x, CELL), lz = Math.floorMod(z, CELL);
        int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
        long h = AuroraCityFeature.hash(seed, cx, cz, 41);
        if (lx < ROAD || lz < ROAD) { street(l, p, seed, x, z, lx, lz); return true; }
        if (cx == SP_CX && cz == SP_CZ) { hideout(l, p, x, z, lx, lz); return true; }
        // whole block inside the city? (a building cut off by the edge looks wrong: those are lots)
        double far = Math.sqrt(Math.pow(cx * CELL + CELL / 2.0 - Recarnet.UNDER_X, 2) + Math.pow(cz * CELL + CELL / 2.0 - Recarnet.UNDER_Z, 2));
        if (far > Recarnet.UNDER_R - 16 || h % 6 == 0) { lot(l, p, seed, x, z); return true; }
        house(l, p, seed, h, x, z, lx, lz);
        return true;
    }

    /** The road up from the Underclass City to Aurora's south gate. */
    static void road(WorldGenLevel l, BlockPos.MutableBlockPos p, int x, int z) {
        int ax = Math.abs(x - Recarnet.UNDER_X);
        if (ax > 3) return;
        int y = B;
        if (z > Recarnet.CITY_R) {                                               // (outside the wall the ground may sit higher: cut it back)
            for (int k = 1; k <= 6; k++) set(l, p, x, y + k, z, b(Blocks.AIR));
        }
        set(l, p, x, y, z, ax == 3 ? b(Blocks.SMOOTH_STONE) : ax == 0 && Math.floorMod(z, 6) < 3 ? b(Blocks.YELLOW_CONCRETE) : b(Blocks.BLACK_CONCRETE));
    }

    private static void street(WorldGenLevel l, BlockPos.MutableBlockPos p, long seed, int x, int z, int lx, int lz) {
        long h = AuroraCityFeature.hash(seed, x, z, 43);
        set(l, p, x, B, z, h % 9 == 0 ? b(Blocks.GRAY_CONCRETE_POWDER) : h % 13 == 0 ? b(Blocks.COBBLESTONE) : b(Blocks.BLACK_CONCRETE));
        if (lx == 0 && lz == 2 || lz == 0 && lx == 2) {                          // orange street lamps at the corners
            if (Math.floorMod(Math.floorDiv(x, CELL) + Math.floorDiv(z, CELL), 2) == 0) {
                for (int y = 1; y <= 4; y++) set(l, p, x, B + y, z, b(Blocks.POLISHED_BLACKSTONE_WALL));
                set(l, p, x, B + 5, z, b(Blocks.OCHRE_FROGLIGHT));
            }
        } else if (h % 97 == 0) set(l, p, x, B + 1, z, b(Blocks.WHITE_CARPET));   // litter
    }

    private static void lot(WorldGenLevel l, BlockPos.MutableBlockPos p, long seed, int x, int z) {
        long h = AuroraCityFeature.hash(seed, x, z, 45);
        set(l, p, x, B, z, h % 3 == 0 ? b(Blocks.COARSE_DIRT) : h % 3 == 1 ? b(Blocks.GRAVEL) : b(Blocks.DIRT));
        if (h % 29 == 0) set(l, p, x, B + 1, z, b(Blocks.COBBLESTONE));
        else if (h % 31 == 0) set(l, p, x, B + 1, z, b(Blocks.CRACKED_STONE_BRICKS));
        else if (h % 23 == 0) set(l, p, x, B + 1, z, b(Blocks.GRASS));
        else if (h % 211 == 0) set(l, p, x, B + 1, z, b(Blocks.COMPOSTER));       // a bin
    }

    private static void house(WorldGenLevel l, BlockPos.MutableBlockPos p, long seed, long h, int x, int z, int lx, int lz) {
        int m = 2 + (int) (h % 3), height = 7 + (int) ((h >> 6) % 9);
        int a = ROAD + m, bb = CELL - 1 - m;
        boolean in = lx >= a && lx <= bb && lz >= a && lz <= bb;
        set(l, p, x, B, z, in ? b(Blocks.SPRUCE_PLANKS) : b(Blocks.COARSE_DIRT));
        if (!in) return;
        BlockState[] walls = {b(Blocks.CYAN_TERRACOTTA), b(Blocks.LIGHT_GRAY_TERRACOTTA), b(Blocks.GRAY_TERRACOTTA), b(Blocks.STONE_BRICKS)};
        BlockState wall = walls[(int) ((h >> 12) % walls.length)];
        boolean ex = lx == a || lx == bb, ez = lz == a || lz == bb, edge = ex || ez;
        int mid = (a + bb) / 2, along = ex ? lz : lx;
        long wh = AuroraCityFeature.hash(seed, x, z, 47);
        for (int y = 1; y <= height; y++) {
            BlockState s;
            if (edge) {
                if (lz == bb && Math.abs(lx - mid) == 0 && y <= 2) s = b(Blocks.AIR);                       // the door (facing the street south)
                else if (y % 4 == 2 && (along - a) % 3 == 1) {                                             // windows: lit, barred, boarded or dark
                    long k = AuroraCityFeature.hash(seed, x, y * 131 + z, 49) % 5;
                    s = k == 0 ? b(Blocks.SHROOMLIGHT) : k == 1 ? b(Blocks.IRON_BARS) : k == 2 ? b(Blocks.SPRUCE_PLANKS) : b(Blocks.BLACK_STAINED_GLASS);
                } else if ((wh + y) % 17 == 0) s = b(Blocks.CRACKED_STONE_BRICKS);
                else if ((wh + y) % 23 == 0) s = b(Blocks.MOSSY_COBBLESTONE);
                else s = wall;
            } else s = y % 4 == 0 ? b(Blocks.SPRUCE_PLANKS) : b(Blocks.AIR);
            set(l, p, x, B + y, z, s);
        }
        set(l, p, x, B + height + 1, z, edge ? b(Blocks.POLISHED_ANDESITE_SLAB) : b(Blocks.GRAY_CONCRETE));
        if (lx == a + 1 && lz == a + 1 && h % 3 == 0) for (int y = 2; y <= 4; y++) set(l, p, x, B + height + y, z, b(Blocks.LIGHTNING_ROD));   // an aerial
    }

    /** The Sky Patrol's hideout: a plain pale warehouse (its inside is the story's workroom). */
    private static void hideout(WorldGenLevel l, BlockPos.MutableBlockPos p, int x, int z, int lx, int lz) {
        int a = ROAD + 2, bb = CELL - 3, h = 7;
        boolean in = lx >= a && lx <= bb && lz >= a && lz <= bb;
        set(l, p, x, B, z, in ? b(Blocks.GRAY_CONCRETE) : b(Blocks.SMOOTH_STONE));
        int mid = (a + bb) / 2;
        if (lz == bb + 1 && lx == mid + 3) sign(l, x, z);
        if (!in) return;
        boolean edge = lx == a || lx == bb || lz == a || lz == bb;
        for (int y = 1; y <= h; y++) {
            BlockState s;
            if (edge) s = lz == bb && Math.abs(lx - mid) <= 1 && y <= 3 ? b(Blocks.AIR) : y == 5 && (lx + lz) % 4 == 0 ? b(Blocks.IRON_TRAPDOOR) : b(Blocks.CALCITE);
            else if (lz == a + 1 && y == 2 && Math.abs(lx - mid) <= 3) s = b(Blocks.PACKED_MUD);       // the workbench
            else if (lz == a + 1 && y >= 3 && y <= 4 && Math.abs(lx - mid) <= 3) s = b(Blocks.BIRCH_PLANKS);   // its pegboard
            else if ((lx == a + 1 || lx == bb - 1) && y <= 5 && y % 2 == 1) s = b(Blocks.SMOOTH_STONE_SLAB);   // shelves
            else s = b(Blocks.AIR);
            set(l, p, x, B + y, z, s);
        }
        set(l, p, x, B + h + 1, z, (lx + lz) % 5 == 0 && !edge ? b(Blocks.SEA_LANTERN) : b(Blocks.LIGHT_GRAY_CONCRETE));
    }

    /** A sign by the hideout's door. */
    private static void sign(WorldGenLevel l, int x, int z) {
        {
            BlockPos at = new BlockPos(x, B + 1, z);
            l.setBlock(at, Blocks.OAK_SIGN.defaultBlockState(), 2);
            if (l.getBlockEntity(at) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sb) {
                var t = new net.minecraft.world.level.block.entity.SignText();
                String[] lines = {"", "Sky Patrol", "~ hideout ~", ""};
                for (int i = 0; i < 4; i++) t = t.setMessage(i, net.minecraft.network.chat.Component.literal(lines[i]));
                net.minecraft.world.level.block.entity.SignText.DIRECT_CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, t).result().ifPresent(tag -> {
                    net.minecraft.nbt.CompoundTag nbt = new net.minecraft.nbt.CompoundTag(); nbt.put("front_text", tag); sb.load(nbt);
                });
            }
        }
    }
}
