package com.xenowarriors.pickup;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;

/**
 * Handing an experience orb straight to a player (a Journey's leftover drops). An orb can hold more than its
 * {@code getValue()}: vanilla stacks same-sized orbs into one (its private count), and Clumps merges orbs of any size
 * into one. So the orb is "touched" the normal way until it's used up, which gives all of it (and Mending, and
 * whatever other mods do on pickup).
 */
public final class XpOrbs {
    private XpOrbs() {}

    /** Gives the player everything in the orb and removes it. Returns how much experience the player gained. */
    public static int giveAll(ServerPlayer p, ExperienceOrb orb) {
        int before = p.totalExperience;
        int delay = p.takeXpDelay;
        for (int i = 0; i < 256 && orb.isAlive(); i++) {
            p.takeXpDelay = 0;
            orb.playerTouch(p);
        }
        p.takeXpDelay = delay;
        if (orb.isAlive()) {                       // (something refused the pickup: at least its face value, as before)
            p.giveExperiencePoints(orb.getValue());
            orb.discard();
        }
        return p.totalExperience - before;
    }
}
