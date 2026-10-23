package com.xenowarriors.client;

import net.minecraft.world.entity.player.Player;

import java.util.function.Predicate;

/**
 * Attacks started by another combat mod rather than a vanilla swing (Epic Fight's battle mode). Returns true once
 * per attack, so our own attack animation for that character plays.
 */
public final class AttackHooks {
    public static Predicate<Player> externalAttack = p -> false;
    /** In the middle of one of the other mod's actions (dodging, guarding, a skill...). */
    public static Predicate<Player> externalBusy = p -> false;
    /** Ticks until the other mod's current attack lands its hit (-1 if unknown): our attack is timed to it. */
    public static java.util.function.ToDoubleFunction<Player> externalHitTicks = p -> -1;

    /**
     * How much to stretch (>1) or speed up (<1) one of our attacks so its strike lands when the other mod's hit
     * does. {@code strikeAt} = the fraction of our animation where the strike lands.
     */
    public static float timeScale(Player p, float ourTicks, float strikeAt) {
        double hit = externalHitTicks.applyAsDouble(p);
        if (hit <= 0 || ourTicks <= 0) return 1F;
        return net.minecraft.util.Mth.clamp((float) (hit / (ourTicks * strikeAt)), 0.6F, 1.6F);
    }

    private AttackHooks() {}
}
