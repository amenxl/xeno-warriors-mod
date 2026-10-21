package com.xenowarriors.character;

import net.minecraft.world.entity.player.Player;

/** Added to every Player by {@link com.xenowarriors.mixin.PlayerMixin}. */
public interface CharacterHolder {
    PlayerCharacter xenowarriors$getCharacter();

    static PlayerCharacter get(Player player) {
        return ((CharacterHolder) player).xenowarriors$getCharacter();
    }

    static CharacterType getType(Player player) {
        return get(player).getType();
    }
}
