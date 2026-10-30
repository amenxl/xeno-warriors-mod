package com.xenowarriors.client.party;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.network.PartySyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** Your party members down the left side: name, health bar and Karma bar. */
public class PartyHud implements IGuiOverlay {
    private static int shownTop = -1;

    /** Where the party frames on the left start this frame (-1 if not shown) - the popup feed stays above them. */
    public static int shownTop() { return shownTop; }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        shownTop = -1;
        if (mc.options.hideGui || mc.player == null || PartyClient.members().size() < 2) return;
        if (com.xenowarriors.client.KhHud.active()) return;      // the Kingdom Hearts HUD covers this
        Font font = mc.font;
        // drawn at 70% size so it doesn't take up much of the screen
        float sc = 0.7F;
        g.pose().pushPose();
        g.pose().scale(sc, sc, 1F);
        // left side, kept above the chat area (bottom ~150 px) and under the top-left corner other mods use
        int members = Math.max(0, PartyClient.members().size() - 1);
        int top = (int) Mth.clamp(height / 2F - 34, 40, Math.max(40, height - 150 - members * 28 * sc));
        int x = Math.round(5 / sc), y = Math.round(top / sc);
        shownTop = top - 2;
        for (PartySyncPacket.Member m : PartyClient.members()) {
            if (m.id().equals(mc.player.getUUID())) continue;
            CharacterType type = CharacterType.values()[Mth.clamp(m.type(), 0, CharacterType.values().length - 1)];
            int color = type == CharacterType.NONE ? 0xFFFFFFFF : type.getColor() | 0xFF000000;
            int w = 80;
            g.fill(x - 2, y - 2, x + w + 2, y + 21, 0x80101018);
            g.fill(x - 2, y - 2, x, y + 21, color);
            String name = (m.leader() ? "\u2605 " : "") + m.name() + (m.knockedOut() ? "  KO" : "");
            String hpFirst = Math.round(m.health()) + "/" + Math.round(m.maxHealth());
            int room = w - 3 - font.width(hpFirst) - 4;                     // the name stops before the HP numbers
            if (font.width(name) > room) name = font.plainSubstrByWidth(name, Math.max(0, room - font.width("\u2026"))).stripTrailing() + "\u2026";
            g.drawString(font, name, x + 3, y, m.knockedOut() ? 0xFF6A5A : 0xFFFFFF);
            float hp = m.maxHealth() <= 0 ? 0 : Mth.clamp(m.health() / m.maxHealth(), 0F, 1F);
            g.fill(x + 3, y + 10, x + w, y + 14, 0xFF3A1414);
            g.fill(x + 3, y + 10, x + 3 + Math.round((w - 3) * hp), y + 14, hp > 0.5F ? 0xFFE04040 : hp > 0.25F ? 0xFFE08A30 : 0xFFFF3030);
            String hpText = Math.round(m.health()) + "/" + Math.round(m.maxHealth());
            g.drawString(font, hpText, x + w - font.width(hpText) - 1, y, 0xFFB0B0B0, true);
            float k = m.maxKarma() <= 0 ? 0 : Mth.clamp(m.karma() / m.maxKarma(), 0F, 1F);
            g.fill(x + 3, y + 16, x + w, y + 19, 0xFF141A3A);
            g.fill(x + 3, y + 16, x + 3 + Math.round((w - 3) * k), y + 19, 0xFF6A8CFF);
            y += 26;
        }
        g.pose().popPose();
    }
}
