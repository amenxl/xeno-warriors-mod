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

    /** The running world's setting, for world generation threads (the data storage itself is only safe on the server thread). */
    private static volatile int cached = 1;

    public static Seasons get(MinecraftServer server) {
        Seasons s = server.overworld().getDataStorage().computeIfAbsent(Seasons::load, Seasons::new, "xenowarriors_seasons");
        cached = s.unlocked;
        return s;
    }

    /**
     * For world generation (off the server thread): the season setting as the server thread last read it - it's
     * read when the overworld loads (see AuroraCells) and whenever it's checked or changed.
     */
    public static boolean unlockedForWorldgen(int season) { return cached >= season; }

    /** Reads this world's setting as its overworld loads (before any chunk of it generates). */
    public static void read(net.minecraft.server.level.ServerLevel overworld) {
        cached = overworld.getDataStorage().computeIfAbsent(Seasons::load, Seasons::new, "xenowarriors_seasons").unlocked;
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

    /** (UI test) forget the setting read in this JVM, as a dedicated server's client never has it. */
    public static void testForgetCached() { cached = 1; }

    public static void set(MinecraftServer server, int season) {
        Seasons s = get(server);
        s.unlocked = Math.max(1, Math.min(3, season));
        cached = s.unlocked;
        s.setDirty();
    }
}
