package com.xenowarriors.recarnet;

import com.mojang.serialization.Codec;
import com.xenowarriors.npc.ModNpcs;
import com.xenowarriors.npc.SkylanderEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * The Royal Capital, Section 1: Skylander islands floating high above Aurora, above the clouds. Built like the show
 * describes Rhalix's kingdom:
 * <ul>
 * <li>the Palace island in the middle: the royal palace (throne room with the two thrones, library upstairs, corner
 * towers, a glass dome), gardens with cherry trees, a fountain and the landing platform;</li>
 * <li>the Market island (stalls and shops) with the Black Market hidden in a cavern under it;</li>
 * <li>SkyHigh, the Skylander school, and the Royal Knights' barracks and training yard;</li>
 * <li>the Noble island with villas;</li>
 * <li>the Commoner Islands further out and lower down: run-down shacks and little farms;</li>
 * <li>giant chains holding everything together, quartz bridges between the inner islands and rope bridges out to the
 * commoners.</li>
 * </ul>
 * Everything is fixed (not seed-based), drawn one chunk at a time: each piece knows its bounding box and only the pieces
 * touching a chunk are drawn into it.
 */
public class RoyalCapitalFeature extends Feature<NoneFeatureConfiguration> {
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> ROYAL_CAPITAL = com.xenowarriors.world.SkyIslandFeature.FEATURES.register("royal_capital",
            () -> new RoyalCapitalFeature(NoneFeatureConfiguration.CODEC));

    /** Below this height you're in Aurora, not the Royal Capital. */
    public static final int FLOOR_Y = 200;
    public static final int R = 236;
    /** Where the Recarnet Pass drops you: the landing platform south of the palace. */
    public static final BlockPos LANDING = new BlockPos(0, 253, 47);

    public static void init() {}

    public RoyalCapitalFeature(Codec<NoneFeatureConfiguration> c) { super(c); }

    // ------------------------------------------------------------------ layout

    enum Kind { PALACE, MARKET, SCHOOL, BARRACKS, NOBLE, COMMONER }
    record Island(Kind kind, int x, int z, int r, int top, int salt) {}

    static final Island PALACE = new Island(Kind.PALACE, 0, 0, 52, 252, 1);
    static final Island MARKET = new Island(Kind.MARKET, 104, -38, 32, 248, 2);
    static final Island SCHOOL = new Island(Kind.SCHOOL, -100, -46, 30, 249, 3);
    static final Island BARRACKS = new Island(Kind.BARRACKS, -64, 98, 28, 246, 4);
    static final Island NOBLE = new Island(Kind.NOBLE, 84, 98, 26, 249, 5);
    static final Island[] COMMONERS = {
            new Island(Kind.COMMONER, 186, 46, 20, 233, 11),
            new Island(Kind.COMMONER, 152, -140, 18, 229, 12),
            new Island(Kind.COMMONER, -188, -6, 21, 231, 13),
            new Island(Kind.COMMONER, -128, -158, 17, 227, 14),
            new Island(Kind.COMMONER, 26, 200, 19, 230, 15),
    };
    static final Island[] ALL;
    static {
        List<Island> a = new ArrayList<>(List.of(PALACE, MARKET, SCHOOL, BARRACKS, NOBLE));
        a.addAll(List.of(COMMONERS));
        ALL = a.toArray(new Island[0]);
    }

    record Piece(int x0, int z0, int x1, int z1, Consumer<RoyalCapitalFeature> draw) {}
    private static List<Piece> pieces;

    private static synchronized List<Piece> pieces() {
        if (pieces != null) return pieces;
        List<Piece> l = new ArrayList<>();
        for (Island i : ALL) {
            int m = i.r + 4;
            l.add(new Piece(i.x - m, i.z - m, i.x + m, i.z + m, f -> f.island(i)));
        }
        // the inner islands: quartz bridges from the palace, giant chains under them
        for (Island i : new Island[]{MARKET, SCHOOL, BARRACKS, NOBLE}) link(l, PALACE, i, true);
        link(l, MARKET, NOBLE, true);
        // the Commoner Islands: chained to the nearest inner island, with a rope bridge to walk over
        link(l, NOBLE, COMMONERS[0], false);
        link(l, MARKET, COMMONERS[1], false);
        link(l, SCHOOL, COMMONERS[2], false);
        link(l, SCHOOL, COMMONERS[3], false);
        link(l, BARRACKS, COMMONERS[4], false);
        pieces = l;
        return l;
    }

    private static void link(List<Piece> l, Island a, Island b, boolean royal) {
        double dx = b.x - a.x, dz = b.z - a.z, len = Math.sqrt(dx * dx + dz * dz);
        double ux = dx / len, uz = dz / len;
        int ax = (int) Math.round(a.x + ux * (a.r - 3)), az = (int) Math.round(a.z + uz * (a.r - 3));
        int bx = (int) Math.round(b.x - ux * (b.r - 3)), bz = (int) Math.round(b.z - uz * (b.r - 3));
        int x0 = Math.min(ax, bx) - 6, x1 = Math.max(ax, bx) + 6, z0 = Math.min(az, bz) - 6, z1 = Math.max(az, bz) + 6;
        l.add(new Piece(x0, z0, x1, z1, f -> f.bridge(ax, a.top, az, bx, b.top, bz, royal)));
        // two chains, one each side, hanging under the bridge
        double px = -uz, pz = ux;
        for (int s : new int[]{-1, 1}) {
            int cax = (int) Math.round(ax + px * s * 4), caz = (int) Math.round(az + pz * s * 4);
            int cbx = (int) Math.round(bx + px * s * 4), cbz = (int) Math.round(bz + pz * s * 4);
            l.add(new Piece(Math.min(cax, cbx) - 6, Math.min(caz, cbz) - 6, Math.max(cax, cbx) + 6, Math.max(caz, cbz) + 6,
                    f -> f.chain(cax, a.top - 3, caz, cbx, b.top - 3, cbz, royal ? 7 : 10)));
        }
    }

    // ------------------------------------------------------------------ drawing (into one chunk)

    private WorldGenLevel l;
    private int cx0, cz0;
    private final BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();

