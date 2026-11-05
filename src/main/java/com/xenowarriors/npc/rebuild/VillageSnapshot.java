package com.xenowarriors.npc.rebuild;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.Tags;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Records a village's built blocks, a slice per tick (never loading a chunk: every chunk of its box has to be loaded
 * already, or it waits for a later look). Three ways to find what belongs to it:
 * - "pieces": a generated village keeps its structure pieces - their boxes say exactly where the buildings are, and
 *   everything inside them that isn't plain terrain is taken.
 * - "bell": no structure record (a village made with /place, an old world): the box around its bell, beds and
 *   workplaces, and only blocks that are clearly built (planks, logs not in a tree, glass, doors, beds, stairs, slabs,
 *   fences, paths, crops and farmland, workstations, torches...).
 * - "town" / "hut": Recarnet's towns (around their beds) and the huts townsfolk build (around their home).
 * The ground right under built blocks (two deep) is kept too, so a crater under a house is filled before the house.
 */
final class VillageSnapshot {
    enum Mode { PIECES, BELL, TOWN, HUT }

    static final int MAX_H = 200, MAX_V = 64, PER_TICK = 24576;

    final ServerLevel l;
    final BlockPos anchor;
    final Mode mode;
    final BoundingBox box;
    private final List<BoundingBox> parts;
    private int part, ix, iy, iz;
    private boolean begun;
    private final Long2IntOpenHashMap found = new Long2IntOpenHashMap();
    private final List<BlockState> palette = new ArrayList<>();
    private final Object2IntOpenHashMap<BlockState> pal = new Object2IntOpenHashMap<>();
    /** Why it stopped short ("chunk", "gov") - null while fine. "chunk" is retried later, anything else isn't. */
    String failed;

    private VillageSnapshot(ServerLevel l, BlockPos anchor, Mode mode, BoundingBox box, List<BoundingBox> parts) {
        this.l = l; this.anchor = anchor; this.mode = mode; this.box = box; this.parts = parts;
    }

    // ------------------------------------------------------------------ where

    /** A snapshot of the village around this bell: its structure pieces when the world still has them, otherwise the bell area. */
    static VillageSnapshot forBell(ServerLevel l, BlockPos bell) {
        StructureStart s = villageStart(l, bell);
        if (s != null) {
            BoundingBox box = clamp(l, s.getBoundingBox(), bell);
            List<BoundingBox> parts = new ArrayList<>();
            for (StructurePiece p : s.getPieces()) {
                BoundingBox b = p.getBoundingBox();
                BoundingBox c = intersect(new BoundingBox(b.minX(), b.minY() - 2, b.minZ(), b.maxX(), b.maxY(), b.maxZ()), box);
                if (c != null) parts.add(c);
            }
            if (!parts.isEmpty()) return new VillageSnapshot(l, bell, Mode.PIECES, box, parts);
        }
        int[] b = {bell.getX(), bell.getY(), bell.getZ(), bell.getX(), bell.getY(), bell.getZ()};
        l.getPoiManager().findAll(h -> true, p -> true, bell, 48, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).forEach(p -> {
            b[0] = Math.min(b[0], p.getX()); b[1] = Math.min(b[1], p.getY()); b[2] = Math.min(b[2], p.getZ());
            b[3] = Math.max(b[3], p.getX()); b[4] = Math.max(b[4], p.getY()); b[5] = Math.max(b[5], p.getZ());
        });
        BoundingBox box = clamp(l, new BoundingBox(b[0] - 8, b[1] - 6, b[2] - 8, b[3] + 8, b[4] + 12, b[5] + 8), bell);
        return new VillageSnapshot(l, bell, Mode.BELL, box, List.of(box));
    }

    /** A district of a Recarnet town, around one of its beds. */
    static VillageSnapshot forTown(ServerLevel l, BlockPos bed) {
        BoundingBox box = clamp(l, new BoundingBox(bed.getX() - 16, bed.getY() - 4, bed.getZ() - 16, bed.getX() + 16, bed.getY() + 12, bed.getZ() + 16), bed);
        return new VillageSnapshot(l, bed, Mode.TOWN, box, List.of(box));
    }

