package com.xenowarriors.compat.epicfight;

import net.minecraft.world.entity.player.Player;

/** Client only: which character a (client-side) player is. */
final class EfClientCheck {
    private EfClientCheck() {}

    static Boolean isRhalix(Player p) {
        return p instanceof net.minecraft.client.player.AbstractClientPlayer cp
                && com.xenowarriors.client.ClientCharacterData.get(cp) == com.xenowarriors.character.CharacterType.RHALIX;
    }
}
