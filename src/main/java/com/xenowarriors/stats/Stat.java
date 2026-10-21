package com.xenowarriors.stats;

/**
 * Stats that grow by doing things - no skill tree.
 *  SPEED   - levels up by sprinting; each level makes you 1% faster
 *  ATTACK  - levels up by dealing damage; each level is +2% damage (melee and skills)
 *  DEFENSE - levels up by taking hits from enemies; each level is -1% damage taken
 */
public enum Stat {
    SPEED(60F, 30F, 0.01F),      // xp = blocks sprinted
    ATTACK(20F, 10F, 0.02F),     // xp = damage dealt
    DEFENSE(15F, 8F, 0.01F);     // xp = damage taken from enemies

    /** Each stat goes to 333, so Character Level (all three added up) reaches 999. */
    public static final int MAX_LEVEL = 333;
    private static final int FULL = 50;

    /**
     * How much a level actually counts: fully up to 50, then tapering (level 333 counts like ~84), so high levels keep
     * getting stronger without speed/damage running away.
     */
    public static float effective(int level) {
        return Math.min(level, FULL) + 2F * (float) Math.sqrt(Math.max(0, level - FULL));
    }

    private final float base, perLevel;
    public final float bonusPerLevel;

    Stat(float base, float perLevel, float bonusPerLevel) {
        this.base = base;
        this.perLevel = perLevel;
        this.bonusPerLevel = bonusPerLevel;
    }

    /** XP needed to go from this level to the next. */
    public float needed(int level) {
        return base + perLevel * level;
    }

    public String id() {
        return name().toLowerCase();
    }
}
