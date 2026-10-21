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
    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || PartyClient.members().isEmpty()) return;
        Font font = mc.font;
        int x = 6, y = height / 2 - 40;
        for (PartySyncPacket.Member m : PartyClient.members()) {
            if (m.id().equals(mc.player.getUUID())) continue;
            CharacterType type = CharacterType.values()[Mth.clamp(m.type(), 0, CharacterType.values().length - 1)];
            int color = type == CharacterType.NONE ? 0xFFFFFFFF : type.getColor() | 0xFF000000;
            int w = 96;
            g.fill(x - 2, y - 2, x + w + 2, y + 25, 0x90101018);
            g.fill(x - 2, y - 2, x, y + 25, color);
            String name = (m.leader() ? "★ " : "") + m.name() + (m.knockedOut() ? "  KO" : "");
            g.drawString(font, name, x + 3, y, m.knockedOut() ? 0xFF6A5A : 0xFFFFFF);
            // health
            float hp = m.maxHealth() <= 0 ? 0 : Mth.clamp(m.health() / m.maxHealth(), 0F, 1F);
            g.fill(x + 3, y + 11, x + w, y + 16, 0xFF3A1414);
            g.fill(x + 3, y + 11, x + 3 + Math.round((w - 3) * hp), y + 16, hp > 0.5F ? 0xFFE04040 : hp > 0.25F ? 0xFFE08A30 : 0xFFFF3030);
            String hpText = Math.round(m.health()) + "/" + Math.round(m.maxHealth());
            g.drawString(font, hpText, x + w - font.width(hpText) - 1, y + 10, 0xFFFFFF, true);
            // karma
            float k = m.maxKarma() <= 0 ? 0 : Mth.clamp(m.karma() / m.maxKarma(), 0F, 1F);
            g.fill(x + 3, y + 19, x + w, y + 22, 0xFF141A3A);
            g.fill(x + 3, y + 19, x + 3 + Math.round((w - 3) * k), y + 22, 0xFF6A8CFF);
            y += 31;
        }
    }
}
