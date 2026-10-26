package com.xenowarriors.character;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Story content by season, so nothing spoils episodes that aren't out yet. Season 1 is always on; Season 2 and 3 content
 * (forms, bosses, quests) stays locked in a world until an operator runs /xeno season unlock 2 (or 3).
 */
public final class Seasons extends SavedData {
    private int unlocked = 1;

    public static Seasons get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(Seasons::load, Seasons::new, "xenowarriors_seasons");
    }

    private static Seasons load(CompoundTag tag) {
        Seasons s = new Seasons();
        s.unlocked = Math.max(1, tag.getInt("unlocked"));
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("unlocked", unlocked);
        return tag;
    }

    public static boolean unlocked(MinecraftServer server, int season) { return server != null && get(server).unlocked >= season; }

    public static int highest(MinecraftServer server) { return get(server).unlocked; }

    public static void set(MinecraftServer server, int season) {
        Seasons s = get(server);
        s.unlocked = Math.max(1, Math.min(3, season));
        s.setDirty();
    }
}