    /** A townsperson's hut (with whatever they put up right by it). */
    static VillageSnapshot forHut(ServerLevel l, BlockPos home) {
        BoundingBox box = clamp(l, new BoundingBox(home.getX() - 6, home.getY() - 3, home.getZ() - 6, home.getX() + 6, home.getY() + 7, home.getZ() + 6), home);
        return new VillageSnapshot(l, home, Mode.HUT, box, List.of(box));
    }

    /** The generated village structure with a piece referenced from this position's chunk (only chunks already loaded are read). */
    static StructureStart villageStart(ServerLevel l, BlockPos p) {
        if (!l.getChunkSource().hasChunk(p.getX() >> 4, p.getZ() >> 4)) return null;
        Registry<Structure> reg = l.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (Map.Entry<Structure, it.unimi.dsi.fastutil.longs.LongSet> e : l.getChunk(p.getX() >> 4, p.getZ() >> 4).getAllReferences().entrySet()) {
            if (!reg.wrapAsHolder(e.getKey()).is(StructureTags.VILLAGE)) continue;
            for (long c : e.getValue()) {
                int cx = net.minecraft.world.level.ChunkPos.getX(c), cz = net.minecraft.world.level.ChunkPos.getZ(c);
                if (!l.getChunkSource().hasChunk(cx, cz)) continue;
                StructureStart s = l.getChunk(cx, cz).getStartForStructure(e.getKey());
                if (s != null && s.isValid() && s.getBoundingBox().isInside(p)) return s;
            }
        }
        return null;
    }

    private static BoundingBox clamp(ServerLevel l, BoundingBox b, BlockPos c) {
        int h = MAX_H / 2;
        int minX = Math.max(b.minX(), c.getX() - h), maxX = Math.min(b.maxX(), c.getX() + h);
        int minZ = Math.max(b.minZ(), c.getZ() - h), maxZ = Math.min(b.maxZ(), c.getZ() + h);
        int minY = Math.max(Math.max(b.minY(), c.getY() - MAX_V / 2), l.getMinBuildHeight());
        int maxY = Math.min(Math.min(b.maxY(), minY + MAX_V - 1), l.getMaxBuildHeight() - 1);
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static BoundingBox intersect(BoundingBox a, BoundingBox b) {
        int x0 = Math.max(a.minX(), b.minX()), y0 = Math.max(a.minY(), b.minY()), z0 = Math.max(a.minZ(), b.minZ());
        int x1 = Math.min(a.maxX(), b.maxX()), y1 = Math.min(a.maxY(), b.maxY()), z1 = Math.min(a.maxZ(), b.maxZ());
        return x0 <= x1 && y0 <= y1 && z0 <= z1 ? new BoundingBox(x0, y0, z0, x1, y1, z1) : null;
    }

    /** Every chunk of the box is loaded right now. */
    boolean loaded() {
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++)
            if (!l.getChunkSource().hasChunk(cx, cz)) return false;
        return true;
    }

    // ------------------------------------------------------------------ what