    @Override
    public synchronized boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {   // (its drawing state lives in fields: chunks generating in parallel must take turns)
        if (!Recarnet.is(ctx.level().getLevel())) return false;
        l = ctx.level();
        cx0 = ctx.origin().getX() & ~15; cz0 = ctx.origin().getZ() & ~15;
        if (Math.abs(cx0) > R + 32 || Math.abs(cz0) > R + 32) return false;
        boolean any = false;
        for (Piece p : pieces()) {
            if (p.x1 < cx0 || p.x0 > cx0 + 15 || p.z1 < cz0 || p.z0 > cz0 + 15) continue;
            p.draw.accept(this);
            any = true;
        }
        for (Island i : ALL) if (in(i.x, i.z)) people(i);
        for (int[] g : new int[][]{{-3, LANDING.getZ() - 5}, {3, LANDING.getZ() - 5}, {-6, 9}, {6, 9}}) if (in(g[0], g[1])) guard(g[0], PALACE.top + 1, g[1]);
        return any;
    }

    private boolean in(int x, int z) { return x >= cx0 && x < cx0 + 16 && z >= cz0 && z < cz0 + 16; }

    private void set(int x, int y, int z, BlockState s) {
        if (!in(x, z) || y < l.getMinBuildHeight() || y >= l.getMaxBuildHeight()) return;
        l.setBlock(mp.set(x, y, z), s, 2);
    }

    private void set(int x, int y, int z, Block b) { set(x, y, z, b.defaultBlockState()); }

    private void box(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        for (int x = Math.max(x0, cx0); x <= Math.min(x1, cx0 + 15); x++)
            for (int z = Math.max(z0, cz0); z <= Math.min(z1, cz0 + 15); z++)
                for (int y = y0; y <= y1; y++) set(x, y, z, s);
    }

    private void box(int x0, int y0, int z0, int x1, int y1, int z1, Block b) { box(x0, y0, z0, x1, y1, z1, b.defaultBlockState()); }

    /** Four walls (no floor or roof). */
    private void walls(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        box(x0, y0, z0, x1, y1, z0, s); box(x0, y0, z1, x1, y1, z1, s);
        box(x0, y0, z0, x0, y1, z1, s); box(x1, y0, z0, x1, y1, z1, s);
    }

    private static BlockState stair(Block b, Direction facing) { return b.defaultBlockState().setValue(StairBlock.FACING, facing); }

    private static BlockState slab(Block b, boolean top) { return b.defaultBlockState().setValue(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM); }

