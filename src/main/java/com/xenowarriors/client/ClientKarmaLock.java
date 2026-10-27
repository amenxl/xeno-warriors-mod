package com.xenowarriors.client;

import net.minecraft.Util;

/** When the local player's Karma is drained by a Xeno Blast (no regen) - for the HUD. */
public final class ClientKarmaLock {
    private static long until;

    private ClientKarmaLock() {}

    public static void set(int ticks) { until = Util.getMillis() + ticks * 50L; }

    /** Seconds left (0 = not locked). */
    public static int secondsLeft() { return (int) Math.max(0, (until - Util.getMillis() + 999) / 1000); }
}
