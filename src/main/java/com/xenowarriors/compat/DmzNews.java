package com.xenowarriors.compat;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.network.PopupPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * DragonMineZ news that used to flood the chat (a bot trained up, learned or made a ki attack, started / finished /
 * talked its way through a mission, a mission's target or an NPC came to you...) goes to the pop-up feed as small,
 * see-through cards instead. The player can send it back to chat (Settings, "Bot / mission news"); that choice is
 * the client's, so the line travels as a pop-up either way and the client decides where it shows.
 */
public final class DmzNews {
    /** (tests) news lines sent since start. */
    public static int sent;
    public static String last = "";

    private DmzNews() {}

    /** News about a bot (its portrait shows on the card). */
    public static void bot(Player to, PartyBotEntity bot, String title, Component line) {
        send(to, bot == null ? to.getUUID() : bot.getUUID(), bot == null ? 0 : bot.character().ordinal(), title, line);
    }

    /** News about the player themselves. */
    public static void self(Player to, String title, Component line) {
        int type = to instanceof ServerPlayer sp ? com.xenowarriors.character.ModCapabilities.getType(sp).ordinal() : 0;
        send(to, to.getUUID(), type, title, line);
    }

    private static void send(Player to, java.util.UUID subject, int type, String title, Component line) {
        if (!(to instanceof ServerPlayer sp)) return;
        String text = line.getString();
        if (text.length() > 158) text = text.substring(0, 157) + "…";
        ChatFormatting color = line.getStyle().getColor() == null ? ChatFormatting.AQUA
                : ChatFormatting.getByName(line.getStyle().getColor().serialize());
        sent++;
        last = title + ": " + text;
        PopupPacket.send(sp, PopupPacket.NEWS, subject, type, -1, title, text, color == null ? "aqua" : color.getName());
    }
}
