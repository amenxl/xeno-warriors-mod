package com.xenowarriors.character;

import net.minecraft.nbt.CompoundTag;

/** Per-player data: which character they picked. Skills/Karma data will go here later. */
public class PlayerCharacter {
    private CharacterType type = CharacterType.NONE;
    /** Game time when each skill slot's cooldown ends (not saved; resets on relog). */
    public final long[] cooldownEnd = new long[5];

    public CharacterType getType() { return type; }
    public void setType(CharacterType type) { this.type = type; }

    public void copyFrom(PlayerCharacter other) { this.type = other.type; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("character", type.getId());
        return tag;
    }

    public void load(CompoundTag tag) {
        this.type = CharacterType.byId(tag.getString("character"));
    }
}
