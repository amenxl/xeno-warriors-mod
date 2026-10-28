package com.xenowarriors.client;

import com.xenowarriors.character.CharacterType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Technica's goggles: they come down over her eyes while she senses Karma (and stay a moment after), and their built-in
 * scanner shows the Karma Level of whoever she looks at.
 */
public final class TechGoggles {
    private static final Map<Integer, Long> LAST = new HashMap<>();

    private TechGoggles() {}

    /** Her goggles are down (sensing now, or within the last 2 seconds). */
    public static boolean down(Player p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ClientCharacterData.get(p) != CharacterType.TECHNICA) return false;
        long now = mc.level.getGameTime();
        if (KarmaFocusClient.focusing(p)) { LAST.put(p.getId(), now); return true; }
        Long t = LAST.get(p.getId());
        return t != null && now - t < 40;
    }
}
