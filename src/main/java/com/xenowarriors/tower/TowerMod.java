package com.xenowarriors.tower;

/** A twist on an ordinary Tower floor (from floor 3, about 2 floors in 5). Shown under the floor title. */
public enum TowerMod {
    NONE("", ""),
    LOW_GRAVITY("Low Gravity", "everyone floats and leaps"),
    NO_HEALING("No Healing", "no healing on this floor at all"),
    ELITE("Elite", "enemies have +60% health and glow"),
    DARKNESS("Darkness", "you can barely see - enemies glow"),
    SWIFT("Swift", "enemies are much faster"),
    HORDE("Horde", "50% more enemies - bonus Money"),
    KARMA_DRAIN("Karma Drain", "your Karma won't come back on its own");

    public final String title, desc;
    TowerMod(String title, String desc) { this.title = title; this.desc = desc; }

    /** (UI tests) force the next floor's twist. */
    public static TowerMod forceNext;

    /** (UI tests) force the next floor's Famished roll. */
    public static Boolean forceFamished;

    /** Chance a floor is Famished (hunger drains faster): none below floor 10, then +3% every 10 floors, up to 40%. */
    public static float famishedChance(int floor) { return floor < 10 ? 0F : Math.min(0.40F, 0.03F * (floor / 10)); }

    static boolean famished(int floor, net.minecraft.util.RandomSource r) {
        if (forceFamished != null) { boolean f = forceFamished; forceFamished = null; return f; }
        return r.nextFloat() < famishedChance(floor);
    }

    static TowerMod roll(int floor, net.minecraft.util.RandomSource r) {
        if (forceNext != null) { TowerMod m = forceNext; forceNext = null; return m; }
        if (floor < 3 || floor % 10 == 0 || r.nextInt(5) >= 2) return NONE;
        TowerMod[] all = values();
        return all[1 + r.nextInt(all.length - 1)];
    }
}
