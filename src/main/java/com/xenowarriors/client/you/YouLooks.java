package com.xenowarriors.client.you;

import com.xenowarriors.client.ClientCharacterData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

/**
 * Which skin a player playing one of their own characters is drawn in (and a bot of one: see BotAvatar): the
 * character's own skin once it has loaded, else - for a bot - its owner's skin, else (null) the player's own.
 */
public final class YouLooks {
    private YouLooks() {}

    /** The creator's preview: the skin being picked (null = the slot's own). */
    public static String previewSkin;

    public static YouSkinClient.Look of(AbstractClientPlayer p) {
        var s = ClientCharacterData.you(p);
        String skin = previewSkin != null && ClientCharacterData.isPreviewing() && p == Minecraft.getInstance().player ? previewSkin : s == null ? "" : s.skin;
        YouSkinClient.Look l = YouSkinClient.look(skin);
        if (l != null) return l;
        if (p instanceof com.xenowarriors.client.bot.BotAvatar a && a.bot() != null && a.bot().ownerId() != null) {   // a bot in "your skin": its owner's
            var conn = Minecraft.getInstance().getConnection();
            var info = conn == null ? null : conn.getPlayerInfo(a.bot().ownerId());
            if (info != null) return new YouSkinClient.Look(info.getSkinLocation(), "slim".equals(info.getModelName()));
        }
        return null;
    }
}
