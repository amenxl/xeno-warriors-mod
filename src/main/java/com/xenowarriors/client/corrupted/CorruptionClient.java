package com.xenowarriors.client.corrupted;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

/** Which animals the server says are possessed (for the purple tint). */
public final class CorruptionClient {
    private static final IntSet CORRUPTED = new IntOpenHashSet();

    private CorruptionClient() {}

    public static void set(int id, boolean on) {
        if (on) CORRUPTED.add(id);
        else CORRUPTED.remove(id);
    }

    public static boolean is(int id) {
        return CORRUPTED.contains(id);
    }

    public static void clear() {
        CORRUPTED.clear();
    }
}
