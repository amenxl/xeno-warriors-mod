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

    private AttackHooks() {}
}
