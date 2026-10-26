package com.xenowarriors.client;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/** Reads the local player's DragonMineZ numbers for our HUD: Ki, stamina, power release, form. */
public final class DmzHud {
    private static Boolean loaded;
    private static boolean broken;
    private static Capability<?> cap;
    private static Method resources, maxEnergy, maxStamina, energy, stamina, release, character, activeForm;
    public static float ki, maxKi, sta, maxSta;
    public static int powerRelease;
    public static String form = "";

    private DmzHud() {}

    public static boolean on() {
        if (loaded == null) loaded = ModList.get().isLoaded("dragonminez");
        if (!loaded || broken) return false;
        if (cap == null) {
            try {
                cap = (Capability<?>) Class.forName("com.dragonminez.common.stats.StatsCapability").getField("INSTANCE").get(null);
                Class<?> d = Class.forName("com.dragonminez.common.stats.StatsData");
                resources = d.getMethod("getResources");
                maxEnergy = d.getMethod("getMaxEnergy");
                maxStamina = d.getMethod("getMaxStamina");
                character = d.getMethod("getCharacter");
                Class<?> r = resources.getReturnType();
                energy = r.getMethod("getCurrentEnergy");
                stamina = r.getMethod("getCurrentStamina");
                release = r.getMethod("getPowerRelease");
                activeForm = character.getReturnType().getMethod("getActiveForm");
            } catch (Throwable t) { broken = true; return false; }
        }
        return true;
    }

    /** Refreshes the numbers; false if there's nothing to show. */
    public static boolean read(Player p) {
        if (!on()) return false;
        try {
            Object data = p.getCapability(cap).resolve().orElse(null);
            if (data == null) return false;
            Object res = resources.invoke(data);
            ki = ((Number) energy.invoke(res)).floatValue();
            maxKi = ((Number) maxEnergy.invoke(data)).floatValue();
            sta = ((Number) stamina.invoke(res)).floatValue();
            maxSta = ((Number) maxStamina.invoke(data)).floatValue();
            powerRelease = ((Number) release.invoke(res)).intValue();
            Object f = activeForm.invoke(character.invoke(data));
            form = f == null || f.toString().isEmpty() ? "Base" : pretty(f.toString());
            return maxKi > 0;
        } catch (Throwable t) { return false; }
    }

    private static String pretty(String id) {
        String s = id.replace('_', ' ');
        StringBuilder b = new StringBuilder();
        for (String w : s.split(" ")) if (!w.isEmpty()) b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        return b.toString().trim();
    }
}
