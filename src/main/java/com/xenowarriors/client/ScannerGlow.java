package com.xenowarriors.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.HashSet;
import java.util.Set;

/** Scanner Eye (Gov human mod): enemies the server marked glow through walls - for its user only. */
public final class ScannerGlow {
    private static Set<Integer> ids = new HashSet<>();
    private static long until;

    private ScannerGlow() {}

    public static void set(int[] list) {
        Set<Integer> s = new HashSet<>();
        for (int i : list) s.add(i);
        ids = s;
        var l = Minecraft.getInstance().level;
        until = l == null ? 0 : l.getGameTime() + 50;
    }

    public static boolean has(Entity e) {
        return !ids.isEmpty() && e.level().getGameTime() < until && ids.contains(e.getId());
    }
}
