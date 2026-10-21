package com.xenowarriors.client.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/**
 * Reads DragonMineZ's state for a player (charging Ki, aura on) without depending on DragonMineZ: everything is
 * looked up by name, and if DragonMineZ isn't installed (or changes) this simply reports "no".
 */
public final class DmzCompat {
    private static Boolean loaded;
    private static boolean broken;
    private static Capability<?> cap;
    private static Method getStatus, isChargingKi, isActionCharging, isAuraActive, isTailVisible, getCharacter, getRaceName;

    private DmzCompat() {}

    private static boolean ready() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("dragonminez");
        if (!loaded || broken) return false;
        if (cap == null) {
            try {
                Class<?> capClass = Class.forName("com.dragonminez.common.stats.StatsCapability");
                cap = (Capability<?>) capClass.getField("INSTANCE").get(null);
                Class<?> data = Class.forName("com.dragonminez.common.stats.StatsData");
                getStatus = data.getMethod("getStatus");
                Class<?> status = getStatus.getReturnType();
                isChargingKi = status.getMethod("isChargingKi");
                isActionCharging = status.getMethod("isActionCharging");
                isAuraActive = status.getMethod("isAuraActive");
                try { isTailVisible = status.getMethod("isTailVisible"); } catch (NoSuchMethodException ignored) {}
                try {
                    getCharacter = data.getMethod("getCharacter");
                    getRaceName = getCharacter.getReturnType().getMethod("getRaceName");
                } catch (NoSuchMethodException ignored) {}
            } catch (Throwable t) {
                broken = true;
                return false;
            }
        }
        return cap != null;
    }

    private static Object status(Player p) {
        if (!ready()) return null;
        try {
            Object data = p.getCapability(cap).resolve().orElse(null);
            return data == null ? null : getStatus.invoke(data);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean flag(Player p, Method m) {
        Object s = status(p);
        if (s == null) return false;
        try {
            return (Boolean) m.invoke(s);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Powering up: charging Ki (or charging a DragonMineZ attack). */
    public static boolean charging(Player p) {
        return ready() && (flag(p, isChargingKi) || flag(p, isActionCharging));
    }

    /** DragonMineZ race id ("saiyan", "human"...), or null. */
    public static String race(Player p) {
        if (!ready() || getCharacter == null || getRaceName == null) return null;
        try {
            Object data = p.getCapability(cap).resolve().orElse(null);
            if (data == null) return null;
            Object r = getRaceName.invoke(getCharacter.invoke(data));
            return r == null ? null : r.toString().toLowerCase();
        } catch (Throwable t) {
            return null;
        }
    }

    /** A Saiyan with their tail out. */
    public static boolean tail(Player p) {
        return isTailVisible != null && "saiyan".equals(race(p)) && flag(p, isTailVisible);
    }

    public static boolean aura(Player p) {
        return ready() && flag(p, isAuraActive);
    }
}
