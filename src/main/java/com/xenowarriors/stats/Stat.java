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

    public static final int MAX_LEVEL = 50;

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
