package com.xenowarriors.recarnet;

import com.xenowarriors.XenoWarriors;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * The Royal Capital floats over Aurora and would leave the ground under it in a big dark shadow. This lights the ground
 * underneath with invisible light blocks on a grid (only where something of the capital is overhead). Runs on chunk
 * load, so it also fixes worlds made before this existed; each column is only done once (the light block is found
 * on the next scan).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CapitalUnderlight {
    private static final int GRID = 5;
    private static final ArrayDeque<ChunkPos> QUEUE = new ArrayDeque<>();
    private static final Set<Long> QUEUED = new HashSet<>();

    private CapitalUnderlight() {}

    @SubscribeEvent
    public static void onLoad(ChunkEvent.Load e) {
        if (!(e.getLevel() instanceof ServerLevel l) || !Recarnet.is(l)) return;
        ChunkPos c = e.getChunk().getPos();
        double cx = c.getMiddleBlockX(), cz = c.getMiddleBlockZ();
        if (Math.sqrt(cx * cx + cz * cz) > RoyalCapitalFeature.R + 30) return;
        synchronized (QUEUE) { if (QUEUED.add(c.toLong())) QUEUE.add(c); }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel l) || !Recarnet.is(l)) return;
        for (int n = 0; n < 4; n++) {
            ChunkPos c;
            synchronized (QUEUE) { c = QUEUE.poll(); if (c != null) QUEUED.remove(c.toLong()); }
            if (c == null) return;
            LevelChunk ch = l.getChunkSource().getChunkNow(c.x, c.z);
            if (ch != null) light(l, ch);
        }
    }

    private static void light(ServerLevel l, LevelChunk ch) {
        int x0 = ch.getPos().getMinBlockX(), z0 = ch.getPos().getMinBlockZ();
        int floor = RoyalCapitalFeature.FLOOR_Y, bottom = l.getMinBuildHeight();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = Math.floorMod(-x0, GRID); x < 16; x += GRID) for (int z = Math.floorMod(-z0, GRID); z < 16; z += GRID) {
            // only under the capital: something solid above the floor height in (or right beside) this column
            if (!shaded(ch, x, z, floor)) continue;
            int y = floor - 1;
            for (; y > bottom; y--) { m.set(x0 + x, y, z0 + z); if (!l.getBlockState(m).isAir()) break; }
            if (y <= bottom || l.getBlockState(m).is(Blocks.LIGHT)) continue;
            m.set(x0 + x, y + 2, z0 + z);
            if (!l.getBlockState(m).isAir() || !l.getBlockState(m.below()).isAir()) continue;
            l.setBlock(m, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15), 2);
        }
    }

    private static boolean shaded(LevelChunk ch, int x, int z, int floor) {
        for (int dx = -2; dx <= 2; dx += 2) for (int dz = -2; dz <= 2; dz += 2) {
            int ax = Math.max(0, Math.min(15, x + dx)), az = Math.max(0, Math.min(15, z + dz));
            if (ch.getHeight(Heightmap.Types.WORLD_SURFACE, ax, az) >= floor) return true;
        }
        return false;
    }
}
