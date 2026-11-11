package com.xenowarriors.statboost;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.character.PlayerCharacter;
import com.xenowarriors.stats.Stat;
import net.minecraft.server.level.ServerPlayer;

/**
 * Every per-character stat a Stat Boost can raise (the same list as the stats menu): how to read it, its cap, who can
 * use it, and how to add levels to it straight away (no XP, so a boost never counts as a level-up of its own).
 */
public enum BoostStat {
    SPEED("speed", "Speed", 0x55DDFF),
    ATTACK("attack", "Attack", 0xFF7744),
    DEFENSE("defense", "Defense", 0x88DD66),
    MINING("mining", "Mining", 0xE0C070),
    CRAFTING("crafting", "Crafting", 0xC08A50),
    SMELTING("smelting", "Smelting", 0xFF8A3A),
    APPETITE("appetite", "Appetite", 0xE8D020),
    BUILDING("building", "Building", 0x7FD46A),
    HEKUBLADE("hekublade", "Hekublade", 0xFF7A1A),
    WIRES("wires", "Wires", 0xB05CFF),
    GUNS("guns", "Guns", 0xB0B8C8);

    public final String id, label;
    public final int color;

    BoostStat(String id, String label, int color) {
        this.id = id;
        this.label = label;
        this.color = color;
    }

    public static BoostStat of(Stat s) {
        return switch (s) {
            case SPEED -> SPEED;
            case ATTACK -> ATTACK;
            case DEFENSE -> DEFENSE;
        };
    }

    public int max() {
        return switch (this) {
            case APPETITE -> com.xenowarriors.stats.Appetite.MAX_LEVEL;
            case HEKUBLADE -> com.xenowarriors.mikiko.BladeLevel.MAX;
            case WIRES -> com.xenowarriors.rhalix.WireLevel.MAX;
            case GUNS -> com.xenowarriors.compat.GunLevel.MAX;
            default -> Stat.MAX_LEVEL;
        };
    }

    /** Only some characters have this stat: Hekublade is Mikiko's, the wires Rhalix's (Season 2). */
    public boolean usable(ServerPlayer p) {
        CharacterType t = ModCapabilities.getType(p);
        if (t == CharacterType.NONE) return false;
        return switch (this) {
            case HEKUBLADE -> t == CharacterType.MIKIKO;
            case WIRES -> com.xenowarriors.forms.WeaponForms.style(p) == com.xenowarriors.forms.Form.Style.WIRE;
            default -> true;
        };
    }

    /** Why {@link #usable} is false, for the message. */
    public String whoCanUse() {
        return switch (this) {
            case HEKUBLADE -> "Only Mikiko has a Hekublade level.";
            case WIRES -> "Only Rhalix (Season 2) has a wires level.";
            default -> "Pick a character first.";
        };
    }

    /** Season 2 stats stay out of the creative tab until Season 2 is unlocked. */
    public int season() { return this == WIRES ? 2 : 1; }

    private static PlayerCharacter cap(ServerPlayer p) {
        return p.getCapability(ModCapabilities.PLAYER_CHARACTER).resolve().orElse(null);
    }

    /** This character's level in the stat. */
    public int level(ServerPlayer p) {
        PlayerCharacter d = cap(p);
        return switch (this) {
            case SPEED, ATTACK, DEFENSE -> d == null ? 0 : d.statLevel[ordinal()];
            case MINING -> d == null ? 0 : d.miningLevel;
            case CRAFTING -> d == null ? 0 : d.craftLevel;
            case SMELTING -> d == null ? 0 : d.smeltLevel;
            case APPETITE -> com.xenowarriors.stats.Appetite.level(p);
            case BUILDING -> com.xenowarriors.stats.Building.level(p);
            case HEKUBLADE -> d == null ? 1 : d.bladeLevel;
            case WIRES -> com.xenowarriors.rhalix.WireLevel.level(p);
            case GUNS -> com.xenowarriors.compat.GunLevel.level(p);
        };
    }

    /**
     * Adds up to {@code n} levels (capped at the max), keeping the XP toward the next level where the stat has it, and
     * applies/syncs what the level changes. Returns how many levels were really added.
     */
    public int add(ServerPlayer p, int n) {
        int before = level(p);
        int after = (int) Math.min((long) max(), (long) before + Math.max(0, n));
        int gained = after - before;
        if (gained <= 0) return 0;
        PlayerCharacter d = cap(p);
        switch (this) {
            case SPEED, ATTACK, DEFENSE -> {
                if (d == null) return 0;
                d.statLevel[ordinal()] = after;
                if (after >= Stat.MAX_LEVEL) d.statXp[ordinal()] = 0;
                com.xenowarriors.stats.Stats.reapply(p);
                com.xenowarriors.stats.Stats.syncAll(p);
            }
            case MINING -> {
                if (d == null) return 0;
                d.miningLevel = after;
                if (after >= max()) d.miningXp = 0;
                com.xenowarriors.stats.Mining.sync(p);
                com.xenowarriors.stats.HandMining.leveled(p, before, after);
            }
            case CRAFTING -> {
                if (d == null) return 0;
                d.craftLevel = after;
                if (after >= max()) d.craftXp = 0;
            }
            case SMELTING -> {
                if (d == null) return 0;
                d.smeltLevel = after;
                if (after >= max()) d.smeltXp = 0;
            }
            case APPETITE -> com.xenowarriors.stats.Appetite.set(p, after, after >= max() ? 0F : com.xenowarriors.stats.Appetite.xp(p));
            case BUILDING -> com.xenowarriors.stats.Building.set(p, after, after >= max() ? 0F : com.xenowarriors.stats.Building.xp(p));
            case HEKUBLADE -> {
                if (d == null) return 0;
                d.bladeLevel = after;
                if (after >= max()) d.bladeXp = 0;
                com.xenowarriors.forms.WeaponForms.announceUnlocks(p, before, after);
            }
            case WIRES -> {
                com.xenowarriors.rhalix.WireLevel.addLevels(p, gained);
                com.xenowarriors.forms.WeaponForms.announceUnlocks(p, before, after);
            }
            case GUNS -> com.xenowarriors.compat.GunLevel.addLevels(p, gained);
        }
        return level(p) - before;
    }
}
