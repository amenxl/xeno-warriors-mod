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

    private static int blazingTop;
    /** Blazing Wind: the top of the party cards above your gauge (0 = none shown) - top-right cards keep above it. */
    public static int blazingTop() { return blazingTop; }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partial, int width, int screenH) {
        Minecraft mc = Minecraft.getInstance();
        height = 0;
        blazingTop = 0;
        if (!KhHud.active() || mc.options.hideGui || PartyClient.members().isEmpty()) return;
        if (com.xenowarriors.client.ClientSettings.blazingHud()) {
            com.xenowarriors.client.HudFade.begin();
            try { blazing(g, mc, width, screenH); } finally { com.xenowarriors.client.HudFade.end(); }
            return;
        }
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
                boolean flash = low && (HudFaces.clock() / 6) % 2 == 0;
                if (HudFaces.hasArt(type)) KhHud.portrait(g, HudFaces.texture(type, face), x + 1, y + 1, 26, flash);
                else com.xenowarriors.client.HudHead.draw(g, com.xenowarriors.client.HudHead.find(m.id()), type, x + 1, y + 1, 26, flash);
            }
            KhHud.small(g, font, m.name(), x + 30, y + 4, 0xFFFFFF, 0.65F);
            if (m.bot()) {                                   // bot teammates get a little BOT tag on their portrait
                g.fill(x + 2, y + CARD_H - 9, x + 20, y + CARD_H - 2, 0xE0303A50);
                KhHud.small(g, font, "BOT", x + 4, y + CARD_H - 8, 0x9FE0FF, 0.5F);
            }
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

    private static final net.minecraft.resources.ResourceLocation CARD = com.xenowarriors.XenoWarriors.id("textures/gui/hud/blazing_party.png");

    /** Blazing Wind: party cards in a row just above your own gauge (name, LV, portrait, HP, KP and EXP). */
    private static void blazing(GuiGraphics g, Minecraft mc, int width, int screenH) {
        var font = mc.font;
        float s = 0.8F;
        int cw = Math.round(112 * s), ch = Math.round(78 * s) + 9;
        int right = width - 4, bottom = com.xenowarriors.client.BlazingHud.top(screenH) - 16;
        int x = right - cw, y = bottom - ch;
        for (PartySyncPacket.Member m : PartyClient.members()) {
            if (m.id().equals(mc.player.getUUID())) continue;
            if (x < width / 2 + 8) { x = right - cw; y -= ch + 2; }            // (a second row only when the right half is full - stacking up early ran it into the top-right cards)
            CharacterType type = CharacterType.values()[Math.max(0, Math.min(CharacterType.values().length - 1, m.type()))];
            g.pose().pushPose();
            g.pose().translate(x, y + 9, 0);
            g.pose().scale(s, s, 1F);
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            g.blit(CARD, 0, 0, 112, 78, 0, 0, 112, 78, 112, 78);
            HudFaces.health(m.id(), m.health(), m.maxHealth());
            boolean low = m.maxHealth() > 0 && m.health() / m.maxHealth() < 0.3F, flash = low && (HudFaces.clock() / 6) % 2 == 0;
            if (type != CharacterType.NONE) {
                String face = HudFaces.face(m.id(), m.health(), m.maxHealth(), false, m.knockedOut());
                if (HudFaces.hasArt(type)) KhHud.portrait(g, HudFaces.texture(type, face), 3, 24, 40, flash);
                else com.xenowarriors.client.HudHead.draw(g, com.xenowarriors.client.HudHead.find(m.id()), type, 5, 26, 36, flash);
            }
            KhHud.small(g, font, String.valueOf(Math.max(1, m.level())), 27, 11, 0xFFFFFF, 1F);
            String hp = String.valueOf(Math.round(m.health()));
            KhHud.small(g, font, hp, 99 - font.width(hp) * 0.85F, 22, 0xFFFFFF, 0.85F);
            float hf = m.maxHealth() > 0 ? m.health() / m.maxHealth() : 0;
            g.fill(51, 33, 51 + Math.round(47 * net.minecraft.util.Mth.clamp(hf, 0, 1)), 37, low ? 0xFFE04040 : 0xFF40D8B0);
            if (type == CharacterType.ZANE || type == CharacterType.ULTIMA)
                g.blit(com.xenowarriors.XenoWarriors.id("textures/gui/hud/blazing_" + (type == CharacterType.ZANE ? "mp" : "dp") + ".png"), 45, 40, 22, 22, 0, 0, 64, 64, 64, 64);
            String kp = String.valueOf(Math.round(m.karma()));
            KhHud.small(g, font, kp, 99 - font.width(kp) * 0.85F, 44, 0xFFFFFF, 0.85F);
            float kf = m.maxKarma() > 0 ? m.karma() / m.maxKarma() : 0;
            g.fill(51, 56, 51 + Math.round(47 * net.minecraft.util.Mth.clamp(kf, 0, 1)), 60, 0xFF60C850);
            g.fill(30, 71, 30 + Math.round(67 * net.minecraft.util.Mth.clamp(m.xp(), 0, 1)), 73, 0xFFE8E8F0);
            if (m.bot()) KhHud.small(g, font, "BOT", 6, 60, 0x9FE0FF, 0.7F);
            if (m.knockedOut()) { g.fill(2, 2, 110, 76, 0x66800000); KhHud.small(g, font, "DOWN", 40, 64, 0xFF5A5A, 0.9F); }
            g.pose().popPose();
            // name above, and how far away they are
            String nm = m.name();
            KhHud.small(g, font, nm, x + 2, y, 0xFFFFFF, 0.75F);
            blazingTop = blazingTop == 0 ? y - 2 : Math.min(blazingTop, y - 2);
            double dist = mc.player.level().dimension().location().toString().equals(m.dim()) ? Math.sqrt(mc.player.distanceToSqr(m.x(), m.y(), m.z())) : -1;
            String d = dist >= 0 ? Math.round(dist) + "m" : "far";
            KhHud.small(g, font, d, x + cw - 2 - font.width(d) * 0.6F, y + 1, 0xBEBED2, 0.6F);
            String b = HudFaces.bubble(m.id());
            if (b != null) {
                int bw = Math.round(font.width(b) * 0.6F) + 6;
                g.fill(x, y - 12, x + bw, y - 2, 0xF0FAFAFF);
                KhHud.small(g, font, b, x + 3, y - 10, 0x14141E, 0.6F);
            }
            x -= cw + 4;
        }
    }
}
