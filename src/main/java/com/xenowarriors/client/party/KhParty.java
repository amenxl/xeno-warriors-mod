package com.xenowarriors.client.party;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.HudFaces;
import com.xenowarriors.client.KhHud;
import com.xenowarriors.network.PartySyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** Party cards top right, Kingdom Hearts style: each member's portrait (with their faces), name, character, distance, HP and Karma. */
public class KhParty implements IGuiOverlay {
    public static final int CARD_W = 122, CARD_H = 28;
    private static int height;

    /** How far down the top-right corner the party cards reach (other things stack under them). */
    public static int bottom() { return height; }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partial, int width, int screenH) {
        Minecraft mc = Minecraft.getInstance();
        height = 0;
        if (!KhHud.active() || mc.options.hideGui || PartyClient.members().isEmpty()) return;
        int x = width - CARD_W - 4, y = 4;
        var font = mc.font;
        g.drawString(font, "PARTY", x + 2, y, 0xC8E1FF, true);
        y += 10;
        for (PartySyncPacket.Member m : PartyClient.members()) {
            if (m.id().equals(mc.player.getUUID())) continue;
            CharacterType type = CharacterType.values()[Math.max(0, Math.min(CharacterType.values().length - 1, m.type()))];
            int col = type.getColor();
            g.fill(x, y, x + CARD_W, y + CARD_H, 0xFF0A0A12);
            g.fill(x + 1, y + 1, x + CARD_W - 1, y + CARD_H - 1, col);
            g.fill(x + 2, y + 2, x + CARD_W - 2, y + CARD_H - 2, 0xF0121422);
            HudFaces.health(m.id(), m.health(), m.maxHealth());
            boolean low = m.maxHealth() > 0 && m.health() / m.maxHealth() < 0.3F;
            if (type != CharacterType.NONE) {
                String face = HudFaces.face(m.id(), m.health(), m.maxHealth(), false, m.knockedOut());
                KhHud.portrait(g, HudFaces.texture(type, face), x + 1, y + 1, 26, low && (HudFaces.clock() / 6) % 2 == 0);
            }
            KhHud.small(g, font, m.name(), x + 30, y + 4, 0xFFFFFF, 0.65F);
            String info;
            if (m.knockedOut()) info = "DOWN";
            else {
                double dist = mc.player.level().dimension().location().toString().equals(m.dim())
                        ? Math.sqrt(mc.player.distanceToSqr(m.x(), m.y(), m.z())) : -1;
                info = type.getDisplayName().getString() + (dist >= 0 ? "  " + Math.round(dist) + "m" : "  far");
            }
            KhHud.small(g, font, info, x + CARD_W - 4 - font.width(info) * 0.55F, y + 4.5F, m.knockedOut() ? 0xFF5A5A : 0xBEBED2, 0.55F);
            KhHud.bar(g, x + 30, y + 12, CARD_W - 40, 5, m.maxHealth() > 0 ? m.health() / m.maxHealth() : 0, low ? 0xFFD03A30 : 0xFF46D75A, 0);
            KhHud.bar(g, x + 34, y + 20, CARD_W - 44, 3, m.maxKarma() > 0 ? m.karma() / m.maxKarma() : 0, 0xFF5A96FF, 0);
            if (m.knockedOut()) g.fill(x + 2, y + 2, x + CARD_W - 2, y + CARD_H - 2, 0x55800000);
            // a short chat line from them
            String b = HudFaces.bubble(m.id());
            if (b != null) {
                int bw = Math.round(font.width(b) * 0.6F) + 6;
                g.fill(x - bw - 3, y + 6, x - 3, y + 16, 0xF0FAFAFF);
                KhHud.small(g, font, b, x - bw, y + 8, 0x14141E, 0.6F);
            }
            y += CARD_H + 3;
        }
        height = y;
    }
}
