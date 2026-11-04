package com.xenowarriors.recarnet;

import com.xenowarriors.XenoWarriors;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers, for each Aurora City block that holds an Order of Assassins cell once Season 3 is out, whether the block
 * started generating before or after Season 3 was unlocked. A city block spans several chunks; without this, the
 * chunks generated before /xeno season unlock 3 drew a plain mid-rise and the ones after drew the Order's building,
 * leaving half of each. The first chunk of a block decides for the whole block.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AuroraCells extends SavedData {
    private static final String NAME = "xenowarriors_aurora_cells";
    /** cell key -> Season 3 was unlocked when it started. Read and written by world generation threads. */
    private static final Map<Long, Boolean> CELLS = new ConcurrentHashMap<>();
    private static volatile AuroraCells data;

    private AuroraCells() {}

    private static long key(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL); }

    /** Whether this block is drawn with Season 3 content (decided by its first chunk; {@code s3Now} for a new block). */
    public static boolean s3(int cx, int cz, boolean s3Now) {
        Boolean was = CELLS.putIfAbsent(key(cx, cz), s3Now);
        if (was != null) return was;
        AuroraCells d = data;
        if (d != null) d.setDirty();
        return s3Now;
    }

    private static AuroraCells load(CompoundTag tag) {
        AuroraCells d = new AuroraCells();
        for (var t : tag.getList("s3", 4)) CELLS.put(((LongTag) t).getAsLong(), true);
        for (var t : tag.getList("s1", 4)) CELLS.put(((LongTag) t).getAsLong(), false);
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag yes = new ListTag(), no = new ListTag();
        for (var e : CELLS.entrySet()) (e.getValue() ? yes : no).add(LongTag.valueOf(e.getKey()));
        tag.put("s3", yes);
        tag.put("s1", no);
        return tag;
    }

    /** The overworld loads before anything generates: read the cells (and the season setting) for this world. */
    @SubscribeEvent
    public static void onLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel l) || l.dimension() != Level.OVERWORLD) return;
        CELLS.clear();
        data = l.getDataStorage().computeIfAbsent(AuroraCells::load, AuroraCells::new, NAME);
        com.xenowarriors.character.Seasons.read(l);                           // (its copy for world generation)
    }
}
