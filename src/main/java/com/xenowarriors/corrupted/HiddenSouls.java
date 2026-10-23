package com.xenowarriors.corrupted;

import net.minecraft.world.entity.LivingEntity;

/**
 * Some corrupted souls slip into people and stay hidden: no glow, no change in behaviour - they go about their day
 * and don't attack anyone. Only Karma Sensing shows what's inside them (and Mikiko's Hekublade can draw it out).
 */
public final class HiddenSouls {
    public static final String TAG = "xw_hidden_soul";
    public static final float CHANCE = 0.4F;

    private HiddenSouls() {}

    public static boolean hidden(LivingEntity e) {
        return e.getPersistentData().getBoolean(TAG);
    }

    public static void hide(net.minecraft.world.entity.Mob host) {
        host.getPersistentData().putBoolean(TAG, true);
        host.setPersistenceRequired();
    }

    public static void clear(LivingEntity e) {
        e.getPersistentData().remove(TAG);
    }
}
