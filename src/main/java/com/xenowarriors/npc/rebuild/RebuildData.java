package com.xenowarriors.npc.rebuild;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/**
 * What each known village looked like when it was first seen (see VillageRebuild): per village, the built blocks in
 * its area as a palette of block states plus one small index per position. Positions are packed relative to the
 * village's box as (y << 16 | x << 8 | z) and kept sorted - so a lookup is a binary search and the index order runs
 * from the bottom up. Only the snapshot is saved: what's missing is found again by looking (a slow sweep) after a load.
 */
public final class RebuildData extends SavedData {
    public static final String NAME = "xenowarriors_village_rebuild";
    /** Hard caps: blocks per village, villages per dimension (the one nobody has been near longest goes first). */
    public static final int MAX_BLOCKS = 24000, MAX_VILLAGES = 96;

    final List<Village> villages = new ArrayList<>();
    private final Long2ObjectOpenHashMap<List<Village>> byChunk = new Long2ObjectOpenHashMap<>();
    int nextId = 1;

    public RebuildData() {}

    public List<Village> villages() { return villages; }

    /** The villages whose box covers this chunk (null: none). */
    List<Village> inChunk(int cx, int cz) { return byChunk.get(ChunkPos.asLong(cx, cz)); }

    /** The village whose box holds this position, if any. */
    public Village at(BlockPos p) {
        List<Village> l = byChunk.get(ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4));
        if (l != null) for (Village v : l) if (v.box.isInside(p)) return v;
        return null;
    }

    void add(Village v) {
        while (villages.size() >= MAX_VILLAGES) {
            Village old = villages.get(0);
            for (Village o : villages) if (o.lastSeen < old.lastSeen) old = o;
            remove(old);
        }
        villages.add(v);
        index(v);
        setDirty();
    }

    void remove(Village v) {
        villages.remove(v);
        for (int cx = v.box.minX() >> 4; cx <= v.box.maxX() >> 4; cx++) for (int cz = v.box.minZ() >> 4; cz <= v.box.maxZ() >> 4; cz++) {
            List<Village> l = byChunk.get(ChunkPos.asLong(cx, cz));
            if (l != null) { l.remove(v); if (l.isEmpty()) byChunk.remove(ChunkPos.asLong(cx, cz)); }
        }
        setDirty();
    }

    private void index(Village v) {
        for (int cx = v.box.minX() >> 4; cx <= v.box.maxX() >> 4; cx++) for (int cz = v.box.minZ() >> 4; cz <= v.box.maxZ() >> 4; cz++)
            byChunk.computeIfAbsent(ChunkPos.asLong(cx, cz), k -> new ArrayList<>(1)).add(v);
    }

    public static RebuildData load(CompoundTag tag) {
        RebuildData d = new RebuildData();
        d.nextId = Math.max(1, tag.getInt("next"));
        ListTag list = tag.getList("villages", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            try {
                Village v = Village.read(list.getCompound(i));
                if (v != null) { d.villages.add(v); d.index(v); }
            } catch (Exception ignored) {}                         // (a broken entry is dropped, not the whole file)
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("next", nextId);
        ListTag list = new ListTag();
        for (Village v : villages) list.add(v.write());
        tag.put("villages", list);
        return tag;
    }

    /** One village's snapshot, plus what's known to be missing right now (not saved). */
    public static final class Village {
        public final int id;
        public final BlockPos anchor;
        public final BoundingBox box;
        /** How it was recorded: "pieces" (the village structure's own pieces), "bell" (built blocks around its bell and workplaces), "town", "hut". */
        public final String kind;
        final int[] rel;
        final short[] st;
        final BlockState[] palette;
        long lastSeen;
        // ---- runtime only
        final BitSet pending = new BitSet();
        final BitSet queued = new BitSet();
        final IntArrayFIFOQueue suspects = new IntArrayFIFOQueue();
        int cursor;
        public int restored;
        final it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap skipUntil = new it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap();

        Village(int id, BlockPos anchor, BoundingBox box, String kind, int[] rel, short[] st, BlockState[] palette) {
            this.id = id; this.anchor = anchor; this.box = box; this.kind = kind; this.rel = rel; this.st = st; this.palette = palette;
        }

        public int size() { return rel.length; }
        public int pendingCount() { return pending.cardinality(); }
        public int paletteSize() { return palette.length; }

        public BlockPos pos(int i) {
            int r = rel[i];
            return new BlockPos(box.minX() + ((r >> 8) & 255), box.minY() + (r >>> 16), box.minZ() + (r & 255));
        }

        public BlockState state(int i) { return palette[st[i] & 0xFFFF]; }

        static int pack(int dx, int dy, int dz) { return (dy << 16) | (dx << 8) | dz; }

        /** This position's index in the snapshot, or -1. */
        public int index(BlockPos p) {
            if (!box.isInside(p)) return -1;
            int i = Arrays.binarySearch(rel, pack(p.getX() - box.minX(), p.getY() - box.minY(), p.getZ() - box.minZ()));
            return i >= 0 ? i : -1;
        }

        public boolean isPending(int i) { return pending.get(i); }

        CompoundTag write() {
            CompoundTag t = new CompoundTag();
            t.putInt("id", id);
            t.putLong("anchor", anchor.asLong());
            t.putIntArray("box", new int[]{box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()});
            t.putString("kind", kind);
            t.putIntArray("pos", rel);
            int[] s = new int[(st.length + 1) / 2];                // two 16-bit palette indexes per int
            for (int i = 0; i < st.length; i++) s[i >> 1] |= (st[i] & 0xFFFF) << ((i & 1) * 16);
            t.putIntArray("st", s);
            ListTag pal = new ListTag();
            for (BlockState b : palette) pal.add(NbtUtils.writeBlockState(b));
            t.put("palette", pal);
            t.putLong("seen", lastSeen);
            return t;
        }

        static Village read(CompoundTag t) {
            int[] b = t.getIntArray("box");
            if (b.length != 6) return null;
            int[] rel = t.getIntArray("pos");
            int[] s = t.getIntArray("st");
            ListTag pal = t.getList("palette", Tag.TAG_COMPOUND);
            BlockState[] palette = new BlockState[pal.size()];
            for (int i = 0; i < palette.length; i++) palette[i] = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), pal.getCompound(i));
            short[] st = new short[rel.length];
            for (int i = 0; i < st.length; i++) {
                if ((i >> 1) >= s.length) return null;
                st[i] = (short) ((s[i >> 1] >>> ((i & 1) * 16)) & 0xFFFF);
                if ((st[i] & 0xFFFF) >= palette.length) return null;
            }
            Village v = new Village(t.getInt("id"), BlockPos.of(t.getLong("anchor")), new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]), t.getString("kind"), rel, st, palette);
            v.lastSeen = t.getLong("seen");
            return v;
        }
    }
}
