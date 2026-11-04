package com.xenowarriors.client;

import com.xenowarriors.character.CharacterType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * Ultima sees what's invisible: invisible players and mobs are drawn see-through for him (the way spectators see them)
 * and his lock-on can pick them. (UltimaSightMixin, LockOn)
 */
public final class UltimaSight {
    private UltimaSight() {}

    /** Whether this viewer is the local player playing Ultima. */
    public static boolean sees(Player viewer) {
        Minecraft mc = Minecraft.getInstance();
        return viewer != null && viewer == mc.player && ClientCharacterData.getLocal() == CharacterType.ULTIMA;
    }

    public static boolean local() {
        return sees(Minecraft.getInstance().player);
    }
}
