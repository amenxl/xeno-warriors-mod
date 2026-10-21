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
    /** Mikiko's foxes: one compound per fox {uuid, name, level, xp, variant, gen, dead}. */
    public net.minecraft.nbt.ListTag pets = new net.minecraft.nbt.ListTag();
    /** Rhalix's winged companions: {uuid, name, type, gen, dead, data}. */
    public net.minecraft.nbt.ListTag wingPets = new net.minecraft.nbt.ListTag();
    /** Rhalix: are his wings out (true) or put away (false)? */
    public boolean wingsOut = true;
    /** How torn their clothes are from explosions (0-3); mends when healed up and resting. */
    public int torn;
    /** The fox riding on his back (saved entity data), or empty. */
    public CompoundTag backFox = new CompoundTag();
    /** Total ticks Mikiko has spent flying (his flying gets better with practice). */
    public int flightTicks;
    /** How many times they've used Karma Sensing (range and duration grow with practice). */
    public int senseUses;
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
        this.senseUses = other.senseUses;
        this.pets = other.pets.copy();
        this.wingPets = other.wingPets.copy();
        this.backFox = other.backFox.copy();
        this.wingsOut = other.wingsOut;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("character", type.getId());
        tag.putFloat("karma", karma);
        tag.putIntArray("stat_levels", statLevel.clone());
        tag.putInt("flight_ticks", flightTicks);
        tag.putInt("sense_uses", senseUses);
        tag.put("pets", pets.copy());
        tag.put("wing_pets", wingPets.copy());
        tag.put("back_fox", backFox.copy());
        tag.putBoolean("wings_in", !wingsOut);
        tag.putInt("torn", torn);
        for (int i = 0; i < 3; i++) tag.putFloat("stat_xp_" + i, statXp[i]);
        return tag;
    }

    public void load(CompoundTag tag) {
        this.type = CharacterType.byId(tag.getString("character"));
        this.flightTicks = tag.getInt("flight_ticks");
        this.senseUses = tag.getInt("sense_uses");
        this.pets = tag.getList("pets", net.minecraft.nbt.Tag.TAG_COMPOUND);
        this.wingPets = tag.getList("wing_pets", net.minecraft.nbt.Tag.TAG_COMPOUND);
        this.backFox = tag.getCompound("back_fox");
        this.wingsOut = !tag.getBoolean("wings_in");
        this.torn = tag.getInt("torn");
        int[] lv = tag.getIntArray("stat_levels");
        for (int i = 0; i < 3; i++) {
            statLevel[i] = i < lv.length ? lv[i] : 0;
            statXp[i] = tag.getFloat("stat_xp_" + i);
        }
        this.karma = tag.contains("karma") ? Math.max(0F, Math.min(Karma.MAX, tag.getFloat("karma"))) : Karma.MAX;
    }
}
