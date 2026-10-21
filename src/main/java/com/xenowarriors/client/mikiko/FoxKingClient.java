package com.xenowarriors.client.mikiko;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.world.entity.Entity;

/** Which Mikikos are in Fox King mode right now (drawn with the Fox King hair and mask). */
public final class FoxKingClient {
    private static final IntSet ACTIVE = new IntOpenHashSet();

    private FoxKingClient() {}

    public static void set(int id, boolean on) {
        if (on) ACTIVE.add(id);
        else ACTIVE.remove(id);
    }

    public static boolean active(Entity e) {
        return ACTIVE.contains(e.getId());
    }
}