    /** Plain terrain and things that grow by themselves - never part of a building. */
    static boolean natural(BlockState s, ServerLevel l, BlockPos p) {
        if (s.isAir()) return true;
        if (s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY)
                || s.is(Tags.Blocks.ORES) || s.is(BlockTags.LEAVES) || s.is(BlockTags.SNOW) || s.is(BlockTags.ICE) || s.is(Blocks.BEE_NEST)
                || s.is(Blocks.SUGAR_CANE) || s.is(Blocks.CACTUS) || s.is(Blocks.PUMPKIN) || s.is(Blocks.MELON) || s.is(Blocks.COBWEB)
                || s.is(Blocks.SANDSTONE) && p.getY() < l.getSeaLevel() - 2) return true;
        Block b = s.getBlock();
        if (b instanceof BushBlock && !(b instanceof CropBlock) && !(b instanceof StemBlock)) return true;   // grass, flowers, saplings...
        if (b instanceof VineBlock || b instanceof GrowingPlantBlock) return true;
        if (!s.getFluidState().isEmpty()) return !keepWater(s, l, p);
        if (s.canBeReplaced()) return true;
        if (s.is(BlockTags.LOGS)) for (Direction d : Direction.values()) if (l.getBlockState(p.relative(d)).is(BlockTags.LEAVES)) return true;   // a tree
        return false;
    }

    /** Water that's part of the village: a farm's channel (next to farmland), or held in something built. */
    private static boolean keepWater(BlockState s, ServerLevel l, BlockPos p) {
        if (!(s.getBlock() instanceof LiquidBlock) || !s.getFluidState().isSource() || !s.getFluidState().is(Fluids.WATER)) return false;
        for (Direction d : Direction.Plane.HORIZONTAL) if (l.getBlockState(p.relative(d)).getBlock() instanceof FarmBlock) return true;
        return false;
    }

    /** Clearly built by someone (the "bell" way, where there's no structure record to go by). */
    static boolean built(BlockState s, ServerLevel l, BlockPos p) {
        if (s.isAir()) return false;
        Block b = s.getBlock();
        if (!s.getFluidState().isEmpty() && b instanceof LiquidBlock) return keepWater(s, l, p);
        if (s.is(BlockTags.LOGS)) return !natural(s, l, p);
        if (s.is(BlockTags.PLANKS) || s.is(BlockTags.STAIRS) || s.is(BlockTags.SLABS) || s.is(BlockTags.WALLS) || s.is(BlockTags.FENCES)
                || s.is(BlockTags.FENCE_GATES) || s.is(BlockTags.DOORS) || s.is(BlockTags.TRAPDOORS) || s.is(BlockTags.BEDS) || s.is(BlockTags.WOOL)
                || s.is(BlockTags.WOOL_CARPETS) || s.is(BlockTags.TERRACOTTA) || s.is(BlockTags.STONE_BRICKS) || s.is(BlockTags.ALL_SIGNS)
                || s.is(BlockTags.FLOWER_POTS) || s.is(BlockTags.BUTTONS) || s.is(BlockTags.PRESSURE_PLATES) || s.is(BlockTags.CANDLES)
                || s.is(BlockTags.ANVIL) || s.is(BlockTags.CAULDRONS) || s.is(Tags.Blocks.GLASS) || s.is(Tags.Blocks.GLASS_PANES)
                || s.is(Tags.Blocks.CHESTS) || s.is(Tags.Blocks.BARRELS) || s.is(BlockTags.CAMPFIRES)) return true;
        if (b instanceof CropBlock || b instanceof StemBlock || b instanceof FarmBlock || b instanceof DirtPathBlock || b instanceof TorchBlock
                || b instanceof LanternBlock || b instanceof AbstractFurnaceBlock || b instanceof IronBarsBlock || b instanceof LadderBlock
                || b instanceof BellBlock || b instanceof GlazedTerracottaBlock || b instanceof CraftingTableBlock || b instanceof ChainBlock
                || b instanceof BrewingStandBlock || b instanceof ComposterBlock || b instanceof LecternBlock) return true;
        if (b == Blocks.COBBLESTONE || b == Blocks.MOSSY_COBBLESTONE || b == Blocks.BRICKS || b == Blocks.SMOOTH_STONE || b == Blocks.HAY_BLOCK
                || b == Blocks.BOOKSHELF || b == Blocks.CUT_SANDSTONE || b == Blocks.SMOOTH_SANDSTONE || b == Blocks.CHISELED_SANDSTONE
                || b == Blocks.CUT_RED_SANDSTONE || b == Blocks.SMOOTH_RED_SANDSTONE || b == Blocks.MUD_BRICKS || b == Blocks.PACKED_MUD
                || b == Blocks.POLISHED_ANDESITE || b == Blocks.POLISHED_GRANITE || b == Blocks.POLISHED_DIORITE || b == Blocks.QUARTZ_BLOCK
                || b == Blocks.WHITE_CONCRETE || b == Blocks.GLOWSTONE || b == Blocks.SEA_LANTERN) return true;
        if (b == Blocks.SANDSTONE) return p.getY() >= l.getSeaLevel() - 2;
        return PoiTypes.forState(s).isPresent() && b != Blocks.BEE_NEST && b != Blocks.BEEHIVE && b != Blocks.NETHER_PORTAL;
    }

    /** A Government facility's own blocks: those places are left alone. */
    private static boolean gov(BlockState s) {
        Block b = s.getBlock();
        return b instanceof com.xenowarriors.gov.GovTerminalBlock || b instanceof com.xenowarriors.gov.GovVaultBlock || b instanceof com.xenowarriors.gov.HealingPodBlock;
    }

    private boolean take(BlockState s, BlockPos p) {
        return mode == Mode.BELL ? built(s, l, p) : !natural(s, l, p);
    }

    // ------------------------------------------------------------------ doing it

    /** Looks at up to PER_TICK positions; true once finished (or failed). */
    boolean step() {
        if (!begun) {
            begun = true;
            if (!loaded()) { failed = "chunk"; return true; }
            BoundingBox p0 = parts.get(0);
            ix = p0.minX(); iy = p0.minY(); iz = p0.minZ();
        }
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int n = 0; n < PER_TICK; n++) {
            if (part >= parts.size()) return true;
            BoundingBox b = parts.get(part);
            m.set(ix, iy, iz);
            if (iy == b.minY() && !l.getChunkSource().hasChunk(ix >> 4, iz >> 4)) { failed = "chunk"; return true; }
            long key = m.asLong();
            if (!found.containsKey(key)) {
                BlockState s = l.getBlockState(m);
                if (gov(s)) { failed = "gov"; return true; }
                if (take(s, m)) found.put(key, id(s));
            }
            if (++iy > b.maxY()) { iy = b.minY(); if (++iz > b.maxZ()) { iz = b.minZ(); if (++ix > b.maxX()) {
                if (++part < parts.size()) { BoundingBox nb = parts.get(part); ix = nb.minX(); iy = nb.minY(); iz = nb.minZ(); }
            } } }
        }
        return part >= parts.size();
    }

    private int id(BlockState s) {
        BlockState k = s;
        return pal.computeIfAbsent(k, x -> { palette.add(k); return palette.size() - 1; });
    }

    /** The finished snapshot (null if there's hardly anything built here). */
    RebuildData.Village finish(int id) {
        // the ground right under what's built, two deep
        long[] keys = found.keySet().toLongArray();
        for (long k : keys) {
            BlockPos p = BlockPos.of(k);
            for (int d = 1; d <= 2; d++) {
                BlockPos q = p.below(d);
                if (!box.isInside(q) || found.containsKey(q.asLong())) break;
                BlockState s = l.getBlockState(q);
                if (s.isAir() || !s.isSolidRender(l, q) || !natural(s, l, q)) break;
                found.put(q.asLong(), id(s));
            }
        }
        if (found.size() < 24) return null;
        long[] all = found.keySet().toLongArray();
        if (all.length > RebuildData.MAX_BLOCKS) {                           // too big: keep what's nearest the middle
            Long[] boxed = Arrays.stream(all).boxed().toArray(Long[]::new);
            Arrays.sort(boxed, (a, b) -> Double.compare(BlockPos.of(a).distSqr(anchor), BlockPos.of(b).distSqr(anchor)));
            all = new long[RebuildData.MAX_BLOCKS];
            for (int i = 0; i < all.length; i++) all[i] = boxed[i];
        }
        long[] packed = new long[all.length];                                  // (packed position << 32 | palette index), sorted by position
        for (int i = 0; i < all.length; i++) {
            BlockPos p = BlockPos.of(all[i]);
            int r = RebuildData.Village.pack(p.getX() - box.minX(), p.getY() - box.minY(), p.getZ() - box.minZ());
            packed[i] = ((long) r << 32) | (found.get(all[i]) & 0xFFFFFFFFL);
        }
        Arrays.sort(packed);
        int[] rel = new int[packed.length];
        short[] st = new short[packed.length];
        for (int i = 0; i < packed.length; i++) { rel[i] = (int) (packed[i] >>> 32); st[i] = (short) packed[i]; }
        String kind = switch (mode) { case PIECES -> "pieces"; case BELL -> "bell"; case TOWN -> "town"; case HUT -> "hut"; };
        return new RebuildData.Village(id, anchor, box, kind, rel, st, palette.toArray(new BlockState[0]));
    }
}
