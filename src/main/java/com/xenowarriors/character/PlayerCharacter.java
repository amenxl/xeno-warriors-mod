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
    /** Last Karma value sent to the client (-1 = never sent). Not saved. */
    public float lastSyncedKarma = -1F;

    public CharacterType getType() { return type; }
    public void setType(CharacterType type) { this.type = type; }

    public float getKarma() { return karma; }
    public void setKarma(float value) { this.karma = Math.max(0F, Math.min(Karma.MAX, value)); }

    /** Keeps the character after death; Karma comes back full on respawn. */
    public void copyFrom(PlayerCharacter other) { this.type = other.type; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("character", type.getId());
        tag.putFloat("karma", karma);
        return tag;
    }

    public void load(CompoundTag tag) {
        this.type = CharacterType.byId(tag.getString("character"));
        this.karma = tag.contains("karma") ? Math.max(0F, Math.min(Karma.MAX, tag.getFloat("karma"))) : Karma.MAX;
    }
}