    private static BlockState leaves(Block b) { return b.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true); }

    private static int hash(int a, int b, int c) {
        int h = a * 73856093 ^ b * 19349663 ^ c * 83492791;
        h ^= h >>> 13; h *= 0x5bd1e995; h ^= h >>> 15;
        return h & 0x7fffffff;
    }

    private void lamp(int x, int y, int z) {
        for (int k = 1; k <= 3; k++) set(x, y + k, z, Blocks.QUARTZ_PILLAR);
        set(x, y + 4, z, Blocks.LANTERN);
    }

    // ------------------------------------------------------------------ islands

    /** Edge wobble, so the islands aren't perfect circles. */
    private static double edge(Island i, double ang) {
        return i.r * (1 + 0.06 * Math.sin(ang * 3 + i.salt) + 0.04 * Math.sin(ang * 7 + i.salt * 2.3));
    }

    private void island(Island i) {
        boolean poor = i.kind == Kind.COMMONER;
        for (int x = Math.max(i.x - i.r - 4, cx0); x <= Math.min(i.x + i.r + 4, cx0 + 15); x++)
            for (int z = Math.max(i.z - i.r - 4, cz0); z <= Math.min(i.z + i.r + 4, cz0 + 15); z++) {
                double dx = x - i.x, dz = z - i.z, d = Math.sqrt(dx * dx + dz * dz), e = edge(i, Math.atan2(dz, dx));
                if (d > e) continue;
                double f = 1 - d / e;
                int depth = 3 + (int) (Math.pow(f, 0.7) * i.r * 0.95) + hash(x, z, i.salt) % 3;
                for (int k = 0; k <= depth; k++) {
                    int y = i.top - k;
                    BlockState s;
                    int h = hash(x, y, z);
                    if (k == 0) s = top(i, x, z, d, e);
                    else if (k < 3) s = poor ? (h % 5 == 0 ? Blocks.COARSE_DIRT : Blocks.DIRT).defaultBlockState() : Blocks.DIRT.defaultBlockState();
                    else if (h % 23 == 0) s = com.xenowarriors.sky.ModBlocks.SKY_ORE.get().defaultBlockState();
                    else s = (poor ? (h % 4 == 0 ? Blocks.MOSSY_COBBLESTONE : Blocks.STONE) : (h % 5 == 0 ? Blocks.CALCITE : Blocks.STONE)).defaultBlockState();
                    set(x, y, z, s);
                }
                // the lifting crystal under the big islands: a glowing sky-glass core at the bottom
                if (!poor && d < i.r * 0.18) {
                    int bottom = i.top - depth;
                    set(x, bottom - 1, z, d < i.r * 0.08 ? Blocks.SEA_LANTERN : Blocks.LIGHT_BLUE_STAINED_GLASS);
                    if (d < i.r * 0.08) set(x, bottom - 2, z, Blocks.LIGHT_BLUE_STAINED_GLASS);
                }
            }
        switch (i.kind) {
            case PALACE -> palaceIsland(i);
            case MARKET -> marketIsland(i);
            case SCHOOL -> schoolIsland(i);
            case BARRACKS -> barracksIsland(i);
            case NOBLE -> nobleIsland(i);
            case COMMONER -> commonerIsland(i);
        }
    }

    private BlockState top(Island i, int x, int z, double d, double e) {
        int h = hash(x, i.top, z);
        if (i.kind == Kind.COMMONER) return (h % 7 == 0 ? Blocks.COARSE_DIRT : h % 11 == 0 ? Blocks.PODZOL : Blocks.GRASS_BLOCK).defaultBlockState();
        double paved = i.kind == Kind.PALACE ? 0.84 : i.kind == Kind.NOBLE ? 0.5 : 0.8;
        if (d > e * paved) return Blocks.GRASS_BLOCK.defaultBlockState();
        return switch (i.kind) {
            case PALACE -> ((x + z) & 7) == 0 ? Blocks.GOLD_BLOCK.defaultBlockState() : ((x ^ z) & 1) == 0 ? Blocks.SMOOTH_QUARTZ.defaultBlockState() : Blocks.QUARTZ_BRICKS.defaultBlockState();
            case MARKET -> h % 3 == 0 ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState();
            case SCHOOL, BARRACKS -> ((x ^ z) & 3) == 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState();
            default -> Blocks.SMOOTH_QUARTZ.defaultBlockState();
        };
    }

    /** Flowers and grass on an island's grassy rim, lamps around the edge of the paving. */
    private void rim(Island i, double paved, int every) {
        for (int x = Math.max(i.x - i.r, cx0); x <= Math.min(i.x + i.r, cx0 + 15); x++)
            for (int z = Math.max(i.z - i.r, cz0); z <= Math.min(i.z + i.r, cz0 + 15); z++) {
                double dx = x - i.x, dz = z - i.z, d = Math.sqrt(dx * dx + dz * dz), e = edge(i, Math.atan2(dz, dx));
                if (d > e - 1) continue;
                int h = hash(x, 7, z) % 29;
                if (d > e * paved + 1) {
                    if (h == 0) set(x, i.top + 1, z, Blocks.CORNFLOWER);
                    else if (h == 1) set(x, i.top + 1, z, Blocks.OXEYE_DAISY);
                    else if (h == 2) set(x, i.top + 1, z, Blocks.ALLIUM);
                    else if (h < 7) set(x, i.top + 1, z, Blocks.GRASS);
                } else if (Math.abs(d - e * paved) < 0.5 && Math.floorMod((int) Math.round(Math.atan2(dz, dx) * 40), every) == 0) {
                    lamp(x, i.top, z);
                }
            }
    }

    // ------------------------------------------------------------------ the Palace island

    private void palaceIsland(Island i) {
        int ox = i.x, oz = i.z, y = i.top;
        rim(i, 0.84, 9);
        // the palace keep: front (entrance) faces south (+z), toward the landing platform
        int x0 = ox - 20, x1 = ox + 20, z0 = oz - 28, z1 = oz + 6;
        BlockState wall = Blocks.SMOOTH_QUARTZ.defaultBlockState(), pillar = Blocks.QUARTZ_PILLAR.defaultBlockState(),
                gold = Blocks.GOLD_BLOCK.defaultBlockState(), glass = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
        box(x0, y, z0, x1, y, z1, Blocks.POLISHED_DIORITE);                                          // floor
        walls(x0, y + 1, z0, x1, y + 24, z1, wall);
        for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) box(c[0], y + 1, c[1], c[0], y + 24, c[1], pillar);
        // windows: two rows, every three blocks
        for (int x = x0 + 2; x <= x1 - 2; x++) {
            if ((x - x0) % 3 == 0) continue;
            for (int[] row : new int[][]{{3, 8}, {15, 20}}) { box(x, y + row[0], z0, x, y + row[1], z0, glass); box(x, y + row[0], z1, x, y + row[1], z1, glass); }
        }
        for (int z = z0 + 2; z <= z1 - 2; z++) {
            if ((z - z0) % 3 == 0) continue;
            for (int[] row : new int[][]{{3, 8}, {15, 20}}) { box(x0, y + row[0], z, x0, y + row[1], z, glass); box(x1, y + row[0], z, x1, y + row[1], z, glass); }
        }
        walls(x0, y + 12, z0, x1, y + 12, z1, gold);                                                 // gold bands
        walls(x0, y + 24, z0, x1, y + 24, z1, gold);
        for (int x = x0; x <= x1; x += 2) { set(x, y + 25, z0, Blocks.QUARTZ_BRICKS); set(x, y + 25, z1, Blocks.QUARTZ_BRICKS); }   // crenellations
        for (int z = z0; z <= z1; z += 2) { set(x0, y + 25, z, Blocks.QUARTZ_BRICKS); set(x1, y + 25, z, Blocks.QUARTZ_BRICKS); }
        box(x0 + 1, y + 12, z0 + 1, x1 - 1, y + 12, z1 - 1, Blocks.QUARTZ_BRICKS);                   // upper floor
        box(x0 + 1, y + 24, z0 + 1, x1 - 1, y + 24, z1 - 1, Blocks.SMOOTH_QUARTZ);                   // roof
        // the entrance: a tall arched doorway, gold-framed
        box(ox - 3, y + 1, z1, ox + 3, y + 7, z1, Blocks.AIR);
        box(ox - 4, y + 8, z1, ox + 4, y + 8, z1, gold);
        box(ox - 4, y + 1, z1, ox - 4, y + 8, z1, gold); box(ox + 4, y + 1, z1, ox + 4, y + 8, z1, gold);
        for (int s : new int[]{-1, 1}) { box(ox + s * 6, y + 1, z1 + 1, ox + s * 6, y + 9, z1 + 1, pillar); set(ox + s * 6, y + 10, z1 + 1, Blocks.GOLD_BLOCK); }
        // ---- the throne room (ground floor)
        box(ox - 1, y, z0 + 4, ox + 1, y, z1, Blocks.RED_WOOL);                                       // the red carpet up the aisle
        for (int z = z1 - 4; z >= z0 + 6; z -= 6) for (int s : new int[]{-1, 1}) {
            box(ox + s * 9, y + 1, z, ox + s * 9, y + 11, z, pillar);
            set(ox + s * 9, y + 11, z, Blocks.GOLD_BLOCK);
        }
        for (int z = z1 - 7; z >= z0 + 4; z -= 6) for (int x : new int[]{ox - 9, ox, ox + 9}) {        // chandeliers
            set(x, y + 11, z, Blocks.CHAIN); set(x, y + 10, z, Blocks.CHAIN); set(x, y + 9, z, Blocks.LANTERN);
        }
        for (int s : new int[]{-1, 1}) for (int z = z0 + 3; z <= z1 - 3; z += 4) box(ox + s * 19, y + 2, z, ox + s * 19, y + 9, z, Blocks.RED_WOOL);   // red hangings
        // the dais with the two thrones (the King's and the Queen's)
        box(ox - 7, y + 1, z0 + 1, ox + 7, y + 1, z0 + 6, Blocks.SMOOTH_QUARTZ);
        box(ox - 5, y + 2, z0 + 1, ox + 5, y + 2, z0 + 4, Blocks.SMOOTH_QUARTZ);
        for (int x = ox - 7; x <= ox + 7; x++) set(x, y + 1, z0 + 7, stair(Blocks.QUARTZ_STAIRS, Direction.NORTH));
        for (int x = ox - 5; x <= ox + 5; x++) set(x, y + 2, z0 + 5, stair(Blocks.QUARTZ_STAIRS, Direction.NORTH));
        for (int t : new int[]{ox - 2, ox + 2}) {
            set(t, y + 3, z0 + 3, Blocks.GOLD_BLOCK); set(t, y + 4, z0 + 3, Blocks.RED_CARPET);           // seat
            box(t, y + 3, z0 + 2, t, y + 6, z0 + 2, gold);                                            // tall back
            set(t - 1, y + 3, z0 + 3, Blocks.GOLD_BLOCK); set(t + 1, y + 3, z0 + 3, Blocks.GOLD_BLOCK);   // arms
            set(t, y + 7, z0 + 2, Blocks.LIGHT_BLUE_STAINED_GLASS);                                   // the jewel on top
        }
        box(ox - 8, y + 2, z0 + 1, ox + 8, y + 10, z0 + 1, Blocks.BLUE_WOOL);                         // royal blue backdrop
        for (int x = ox - 6; x <= ox + 6; x += 3) set(x, y + 10, z0 + 1, Blocks.GOLD_BLOCK);
        // stairs up (east side) to the upper floor
        for (int k = 0; k < 11; k++) {
            set(x1 - 2, y + 1 + k, z1 - 3 - k, stair(Blocks.QUARTZ_STAIRS, Direction.NORTH));
            set(x1 - 3, y + 1 + k, z1 - 3 - k, stair(Blocks.QUARTZ_STAIRS, Direction.NORTH));
            box(x1 - 3, y + 12, z1 - 3 - k, x1 - 2, y + 12, z1 - 3 - k, Blocks.AIR);
        }
        // ---- upper floor: the royal library and a balcony over the courtyard
        for (int x = x0 + 1; x <= x1 - 1; x++) for (int zz : new int[]{z0 + 1, z1 - 1}) box(x, y + 13, zz, x, y + 15, zz, Blocks.BOOKSHELF);
        for (int x = ox - 12; x <= ox + 12; x += 6) { box(x, y + 13, oz - 16, x + 2, y + 13, oz - 16, Blocks.SPRUCE_PLANKS); set(x + 1, y + 14, oz - 16, Blocks.LANTERN); }
        for (int x = ox - 6; x <= ox + 6; x += 4) { set(x, y + 22, oz - 10, Blocks.CHAIN); set(x, y + 21, oz - 10, Blocks.LANTERN); }
        box(ox - 5, y + 12, z1 + 1, ox + 5, y + 12, z1 + 3, Blocks.SMOOTH_QUARTZ);                    // balcony
        for (int x = ox - 5; x <= ox + 5; x++) set(x, y + 13, z1 + 3, slab(Blocks.QUARTZ_SLAB, false));
        box(ox - 2, y + 13, z1, ox + 2, y + 16, z1, Blocks.AIR);
        // ---- the glass dome on the roof
        int dc = oz - 11;
        for (int x = ox - 10; x <= ox + 10; x++) for (int z = dc - 10; z <= dc + 10; z++) for (int k = 0; k <= 10; k++) {
            double r = Math.sqrt((x - ox) * (x - ox) + (z - dc) * (z - dc) + k * k);
            if (r > 9.6 || r < 8.6) continue;
            set(x, y + 25 + k, z, k == 0 || k == 4 ? gold : glass);
        }
        set(ox, y + 35, dc, Blocks.GOLD_BLOCK); set(ox, y + 36, dc, Blocks.END_ROD);
        // ---- four corner towers with blue cone roofs and golden spires
        for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) tower(c[0], y, c[1], 5, 36);
        // ---- the courtyard: fountain, cherry trees, hedges, flowers
        int fz = oz + 24;
        for (int x = ox - 5; x <= ox + 5; x++) for (int z = fz - 5; z <= fz + 5; z++) {
            double r = Math.sqrt((x - ox) * (x - ox) + (z - fz) * (z - fz));
            if (r <= 4.6) { set(x, y, z, Blocks.WATER); if (r > 3.6) set(x, y + 1, z, Blocks.QUARTZ_BRICKS); }
        }
        box(ox, y + 1, fz, ox, y + 3, fz, Blocks.QUARTZ_PILLAR); set(ox, y + 4, fz, Blocks.GOLD_BLOCK); set(ox, y + 5, fz, Blocks.WATER);
        for (int[] t : new int[][]{{-14, 18}, {14, 18}, {-14, 32}, {14, 32}, {-30, -6}, {30, -6}, {-30, -22}, {30, -22}, {-24, 12}, {24, 12}}) cherry(ox + t[0], y, oz + t[1]);
        for (int s : new int[]{-1, 1}) {
            box(ox + s * 8, y + 1, oz + 12, ox + s * 8, y + 1, oz + 38, leaves(Blocks.AZALEA_LEAVES));     // hedges along the walk
            for (int z = oz + 13; z <= oz + 37; z += 2) set(ox + s * 9, y + 1, z, (z & 2) == 0 ? Blocks.POPPY : Blocks.CORNFLOWER);
        }
        box(ox - 2, y, oz + 7, ox + 2, y, oz + 46, Blocks.QUARTZ_BRICKS);                             // the grand walk
        box(ox - 1, y, oz + 7, ox + 1, y, oz + 46, Blocks.RED_WOOL);
        // ---- the landing platform (where the Recarnet Pass drops you) with the sign
        int lz = LANDING.getZ();
        for (int x = ox - 4; x <= ox + 4; x++) for (int z = lz - 3; z <= lz + 3; z++) set(x, y, z, ((x + z) & 1) == 0 ? Blocks.GOLD_BLOCK : Blocks.SMOOTH_QUARTZ);
        for (int s : new int[]{-1, 1}) lamp(ox + s * 5, y, lz);
        sign(ox + 3, y + 1, lz - 3, "Royal Capital", "~ Section 1 ~", "King Rhalix", "welcomes you");
    }

    private void tower(int cx, int y, int cz, int r, int h) {
        for (int x = cx - r; x <= cx + r; x++) for (int z = cz - r; z <= cz + r; z++) {
            double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
            if (d > r + 0.4) continue;
            boolean wall = d > r - 0.8;
            for (int k = 0; k <= h; k++) {
                if (wall) set(x, y + k, z, (k == 12 || k == 24 || k == h) ? Blocks.GOLD_BLOCK.defaultBlockState()
                        : (k % 6 == 3 && ((x + z) & 1) == 0) ? Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState() : Blocks.QUARTZ_BRICKS.defaultBlockState());
                else if (k == 0 || k == 12 || k == 24) set(x, y + k, z, Blocks.SMOOTH_QUARTZ);
                else set(x, y + k, z, Blocks.AIR);
            }
            // cone roof
            int cone = (int) Math.round((r + 1.2 - d) * 2.2);
            for (int k = 1; k <= cone; k++) set(x, y + h + k, z, Blocks.LIGHT_BLUE_TERRACOTTA);
        }
        int peak = y + h + (int) Math.round((r + 1.2) * 2.2);
        set(cx, peak + 1, cz, Blocks.GOLD_BLOCK); set(cx, peak + 2, cz, Blocks.END_ROD); set(cx, peak + 3, cz, Blocks.END_ROD);
    }

    private void cherry(int x, int y, int z) {
        int h = 4 + hash(x, y, z) % 2;
        box(x, y + 1, z, x, y + h, z, Blocks.CHERRY_LOG);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int k = -1; k <= 1; k++) {
            if (Math.abs(dx) + Math.abs(dz) + Math.abs(k) > 3) continue;
            if (dx == 0 && dz == 0 && k <= 0) continue;
            set(x + dx, y + h + k, z + dz, leaves(Blocks.CHERRY_LEAVES));
        }
    }

    private void sign(int x, int y, int z, String a, String b, String c, String d) {
        if (!in(x, z)) return;
        BlockPos p = new BlockPos(x, y, z);
        l.setBlock(p, Blocks.OAK_SIGN.defaultBlockState(), 2);
        if (l.getBlockEntity(p) instanceof net.minecraft.world.level.block.entity.SignBlockEntity s) {
            // (no setText during world generation - it pings a level that isn't there yet; load the text instead)
            var txt = new net.minecraft.world.level.block.entity.SignText();
            String[] lines = {a, b, c, d};
            for (int k = 0; k < 4; k++) txt = txt.setMessage(k, net.minecraft.network.chat.Component.literal(lines[k]));
            net.minecraft.world.level.block.entity.SignText.DIRECT_CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, txt).result().ifPresent(tag -> {
                net.minecraft.nbt.CompoundTag nbt = new net.minecraft.nbt.CompoundTag();
                nbt.put("front_text", tag);
                s.load(nbt);
            });
        }
    }

    // ------------------------------------------------------------------ the Market island + the Black Market under it

    private void marketIsland(Island i) {
        int ox = i.x, oz = i.z, y = i.top;
        rim(i, 0.8, 8);
        // a ring of stalls round the square
        Block[] canopies = {Blocks.RED_WOOL, Blocks.YELLOW_WOOL, Blocks.LIGHT_BLUE_WOOL, Blocks.LIME_WOOL, Blocks.ORANGE_WOOL, Blocks.MAGENTA_WOOL, Blocks.WHITE_WOOL, Blocks.CYAN_WOOL};
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4 + 0.3;
            int sx = ox + (int) Math.round(Math.cos(a) * 14), sz = oz + (int) Math.round(Math.sin(a) * 14);
            for (int[] p : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) box(sx + p[0], y + 1, sz + p[1], sx + p[0], y + 3, sz + p[1], Blocks.SPRUCE_FENCE);
            box(sx - 2, y + 4, sz - 2, sx + 2, y + 4, sz + 2, canopies[k]);
            set(sx, y + 1, sz, Blocks.BARREL); set(sx - 1, y + 1, sz, Blocks.SPRUCE_PLANKS); set(sx + 1, y + 1, sz, k % 2 == 0 ? Blocks.MELON : Blocks.HAY_BLOCK);
        }
        set(ox, y + 1, oz, Blocks.QUARTZ_PILLAR); set(ox, y + 2, oz, Blocks.QUARTZ_PILLAR); set(ox, y + 3, oz, Blocks.BELL);
        sign(ox + 2, y + 1, oz + 2, "Sky Market", "Section 1", "Fresh milk!", "Cielo only");
        // two shops at the north edge
        for (int s : new int[]{-1, 1}) {
            int bx = ox + s * 9, bz = oz - 20;
            box(bx - 4, y, bz - 3, bx + 4, y, bz + 3, Blocks.SMOOTH_QUARTZ);
            walls(bx - 4, y + 1, bz - 3, bx + 4, y + 5, bz + 3, Blocks.QUARTZ_BRICKS.defaultBlockState());
            box(bx - 3, y + 2, bz + 3, bx + 3, y + 4, bz + 3, Blocks.LIGHT_BLUE_STAINED_GLASS);      // shop front
            box(bx, y + 1, bz + 3, bx, y + 2, bz + 3, Blocks.AIR);
            box(bx - 4, y + 6, bz - 3, bx + 4, y + 6, bz + 3, s < 0 ? Blocks.RED_TERRACOTTA : Blocks.BLUE_TERRACOTTA);
            box(bx - 3, y + 1, bz - 2, bx + 3, y + 1, bz - 2, Blocks.BARREL);
            set(bx, y + 5, bz, Blocks.LANTERN);
        }
        // ---- the Black Market: steps down from a back alley into a cavern inside the island
        int ex = ox + 16, ez = oz + 10, floor = y - 11;
        for (int k = 0; k < 10; k++) {                                                                // the stairway down
            int z = ez + k;
            box(ex - 1, y - k - 1, z, ex + 1, y - k + 3, z, Blocks.AIR);
            box(ex - 1, y - k - 1, z, ex + 1, y - k - 1, z, stair(Blocks.STONE_BRICK_STAIRS, Direction.NORTH));
        }
        set(ex - 2, y + 1, ez - 1, Blocks.SOUL_LANTERN);
        int bx0 = ex - 12, bx1 = ex + 4, bz0 = ez + 10, bz1 = ez + 26;
        box(bx0 - 1, floor - 1, bz0 - 1, bx1 + 1, floor + 6, bz1 + 1, Blocks.DEEPSLATE_TILES);
        box(bx0, floor, bz0, bx1, floor + 5, bz1, Blocks.AIR);
        box(ex - 1, floor, ez + 9, ex + 1, floor + 3, bz0, Blocks.AIR);
        for (int x = bx0; x <= bx1; x++) for (int z = bz0; z <= bz1; z++) if (((x + z) & 3) == 0) set(x, floor - 1, z, Blocks.POLISHED_BLACKSTONE);
        for (int x = bx0 + 2; x <= bx1 - 2; x += 5) for (int z = bz0 + 2; z <= bz1 - 2; z += 5) { set(x, floor + 5, z, Blocks.CHAIN); set(x, floor + 4, z, Blocks.SOUL_LANTERN); }
        // auction cages, shady stalls, the auction stage
        for (int z = bz0 + 1; z <= bz1 - 1; z += 4) {
            box(bx0, floor, z, bx0 + 2, floor + 3, z + 2, Blocks.IRON_BARS);
            box(bx0 + 1, floor, z + 1, bx0 + 1, floor + 2, z + 1, Blocks.AIR);
        }
        for (int z = bz0 + 2; z <= bz1 - 2; z += 5) { set(bx1, floor, z, Blocks.BARREL); set(bx1, floor + 1, z, Blocks.SOUL_LANTERN); set(bx1 - 1, floor, z, Blocks.DARK_OAK_PLANKS); }
        box(bx0 + 5, floor, bz1 - 3, bx1 - 3, floor, bz1 - 1, Blocks.POLISHED_BLACKSTONE_BRICKS);
        set((bx0 + bx1) / 2, floor + 1, bz1 - 2, Blocks.LECTERN);
        sign(ex, floor, bz0 + 1, "Black Market", "No questions", "No names", "No refunds");
    }

    // ------------------------------------------------------------------ SkyHigh (the school)

    private void schoolIsland(Island i) {
        int ox = i.x, oz = i.z, y = i.top;
        rim(i, 0.8, 8);
        int x0 = ox - 15, x1 = ox + 15, z0 = oz - 16, z1 = oz - 2;
        box(x0, y, z0, x1, y, z1, Blocks.POLISHED_DIORITE);
        walls(x0, y + 1, z0, x1, y + 12, z1, Blocks.WHITE_CONCRETE.defaultBlockState());
        walls(x0, y + 6, z0, x1, y + 6, z1, Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState());
        walls(x0, y + 12, z0, x1, y + 12, z1, Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState());
        box(x0 + 1, y + 6, z0 + 1, x1 - 1, y + 6, z1 - 1, Blocks.SMOOTH_STONE);
        box(x0, y + 13, z0, x1, y + 13, z1, Blocks.WHITE_CONCRETE);
        for (int x = x0 + 2; x <= x1 - 2; x += 3) for (int base : new int[]{2, 8}) {
            box(x, y + base, z0, x + 1, y + base + 2, z0, Blocks.LIGHT_BLUE_STAINED_GLASS);
            box(x, y + base, z1, x + 1, y + base + 2, z1, Blocks.LIGHT_BLUE_STAINED_GLASS);
        }
        box(ox - 1, y + 1, z1, ox + 1, y + 4, z1, Blocks.AIR);
        // two classrooms downstairs: rows of desks facing a blackboard
        for (int c : new int[]{-1, 1}) {
            int cx = ox + c * 7;
            box(cx - 5, y + 2, z0 + 1, cx + 5, y + 4, z0 + 1, Blocks.BLACK_CONCRETE);
            for (int z = z0 + 4; z <= z1 - 3; z += 2) for (int x = cx - 4; x <= cx + 4; x += 2) {
                set(x, y + 1, z, slab(Blocks.OAK_SLAB, true));
                set(x, y + 1, z + 1, stair(Blocks.OAK_STAIRS, Direction.SOUTH));
            }
            set(cx, y + 1, z0 + 2, Blocks.LECTERN);
        }
        box(ox, y + 1, z0 + 1, ox, y + 5, z1 - 1, Blocks.WHITE_CONCRETE);                              // dividing wall
        box(ox, y + 1, z1 - 3, ox, y + 3, z1 - 2, Blocks.AIR);
        for (int x = x0 + 3; x <= x1 - 3; x += 6) set(x, y + 5, (z0 + z1) / 2, Blocks.SEA_LANTERN);
        for (int k = 0; k < 5; k++) set(x1 - 2, y + 1 + k, z1 - 2 - k, stair(Blocks.QUARTZ_STAIRS, Direction.NORTH));   // stairs up
        box(x1 - 2, y + 6, z1 - 6, x1 - 2, y + 6, z1 - 2, Blocks.AIR);
        sign(ox + 2, y + 1, z1 + 1, "SkyHigh", "Skylander School", "Karma class", "today!");
        // the courtyard: a Karma Activation circle in gold, and a sports field
        for (int x = ox - 4; x <= ox + 4; x++) for (int z = oz + 3; z <= oz + 11; z++) {
            double r = Math.sqrt((x - ox) * (x - ox) + (z - oz - 7) * (z - oz - 7));
            if (Math.abs(r - 3.6) < 0.55) set(x, y, z, Blocks.GOLD_BLOCK);
            else if (r < 1) set(x, y, z, Blocks.LIGHT_BLUE_STAINED_GLASS);
        }
        for (int x = ox - 12; x <= ox + 12; x++) for (int z = oz + 13; z <= oz + 24; z++) {
            boolean line = x == ox - 12 || x == ox + 12 || z == oz + 13 || z == oz + 24 || x == ox;
            set(x, y, z, line ? Blocks.WHITE_CONCRETE : Blocks.GRASS_BLOCK);
        }
    }

    // ------------------------------------------------------------------ the Royal Knights' barracks

    private void barracksIsland(Island i) {
        int ox = i.x, oz = i.z, y = i.top;
        rim(i, 0.8, 8);
        int x0 = ox - 12, x1 = ox + 12, z0 = oz - 18, z1 = oz - 6;
        box(x0, y, z0, x1, y, z1, Blocks.STONE_BRICKS);
        walls(x0, y + 1, z0, x1, y + 8, z1, Blocks.STONE_BRICKS.defaultBlockState());
        for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) box(c[0], y + 1, c[1], c[0], y + 10, c[1], Blocks.QUARTZ_PILLAR);
        walls(x0, y + 8, z0, x1, y + 8, z1, Blocks.QUARTZ_BRICKS.defaultBlockState());
        box(x0, y + 9, z0, x1, y + 9, z1, Blocks.DARK_OAK_PLANKS);
        for (int x = x0 + 2; x <= x1 - 2; x += 4) { box(x, y + 3, z0, x, y + 5, z0, Blocks.GLASS); box(x, y + 3, z1, x, y + 5, z1, Blocks.GLASS); }
        box(ox - 1, y + 1, z1, ox + 1, y + 4, z1, Blocks.AIR);
        for (int x = x0 + 2; x <= x1 - 2; x += 3) {                                                    // bunks
            set(x, y + 1, z0 + 1, Blocks.RED_WOOL); set(x, y + 1, z0 + 2, Blocks.WHITE_WOOL);
            set(x, y + 1, z1 - 2, Blocks.RED_WOOL); set(x, y + 1, z1 - 1, Blocks.WHITE_WOOL);
        }
        set(x0 + 1, y + 1, oz - 12, Blocks.ANVIL); set(x0 + 1, y + 1, oz - 11, Blocks.GRINDSTONE); set(x1 - 1, y + 1, oz - 12, Blocks.SMITHING_TABLE);
        for (int x = x0 + 3; x <= x1 - 3; x += 6) set(x, y + 7, oz - 12, Blocks.LANTERN);
        sign(ox + 2, y + 1, z1 + 1, "Royal Knights", "Barracks", "Train hard,", "fly true");
        // the training yard: a sparring ring, practice dummies and targets
        for (int x = ox - 6; x <= ox + 6; x++) for (int z = oz + 2; z <= oz + 14; z++) {
            double r = Math.sqrt((x - ox) * (x - ox) + (z - oz - 8) * (z - oz - 8));
            if (r <= 6) set(x, y, z, Math.abs(r - 6) < 0.6 ? Blocks.QUARTZ_BRICKS : Blocks.POLISHED_ANDESITE);
        }
        for (int k = 0; k < 4; k++) {
            int dx = ox - 15 + k * 3, dz = oz + 4;
            set(dx, y + 1, dz, Blocks.SPRUCE_FENCE); set(dx, y + 2, dz, Blocks.HAY_BLOCK); set(dx, y + 3, dz, Blocks.CARVED_PUMPKIN);
        }
        for (int k = 0; k < 4; k++) { int tx = ox + 12, tz = oz - 2 + k * 4; set(tx, y + 1, tz, Blocks.SPRUCE_FENCE); set(tx, y + 2, tz, Blocks.TARGET); }
    }

    // ------------------------------------------------------------------ the Noble island

    private void nobleIsland(Island i) {
        int ox = i.x, oz = i.z, y = i.top;
        rim(i, 0.5, 8);
        int[][] villas = {{-9, -6}, {9, -6}, {0, 10}};
        for (int[] v : villas) {
            int bx = ox + v[0], bz = oz + v[1];
            box(bx - 4, y, bz - 4, bx + 4, y, bz + 4, Blocks.SMOOTH_QUARTZ);
            walls(bx - 4, y + 1, bz - 4, bx + 4, y + 6, bz + 4, Blocks.QUARTZ_BRICKS.defaultBlockState());
            walls(bx - 4, y + 6, bz - 4, bx + 4, y + 6, bz + 4, Blocks.GOLD_BLOCK.defaultBlockState());
            box(bx - 4, y + 7, bz - 4, bx + 4, y + 7, bz + 4, Blocks.SMOOTH_QUARTZ_SLAB);
            box(bx - 3, y + 7, bz - 3, bx + 3, y + 8, bz + 3, Blocks.LIGHT_BLUE_TERRACOTTA);
            for (int k = -2; k <= 2; k += 2) { box(bx + k, y + 2, bz - 4, bx + k, y + 4, bz - 4, Blocks.LIGHT_BLUE_STAINED_GLASS); box(bx + k, y + 2, bz + 4, bx + k, y + 4, bz + 4, Blocks.LIGHT_BLUE_STAINED_GLASS); }
            box(bx, y + 1, bz + 4, bx, y + 2, bz + 4, Blocks.AIR);
            set(bx, y + 5, bz, Blocks.LANTERN);
            cherry(bx + 6, y, bz + 5);
        }
        sign(ox + 2, y + 1, oz + 2, "Noble Island", "Section 1", "", "");
    }

    // ------------------------------------------------------------------ the Commoner Islands

    private void commonerIsland(Island i) {
        int ox = i.x, oz = i.z, y = i.top;
        Random r = new Random(i.salt * 7919L);
        // dirt paths
        for (int k = -i.r + 3; k <= i.r - 3; k++) { set(ox + k, y, oz, Blocks.DIRT_PATH); set(ox, y, oz + k, Blocks.DIRT_PATH); }
        int n = 3 + r.nextInt(2);
        for (int k = 0; k < n; k++) {
            double a = k * Math.PI * 2 / n + r.nextDouble() * 0.5;
            int sx = ox + (int) Math.round(Math.cos(a) * i.r * 0.5), sz = oz + (int) Math.round(Math.sin(a) * i.r * 0.5);
            shack(sx, y, sz, r);
        }
        // a little farm plot and a water hole
        for (int x = ox + 2; x <= ox + 6; x++) for (int z = oz + 2; z <= oz + 5; z++) {
            set(x, y, z, Blocks.FARMLAND);
            set(x, y + 1, z, Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, hash(x, y, z) % 8));
        }
        set(ox + 4, y, oz + 6, Blocks.WATER);
        set(ox - 2, y + 1, oz - 2, Blocks.BARREL); set(ox - 3, y + 1, oz - 2, Blocks.COMPOSTER);
        for (int x = ox - i.r + 2; x <= ox + i.r - 2; x++) for (int z = oz - i.r + 2; z <= oz + i.r - 2; z++) {
            int h = hash(x, 3, z) % 31;
            if (h == 0) set(x, y + 1, z, Blocks.GRASS); else if (h == 1) set(x, y + 1, z, Blocks.DEAD_BUSH);
        }
    }

    private void shack(int cx, int y, int cz, Random r) {
        int w = 2 + r.nextInt(2), d = 2 + r.nextInt(2);
        Block[] wallSet = {Blocks.SPRUCE_PLANKS, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.OAK_PLANKS};
        for (int x = cx - w; x <= cx + w; x++) for (int z = cz - d; z <= cz + d; z++) {
            set(x, y, z, Blocks.COARSE_DIRT);
            boolean edge = x == cx - w || x == cx + w || z == cz - d || z == cz + d;
            for (int k = 1; k <= 4; k++) {
                if (!edge) { set(x, y + k, z, Blocks.AIR); continue; }
                int h = hash(x, y + k, z);
                if (h % 9 == 0 && k > 1) continue;                                                    // holes in the walls
                set(x, y + k, z, wallSet[h % wallSet.length]);
            }
            if (hash(x, y + 5, z) % 6 != 0) set(x, y + 5, z, slab(Blocks.SPRUCE_SLAB, false));        // a patchy roof
        }
        set(cx, y + 1, cz + d, Blocks.AIR); set(cx, y + 2, cz + d, Blocks.AIR);
        set(cx - w + 1, y + 4, cz - d + 1, Blocks.COBWEB);
        set(cx + w - 1, y + 1, cz - d + 1, Blocks.BARREL);
        set(cx, y + 4, cz, Blocks.LANTERN);
    }

    // ------------------------------------------------------------------ bridges and chains

    private void bridge(int ax, int ay, int az, int bx, int by, int bz, boolean royal) {
        double dx = bx - ax, dz = bz - az, len = Math.sqrt(dx * dx + dz * dz);
        double ux = dx / len, uz = dz / len, px = -uz, pz = ux;
        int steps = (int) Math.ceil(len * 2);
        for (int s = 0; s <= steps; s++) {
            double t = (double) s / steps;
            double x = ax + dx * t, z = az + dz * t;
            int y = (int) Math.round(ay + (by - ay) * t + (royal ? 2 * Math.sin(Math.PI * t) : -1.5 * Math.sin(Math.PI * t)));
            for (int w = -2; w <= 2; w++) {
                int bxw = (int) Math.round(x + px * w), bzw = (int) Math.round(z + pz * w);
                if (royal) {
                    set(bxw, y, bzw, Math.abs(w) == 2 ? Blocks.QUARTZ_BRICKS : w == 0 ? Blocks.LIGHT_BLUE_STAINED_GLASS : Blocks.SMOOTH_QUARTZ);
                    if (Math.abs(w) == 2) set(bxw, y + 1, bzw, slab(Blocks.QUARTZ_SLAB, false));
                    set(bxw, y - 1, bzw, Blocks.GOLD_BLOCK);
                } else if (Math.abs(w) <= 1) {
                    if (hash(bxw, y, bzw) % 7 != 0) set(bxw, y, bzw, Blocks.SPRUCE_PLANKS);          // a few planks missing
                }
            }
            if (s % 10 == 5) for (int w : new int[]{-2, 2}) {                                           // posts with lanterns / rope posts
                int bxw = (int) Math.round(x + px * w), bzw = (int) Math.round(z + pz * w);
                if (royal) { set(bxw, y + 1, bzw, Blocks.QUARTZ_PILLAR); set(bxw, y + 2, bzw, Blocks.LANTERN); }
                else { set(bxw, y, bzw, Blocks.SPRUCE_PLANKS); set(bxw, y + 1, bzw, Blocks.SPRUCE_FENCE); }
            }
        }
    }

    /** A giant chain of iron links, hanging slack between two points. */
    private void chain(int ax, int ay, int az, int bx, int by, int bz, int sag) {
        double dx = bx - ax, dy = by - ay, dz = bz - az, len = Math.sqrt(dx * dx + dz * dz);
        double ux = dx / len, uz = dz / len, px = -uz, pz = ux;
        int links = Math.max(2, (int) (len / 5));
        for (int k = 0; k <= links; k++) {
            double t = (double) k / links;
            double cx = ax + dx * t, cz = az + dz * t, cy = ay + dy * t - sag * 4 * t * (1 - t);
            boolean flat = k % 2 == 0;
            for (int a = 0; a < 40; a++) {
                double ang = a * Math.PI * 2 / 40, c = Math.cos(ang) * 3.2, s = Math.sin(ang) * 1.6;
                double x = cx + ux * c + (flat ? px * s : 0), y = cy + (flat ? 0 : s), z = cz + uz * c + (flat ? pz * s : 0);
                set((int) Math.round(x), (int) Math.round(y), (int) Math.round(z), Blocks.IRON_BLOCK);
            }
        }
        // anchors where the chain meets the islands
        for (int[] p : new int[][]{{ax, ay, az}, {bx, by, bz}}) box(p[0] - 1, p[1] - 1, p[2] - 1, p[0] + 1, p[1] + 1, p[2] + 1, Blocks.POLISHED_DEEPSLATE);
    }

    // ------------------------------------------------------------------ the people

    private void people(Island i) {
        int n = switch (i.kind) { case PALACE -> 6; case MARKET -> 6; case SCHOOL -> 5; case BARRACKS -> 5; case NOBLE -> 3; case COMMONER -> 3; };
        Random r = new Random(i.salt * 31L);
        for (int k = 0; k < n; k++) {
            SkylanderEntity sk = ModNpcs.SKYLANDER.get().create(l.getLevel());
            if (sk == null) continue;
            double a = r.nextDouble() * Math.PI * 2, d = 4 + r.nextDouble() * (i.r * 0.5);
            int x = i.x + (int) Math.round(Math.cos(a) * d), z = i.z + (int) Math.round(Math.sin(a) * d);
            sk.moveTo(x + 0.5, i.top + 2, z + 0.5, r.nextFloat() * 360F, 0F);
            sk.finalizeSpawn(l, l.getCurrentDifficultyAt(new BlockPos(x, i.top, z)), MobSpawnType.STRUCTURE, null, null);
            sk.liveOn(new BlockPos(i.x, i.top + 1, i.z), Math.max(6, i.r - 5), false);
            sk.setPersistenceRequired();
            l.addFreshEntityWithPassengers(sk);
        }
    }

    private void guard(int x, int y, int z) {
        RoyalGuardEntity g = ModNpcs.ROYAL_GUARD.get().create(l.getLevel());
        if (g == null) return;
        g.moveTo(x + 0.5, y, z + 0.5, 0F, 0F);
        g.finalizeSpawn(l, l.getCurrentDifficultyAt(new BlockPos(x, y, z)), MobSpawnType.STRUCTURE, null, null);
        g.post(new BlockPos(x, y, z));
        g.setYRot(0F); g.setYHeadRot(0F); g.yBodyRot = 0F;
        l.addFreshEntityWithPassengers(g);
    }

    /** Is this spot up in the Royal Capital (in Recarnet, above Aurora, above its floor)? */
    public static boolean inCapital(net.minecraft.world.level.Level level, BlockPos p) {
        return Recarnet.is(level) && p.getY() >= FLOOR_Y && Recarnet.dist(p) < R + 20;
    }
}
