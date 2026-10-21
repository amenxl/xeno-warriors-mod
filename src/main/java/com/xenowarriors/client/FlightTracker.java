package com.xenowarriors.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

import java.util.HashMap;
import java.util.Map;

/**
 * Works out whether a player is flying. Your own flying state is known exactly, but the game
 * doesn't tell you when OTHER players are creative-flying, so for them we treat "in the air for a
 * while and not falling fast" (or gliding with an elytra) as flying.
 */
public final class FlightTracker {
    private static final Map<Integer, Integer> AIR_TICKS = new HashMap<>();

    private FlightTracker() {}

    /** Call once per client tick for every player in the world. */
    public static void tick(AbstractClientPlayer player) {
        boolean airborne = !player.onGround() && !player.isInWater() && !player.onClimbable() && !player.isPassenger();
        AIR_TICKS.put(player.getId(), airborne ? AIR_TICKS.getOrDefault(player.getId(), 0) + 1 : 0);
    }

    public static boolean isFlying(AbstractClientPlayer player) {
        if (player.isFallFlying()) return true;
        if (player == Minecraft.getInstance().player) return player.getAbilities().flying;
        double dy = player.getY() - player.yo;
        return AIR_TICKS.getOrDefault(player.getId(), 0) > 14 && dy > -0.35;
    }

    public static void clear() {
        AIR_TICKS.clear();
    }
}
