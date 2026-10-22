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

    /** Every stat and skill goes to 30,000; with Speed, Attack and Defense all maxed you're Character Level 88,888. */
    public static final int MAX_LEVEL = 30000;
    private static final int FULL = 50;

    /**
     * How much a level actually counts: fully up to 50, then tapering (level 333 counts like ~84), so high levels keep
     * getting stronger without speed/damage running away.
     */
    public static float effective(int level) {
        float e = Math.min(level, FULL) + 2F * (float) Math.sqrt(Math.max(0, Math.min(level, 333) - FULL));
        if (level > 333) e += 25F * (float) Math.log(1 + (level - 333) / 333.0);   // slow, steady growth to the top
        return e;
    }

    /** The bonus this stat gives at a level (speed and damage fractions, damage-taken reduction), capped. */
    public float bonus(int level) {
        float b = effective(level) * bonusPerLevel;
        return switch (this) {
            case SPEED -> Math.min(1.5F, b);       // up to +150% speed
            case ATTACK -> Math.min(5F, b);        // up to +500% damage
            case DEFENSE -> Math.min(0.75F, b);    // up to 75% less damage taken
        };
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
