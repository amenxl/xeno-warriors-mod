package com.xenowarriors.compat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.ModList;

/** Calls into Epic Fight for the townsfolk (only when it's installed). */
public final class EfNpcHooks {
    private static Boolean loaded;

    private EfNpcHooks() {}

    public static boolean loaded() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("epicfight");
        return loaded;
    }

    /** Epic Fight's throwing motion for a ranged attack. */
    public static void throwAnimation(LivingEntity e) {
        if (!loaded()) return;
        try {
            com.xenowarriors.compat.epicfight.EfNpcPatches.throwAnimation(e);
        } catch (Throwable ignored) {}
    }
}
