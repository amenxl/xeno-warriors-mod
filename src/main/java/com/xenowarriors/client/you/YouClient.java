package com.xenowarriors.client.you;

import com.xenowarriors.you.YouSlot;
import net.minecraft.client.Minecraft;

/** Client state for You characters: your slots list (for the slots screen) and your HUD state (Sun meter, seed, form). */
public final class YouClient {
    private YouClient() {}

    public static YouSlot[] slots = new YouSlot[0];
    public static int[] levels = new int[0];
    public static int current = -1;
    /** -1 unless you're a Seedling. */
    public static int sun = -1;
    public static boolean seed;
    public static String form = "";

    public static void slots(YouSlot[] s, int[] lv, int cur, boolean open) {
        slots = s; levels = lv; current = cur;
        Minecraft mc = Minecraft.getInstance();
        if (open) mc.setScreen(new YouSlotsScreen());
        else if (mc.screen instanceof YouSlotsScreen || mc.screen instanceof YouCreatorScreen) mc.setScreen(null);
    }

    public static void state(int sun, boolean seed, String form) { YouClient.sun = sun; YouClient.seed = seed; YouClient.form = form; }

    public static void clear() { slots = new YouSlot[0]; levels = new int[0]; current = -1; sun = -1; seed = false; form = ""; }
}
