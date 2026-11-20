package com.xenowarriors.client.you;

import com.xenowarriors.client.ClientCharacterData;
import com.xenowarriors.you.YouSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** Which made character's tail / wings to draw on someone: theirs, or - in the tail and wings screen's preview - the one being styled. */
public final class YouFeatures {
    private YouFeatures() {}

    /** The tail and wings screen's preview (null: none). */
    public static YouSlot preview;

    public static YouSlot of(Player p) {
        if (preview != null && ClientCharacterData.isPreviewing() && p == Minecraft.getInstance().player) return preview;
        return ClientCharacterData.you(p);
    }

    /** In the preview right now. */
    public static boolean previewing(Player p) { return preview != null && ClientCharacterData.isPreviewing() && p == Minecraft.getInstance().player; }
}
