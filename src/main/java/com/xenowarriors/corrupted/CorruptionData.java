package com.xenowarriors.corrupted;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Every Corrupted Ore block that's open to the air (and so spreading), bucketed by chunk section for quick lookups. */
public class CorruptionData extends SavedData {
    private final Long2ObjectOpenHashMap<LongOpenHashSet> bySection = new Long2ObjectOpenHashMap<>();

    public static CorruptionData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(CorruptionData::load, CorruptionData::new, "xenowarriors_corruption");
    }

    private static long section(BlockPos p) {
        return SectionPos.asLong(SectionPos.blockToSectionCoord(p.getX()), SectionPos.blockToSectionCoord(p.getY()),
                SectionPos.blockToSectionCoord(p.getZ()));
    }

    public void add(BlockPos p) {
        if (bySection.computeIfAbsent(section(p), k -> new LongOpenHashSet()).add(p.asLong())) setDirty();
    }

    public void remove(BlockPos p) {
        long k = section(p);
        LongOpenHashSet set = bySection.get(k);
        if (set != null && set.remove(p.asLong())) {
            if (set.isEmpty()) bySection.remove(k);
            setDirty();
        }
    }

    /** The closest open ore within reach (checked to still be there), or null. */
    public BlockPos nearest(ServerLevel level, BlockPos p, int reach) {
        int sx = SectionPos.blockToSectionCoord(p.getX()), sy = SectionPos.blockToSectionCoord(p.getY()), sz = SectionPos.blockToSectionCoord(p.getZ());
        BlockPos best = null;
        double bestD = (double) reach * reach;
        java.util.List<BlockPos> stale = null;
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            LongOpenHashSet set = bySection.get(SectionPos.asLong(sx + dx, sy + dy, sz + dz));
            if (set == null) continue;
            for (long l : set) {
                BlockPos o = BlockPos.of(l);
                double d = o.distSqr(p);
                if (d > bestD) continue;
                if (level.isLoaded(o) && !level.getBlockState(o).is(com.xenowarriors.sky.ModBlocks.CORRUPTED_ORE.get())) {
                    if (stale == null) stale = new java.util.ArrayList<>();
                    stale.add(o);
                    continue;
                }
                best = o;
                bestD = d;
            }
        }
        if (stale != null) stale.forEach(this::remove);
        return best;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        LongOpenHashSet all = new LongOpenHashSet();
        bySection.values().forEach(all::addAll);
        tag.putLongArray("ores", all.toLongArray());
        return tag;
    }

    public static CorruptionData load(CompoundTag tag) {
        CorruptionData d = new CorruptionData();
        for (long l : tag.getLongArray("ores")) d.add(BlockPos.of(l));
        d.setDirty(false);
        return d;
    }
}
