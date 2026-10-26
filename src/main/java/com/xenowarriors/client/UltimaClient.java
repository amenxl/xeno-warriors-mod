package com.xenowarriors.client;

import java.util.HashMap;
import java.util.Map;

/** What the client knows about Ultimas' output (for the HUD and the Overload face). */
public final class UltimaClient {
    private static final Map<Integer, int[]> STATE = new HashMap<>();

    private UltimaClient() {}

    public static void set(int id, int tier, boolean over) { STATE.put(id, new int[]{tier, over ? 1 : 0}); }

    public static int tier(int id) { int[] s = STATE.get(id); return s == null ? 0 : s[0]; }

    public static boolean overload(int id) { int[] s = STATE.get(id); return s != null && s[1] == 1; }

    public static int percent(int id) { return overload(id) ? 120 : com.xenowarriors.ultima.UltimaOutput.PERCENT[Math.min(3, tier(id))]; }
}
