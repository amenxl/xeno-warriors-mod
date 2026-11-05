package com.xenowarriors.forms;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Summoned gun sounds, made from vanilla sounds (no other mod's files). Called on the server with the shooter as
 * {@code except} (everyone else hears it) and on the shooter's client with the local player (heard at once, no lag).
 */
public final class GunSounds {
    private GunSounds() {}

    public static void shot(Level level, Player except, double x, double y, double z, Form form, Form.Style style) {
        switch (form) {
            case RIFLE -> {
                level.playSound(except, x, y, z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 0.9F, 0.95F + level.random.nextFloat() * 0.1F);
                level.playSound(except, x, y, z, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 0.25F, 1.9F);
            }
            case SHOTGUN -> {
                level.playSound(except, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.55F, 1.55F);
                level.playSound(except, x, y, z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 0.8F, 0.6F);
            }
            default -> {
                level.playSound(except, x, y, z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.85F, 1.45F + level.random.nextFloat() * 0.1F);
                level.playSound(except, x, y, z, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 0.3F, 2.0F);
            }
        }
        if (style == Form.Style.HEKU) level.playSound(except, x, y, z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.18F, 1.8F);
        else level.playSound(except, x, y, z, SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.35F, 1.4F);
    }

    /** The shotgun's pump (half-way through the gap between shots). */
    public static void pump(Level level, Player except, double x, double y, double z, boolean back) {
        level.playSound(except, x, y, z, back ? SoundEvents.PISTON_CONTRACT : SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 0.35F, back ? 1.8F : 2.0F);
    }

    /** Aiming in / out: a quiet cloth-and-metal shuffle. */
    public static void aim(Level level, Player except, double x, double y, double z, boolean in) {
        level.playSound(except, x, y, z, SoundEvents.ARMOR_EQUIP_GENERIC, SoundSource.PLAYERS, 0.35F, in ? 1.5F : 1.25F);
    }
}
