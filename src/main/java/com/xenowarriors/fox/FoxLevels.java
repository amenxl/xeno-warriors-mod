package com.xenowarriors.fox;

/** How Mikiko's foxes grow: XP needed per level and what each level gives. Shared by server and client. */
public final class FoxLevels {
    public static final int MAX = 50;

    private FoxLevels() {}

    /** XP needed to go from this level to the next. */
    public static int xpFor(int level) {
        return 20 + 15 * level;
    }

    /** Extra max health (half-hearts) at this level. */
    public static double health(int level) {
        return (level - 1) * 1.0;
    }

    /** Extra bite damage at this level. */
    public static double attack(int level) {
        return (level - 1) * 0.2;
    }

    /** Extra speed (fraction of base) at this level. */
    public static double speed(int level) {
        return (level - 1) * 0.003;
    }
}
