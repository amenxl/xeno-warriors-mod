package com.xenowarriors.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;

/**
 * A bot summoned DragonMineZ's dragon for you: its wish menu opens, just as if you'd right-clicked the dragon
 * yourself (DragonMineZ's own screen; the wish goes through its own packet, which checks you're the summoner).
 */
public final class DmzWishOpen {
    private DmzWishOpen() {}

    /** (Tests) the last dragon whose menu was opened this way, and the screen it gave. */
    public static int lastDragon = -1;
    public static String lastScreen = "";

    public static void open(int id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        var e = mc.level.getEntity(id);
        if (!(e instanceof Mob dragon)) return;
        if (mc.screen != null) mc.setScreen(null);
        dragon.interact(mc.player, InteractionHand.MAIN_HAND);                   // (its mobInteract opens the wishes screen for its summoner)
        lastDragon = id;
        lastScreen = mc.screen == null ? "none" : mc.screen.getClass().getName();
    }
}
