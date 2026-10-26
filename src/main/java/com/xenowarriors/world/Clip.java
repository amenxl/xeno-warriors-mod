package com.xenowarriors.world;

import net.minecraft.core.BlockPos;

/**
 * Lets the structure builders (Government facility, Order hideout...) draw just one chunk's slice of themselves, so a
 * city can place them chunk by chunk during world generation - each chunk asks for the whole building, clipped.
 */
public final class Clip {
    private static final ThreadLocal<int[]> BOX = new ThreadLocal<>();

    private Clip() {}

    public static void set(int minX, int minZ, int maxX, int maxZ) { BOX.set(new int[]{minX, minZ, maxX, maxZ}); }

    public static void clear() { BOX.remove(); }

    public static boolean ok(BlockPos p) {
        int[] b = BOX.get();
        return b == null || (p.getX() >= b[0] && p.getX() <= b[2] && p.getZ() >= b[1] && p.getZ() <= b[3]);
    }
}
