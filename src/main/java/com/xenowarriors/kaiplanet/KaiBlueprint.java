package com.xenowarriors.kaiplanet;

import com.xenowarriors.XenoWarriors;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * King Kai's planet as DragonMineZ made it: read straight from the Otherworld map DragonMineZ ships in its jar (the
 * same one it copies into every world), so the builder always knows exactly what the planet should look like - even
 * in a world where it got smashed before this existed. The planet's body (a hollow sphere ~70 blocks across) plus
 * everything on top of it (King Kai's house, the road, the tree, his car); the clouds and Snake Way below are left out.
 * Positions are packed relative to the box as (y << 16 | x << 8 | z) and kept sorted (a binary search finds one).
 */
public final class KaiBlueprint {
    /** The planet's middle, its radius (with the crust), and how far around on top its things reach. */
    public static final double CX = 59.0, CY = 120.5, CZ = 1097.0, BODY_R = 76, TOP_R = 100, TOP_Y = 150;
    /** King Kai's spot (where DragonMineZ puts him). */
    public static final BlockPos KING_KAI = new BlockPos(54, 190, 1082);
    static final int X0 = (int) CX - 100, Z0 = (int) CZ - 100, Y0 = 40;

    final int[] keys;
    final short[] idx;
    final BlockState[] palette;
    /** Block entity data (signs, beds; containers come back empty), by key. */
    final Int2ObjectOpenHashMap<CompoundTag> blockEntities;

    private KaiBlueprint(int[] keys, short[] idx, BlockState[] palette, Int2ObjectOpenHashMap<CompoundTag> be) {
        this.keys = keys; this.idx = idx; this.palette = palette; this.blockEntities = be;
    }

    public int size() { return keys.length; }

    public static int key(int x, int y, int z) {
        int rx = x - X0, ry = y - Y0, rz = z - Z0;
        if (rx < 0 || rx > 255 || ry < 0 || ry > 255 || rz < 0 || rz > 255) return -1;
        return ry << 16 | rx << 8 | rz;
    }

    /** This position's index in the blueprint, or -1. */
    public int index(BlockPos p) {
        int k = key(p.getX(), p.getY(), p.getZ());
        return k < 0 ? -1 : Math.max(-1, Arrays.binarySearch(keys, k));
    }

    public BlockPos pos(int i) {
        int k = keys[i];
        return new BlockPos(X0 + (k >> 8 & 255), Y0 + (k >> 16 & 255), Z0 + (k & 255));
    }

    public BlockState state(int i) { return palette[idx[i]]; }

    public CompoundTag blockEntity(int i) { return blockEntities.get(keys[i]); }

    /** Inside the area the blueprint covers (the box around the planet)? */
    public static boolean near(double x, double y, double z) {
        double dx = x - CX, dy = y - CY, dz = z - CZ;
        return dx * dx + dy * dy + dz * dz < 130 * 130;
    }

    /** Part of the planet: its body, or on top of it. */
    static boolean planet(int x, int y, int z) {
        double dx = x + .5 - CX, dy = y + .5 - CY, dz = z + .5 - CZ;
        if (dx * dx + dy * dy + dz * dz <= BODY_R * BODY_R) return true;
        return y >= TOP_Y && dx * dx + dz * dz <= TOP_R * TOP_R;
    }

    // ------------------------------------------------------------------ reading DragonMineZ's map

    /** Reads it (a second or so: run off the server thread). Null if DragonMineZ or its map isn't there. */
    static KaiBlueprint load(HolderGetter<Block> blocks) {
        try {
            var file = net.minecraftforge.fml.ModList.get().getModFileById("dragonminez");
            if (file == null) return null;
            Map<Long, int[]> unsorted = new HashMap<>();                         // key -> state id (sorted after)
            List<BlockState> pal = new ArrayList<>();
            Map<BlockState, Integer> palIds = new HashMap<>();
            Int2ObjectOpenHashMap<CompoundTag> be = new Int2ObjectOpenHashMap<>();
            IntArrayList keyList = new IntArrayList();
            List<Integer> stateList = new ArrayList<>();
            for (int rx = -1; rx <= 0; rx++) for (int rz = 1; rz <= 2; rz++) {
                Path path = file.getFile().findResource("data", "dragonminez", "regions", "otherworld", "r." + rx + "." + rz + ".mca");
                if (path == null || !Files.exists(path)) continue;
                byte[] data = Files.readAllBytes(path);
                for (int cx = X0 >> 4; cx <= (X0 + 200) >> 4; cx++) for (int cz = Z0 >> 4; cz <= (Z0 + 200) >> 4; cz++) {
                    if (cx >> 5 != rx || cz >> 5 != rz) continue;
                    CompoundTag ch = chunk(data, cx, cz);
                    if (ch == null) continue;
                    readChunk(ch, cx, cz, blocks, pal, palIds, keyList, stateList, be);
                }
            }
            if (keyList.isEmpty()) return null;
            Integer[] order = new Integer[keyList.size()];
            for (int i = 0; i < order.length; i++) order[i] = i;
            Arrays.sort(order, Comparator.comparingInt(keyList::getInt));
            int[] keys = new int[order.length];
            short[] idx = new short[order.length];
            for (int i = 0; i < order.length; i++) { keys[i] = keyList.getInt(order[i]); idx[i] = (short) (int) stateList.get(order[i]); }
            return new KaiBlueprint(keys, idx, pal.toArray(new BlockState[0]), be);
        } catch (Throwable t) {
            XenoWarriors.LOGGER.warn("Couldn't read King Kai's planet from DragonMineZ's Otherworld map", t);
            return null;
        }
    }

    private static CompoundTag chunk(byte[] region, int cx, int cz) throws Exception {
        int i = 4 * ((cx & 31) + (cz & 31) * 32);
        int off = (region[i] & 255) << 16 | (region[i + 1] & 255) << 8 | (region[i + 2] & 255);
        if (off == 0) return null;
        int o = off * 4096;
        if (o + 5 > region.length) return null;
        int len = (region[o] & 255) << 24 | (region[o + 1] & 255) << 16 | (region[o + 2] & 255) << 8 | (region[o + 3] & 255);
        int type = region[o + 4] & 127;
        InputStream raw = new ByteArrayInputStream(region, o + 5, Math.max(0, len - 1));
        InputStream in = type == 1 ? new GZIPInputStream(raw) : type == 2 ? new InflaterInputStream(raw) : raw;
        try (DataInputStream d = new DataInputStream(in)) { return NbtIo.read(d, NbtAccounter.UNLIMITED); }
    }

    private static void readChunk(CompoundTag ch, int cx, int cz, HolderGetter<Block> blocks, List<BlockState> pal, Map<BlockState, Integer> palIds,
                                  IntArrayList keyList, List<Integer> stateList, Int2ObjectOpenHashMap<CompoundTag> be) {
        ListTag sections = ch.getList("sections", Tag.TAG_COMPOUND);
        for (int s = 0; s < sections.size(); s++) {
            CompoundTag sec = sections.getCompound(s);
            int y0 = sec.getByte("Y") * 16;
            if (y0 + 15 < Y0 || y0 > Y0 + 255) continue;
            CompoundTag bs = sec.getCompound("block_states");
            ListTag paletteTag = bs.getList("palette", Tag.TAG_COMPOUND);
            if (paletteTag.isEmpty()) continue;
            BlockState[] local = new BlockState[paletteTag.size()];
            boolean any = false;
            for (int i = 0; i < local.length; i++) {
                BlockState st = NbtUtils.readBlockState(blocks, paletteTag.getCompound(i));
                if (st.is(Blocks.BEDROCK) || st.getBlock().getDescriptionId().contains("otherworld_cloud")) st = Blocks.AIR.defaultBlockState();   // (the ground below, not the planet)
                local[i] = st;
                if (!st.isAir()) any = true;
            }
            if (!any) continue;
            long[] data = bs.getLongArray("data");
            int bits = local.length <= 1 ? 0 : Math.max(4, 32 - Integer.numberOfLeadingZeros(local.length - 1));
            int per = bits == 0 ? 0 : 64 / bits;
            for (int i = 0; i < 4096; i++) {
                int v = 0;
                if (bits > 0) {
                    int word = i / per;
                    if (word >= data.length) break;
                    v = (int) (data[word] >>> ((i % per) * bits) & ((1L << bits) - 1));
                }
                if (v >= local.length) continue;
                BlockState st = local[v];
                if (st.isAir()) continue;
                int x = cx * 16 + (i & 15), y = y0 + (i >> 8), z = cz * 16 + (i >> 4 & 15);
                if (!planet(x, y, z)) continue;
                int k = key(x, y, z);
                if (k < 0) continue;
                Integer id = palIds.get(st);
                if (id == null) { id = pal.size(); pal.add(st); palIds.put(st, id); }
                keyList.add(k);
                stateList.add(id);
            }
        }
        ListTag bes = ch.getList("block_entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < bes.size(); i++) {
            CompoundTag t = bes.getCompound(i);
            int k = key(t.getInt("x"), t.getInt("y"), t.getInt("z"));
            if (k >= 0 && planet(t.getInt("x"), t.getInt("y"), t.getInt("z"))) be.put(k, t);
        }
    }
}
