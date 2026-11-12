package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.*;

/**
 * With DragonMineZ: a character out with a Dragon Radar finds the Dragon Balls (OffDuty's "Searching for the Dragon
 * Balls"). When the search is over it brings back every ball of the radar's set it could find - the real ones from the
 * world (taken out of the ground and out of DragonMineZ's ball tracking, as if picked up), or ones that haven't come
 * down yet. A ball someone already holds can't be found.
 */
public final class DmzBotRadar {
    private DmzBotRadar() {}

    /** radar item -> the ball sets it finds (set id, item suffix, world) */
    static final Map<String, String[][]> RADARS = Map.of(
            "dragonminez:dball_radar", new String[][]{{"earth", "", "minecraft:overworld"}},
            "dragonminez:namekdball_radar", new String[][]{{"namek", "_namek", "dragonminez:namek"}},
            "dragonminez:fused_dball_radar", new String[][]{{"earth", "", "minecraft:overworld"}, {"namek", "_namek", "dragonminez:namek"}});

    private static Boolean loaded;
    private static boolean broken;
    private static Method dataGet, activeBalls, pendingBalls, unregister;
    /** (Tests) balls found so far. */
    public static int found;

    static boolean ready() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("dragonminez");
        if (!loaded || broken) return false;
        if (dataGet != null) return true;
        try {
            Class<?> d = Class.forName("com.dragonminez.server.world.data.DragonBallSavedData");
            dataGet = d.getMethod("get", ServerLevel.class);
            activeBalls = d.getMethod("getActiveBalls", String.class);
            pendingBalls = d.getMethod("getPendingBalls", String.class);
            unregister = Class.forName("com.dragonminez.server.events.DragonBallsHandler").getMethod("unregisterConsumedDragonBalls", ServerLevel.class, Collection.class, String.class);
            return true;
        } catch (Throwable t) {
            broken = true; dataGet = null;
            XenoWarriors.LOGGER.warn("Bots can't hunt Dragon Balls (DragonMineZ's API changed?)", t);
            return false;
        }
    }

    public static boolean isRadar(ItemStack s) {
        if (s.isEmpty()) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
        return id != null && RADARS.containsKey(id.toString());
    }

    /** The radar's item id, or null. */
    public static String radarId(ItemStack s) { return isRadar(s) ? ForgeRegistries.ITEMS.getKey(s.getItem()).toString() : null; }

    /** Finds the balls this radar points to. Returns the ball items found (empty: none). */
    @SuppressWarnings("unchecked")
    public static List<ItemStack> collect(ServerPlayer p, String radar) {
        List<ItemStack> out = new ArrayList<>();
        if (!ready() || radar == null || !RADARS.containsKey(radar)) return out;
        for (String[] set : RADARS.get(radar)) {
            ServerLevel level = p.server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(set[2])));
            if (level == null) continue;
            try {
                Object data = dataGet.invoke(null, level);
                Map<Integer, List<BlockPos>> active = (Map<Integer, List<BlockPos>>) activeBalls.invoke(data, set[0]);
                Map<Integer, List<BlockPos>> pending = (Map<Integer, List<BlockPos>>) pendingBalls.invoke(data, set[0]);
                List<BlockPos> taken = new ArrayList<>();
                for (int star = 1; star <= 7; star++) {
                    Item ball = ForgeRegistries.ITEMS.getValue(new ResourceLocation("dragonminez", "dball" + star + set[1]));
                    if (ball == null || ball == net.minecraft.world.item.Items.AIR) continue;
                    boolean got = false;
                    List<BlockPos> a = active == null ? null : active.get(star);
                    if (a != null && !a.isEmpty()) {
                        BlockPos pos = a.get(0);
                        level.getChunk(pos);                                          // (loads it if need be)
                        var block = ForgeRegistries.ITEMS.getKey(level.getBlockState(pos).getBlock().asItem());
                        if (block != null && block.getPath().startsWith("dball")) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                        taken.add(pos);
                        got = true;
                    } else {
                        List<BlockPos> q = pending == null ? null : pending.get(star);
                        if (q != null && !q.isEmpty()) { q.remove(0); got = true; ((net.minecraft.world.level.saveddata.SavedData) data).setDirty(); }
                    }
                    if (got) { out.add(new ItemStack(ball)); found++; }
                }
                if (!taken.isEmpty()) unregister.invoke(null, level, taken, set[0]);
            } catch (Throwable t) {
                XenoWarriors.LOGGER.warn("A bot couldn't find the Dragon Balls", t);
            }
        }
        return out;
    }
}
