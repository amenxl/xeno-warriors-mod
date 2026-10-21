package com.xenowarriors.fox;

/**
 * How Mikiko's foxes grow: XP needed per level and what each level gives. Shared by server and client.
 * Levels go all the way to 9,000. Up to 50 every level counts fully; past that the gains keep coming but taper off
 * (so a level 9,000 fox is mighty without being able to one-shot the world).
 */
public final class FoxLevels {
    public static final int MAX = 9000;
    private static final int FULL = 50;

    private FoxLevels() {}

    private static double past(int level) {
        return Math.sqrt(Math.max(0, level - FULL));
    }

    /** XP needed to go from this level to the next. */
    public static int xpFor(int level) {
        return 20 + 15 * Math.min(level, FULL) + 2 * Math.max(0, level - FULL);
    }

    /** Extra max health (half-hearts) at this level. */
    public static double health(int level) {
        return (Math.min(level, FULL) - 1) * 1.0 + past(level) * 2.0;
    }

    /** Extra bite damage at this level. */
    public static double attack(int level) {
        return (Math.min(level, FULL) - 1) * 0.2 + past(level) * 0.25;
    }

    /** Extra speed (fraction of base) at this level. */
    public static double speed(int level) {
        return Math.min(0.4, (Math.min(level, FULL) - 1) * 0.003 + past(level) * 0.002);
    }
}
