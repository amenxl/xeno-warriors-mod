package com.xenowarriors.compat;

import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.ModList;

/** A pet's DragonMineZ Ki, if DragonMineZ gave it any (by reflection; {0, 0} when there's none). Server-safe. */
public final class DmzPetKi {
    private static Boolean loaded;
    private static boolean broken;
    private static net.minecraftforge.common.capabilities.Capability<?> cap;
    private static java.lang.reflect.Method getResources, getKi, getMaxKi;

    private DmzPetKi() {}

    public static float[] ki(Entity e) {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("dragonminez");
        if (!loaded || broken) return new float[]{0, 0};
        try {
            if (cap == null) {
                cap = (net.minecraftforge.common.capabilities.Capability<?>) Class.forName("com.dragonminez.common.stats.StatsCapability").getField("INSTANCE").get(null);
                Class<?> data = Class.forName("com.dragonminez.common.stats.StatsData");
                getResources = data.getMethod("getResources");
                getKi = getResources.getReturnType().getMethod("getCurrentEnergy");
                getMaxKi = data.getMethod("getMaxEnergy");
            }
            Object d = e.getCapability(cap).resolve().orElse(null);
            if (d == null) return new float[]{0, 0};
            Object res = getResources.invoke(d);
            float ki = ((Number) getKi.invoke(res)).floatValue();
            float max = ((Number) getMaxKi.invoke(d)).floatValue();
            return new float[]{ki, Math.max(max, ki)};
        } catch (Throwable t) {
            broken = true;
            return new float[]{0, 0};
        }
    }
}
