package com.xenowarriors.character;

import com.xenowarriors.karma.Karma;
import net.minecraft.nbt.CompoundTag;

/** Per-player data: which character they picked, their Karma, and skill cooldowns. */
public class PlayerCharacter {
    private CharacterType type = CharacterType.NONE;
    /** Game time when each skill slot's cooldown ends (not saved; resets on relog). */
    public final long[] cooldownEnd = new long[5];
    /** Karma - the energy every skill runs on. Starts full. */
    private float karma = Karma.MAX;
    /** Level-up stats (see stats/Stats): level and progress toward the next level for each. */
    public final int[] statLevel = new int[3];
    public final float[] statXp = new float[3];
    /** Total ticks Mikiko has spent flying (his flying gets better with practice). */
    public int flightTicks;
    /** Last Karma value sent to the client (-1 = never sent). Not saved. */
    public float lastSyncedKarma = -1F;

    public CharacterType getType() { return type; }
    public void setType(CharacterType type) { this.type = type; }

    public float getKarma() { return karma; }
    public void setKarma(float value) { this.karma = Math.max(0F, Math.min(Karma.MAX, value)); }

    /** Keeps the character after death; Karma comes back full on respawn. */
    public void copyFrom(PlayerCharacter other) {
        this.type = other.type;
        System.arraycopy(other.statLevel, 0, statLevel, 0, 3);
        System.arraycopy(other.statXp, 0, statXp, 0, 3);
        this.flightTicks = other.flightTicks;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("character", type.getId());
        tag.putFloat("karma", karma);
        tag.putIntArray("stat_levels", statLevel.clone());
        tag.putInt("flight_ticks", flightTicks);
        for (int i = 0; i < 3; i++) tag.putFloat("stat_xp_" + i, statXp[i]);
        return tag;
    }

    public void load(CompoundTag tag) {
        this.type = CharacterType.byId(tag.getString("character"));
        this.flightTicks = tag.getInt("flight_ticks");
        int[] lv = tag.getIntArray("stat_levels");
        for (int i = 0; i < 3; i++) {
            statLevel[i] = i < lv.length ? lv[i] : 0;
            statXp[i] = tag.getFloat("stat_xp_" + i);
        }
        this.karma = tag.contains("karma") ? Math.max(0F, Math.min(Karma.MAX, tag.getFloat("karma"))) : Karma.MAX;
    }
}
