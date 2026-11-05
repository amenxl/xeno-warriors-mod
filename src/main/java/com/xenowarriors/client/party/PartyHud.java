package com.xenowarriors.client.party;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.network.PartySyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Classic HUD: your party members down the left side (name, health bar and Karma bar - and with DragonMineZ a ki
 * bar - each with its numbers), and
 * your own health in numbers beside the hearts.
 */
public class PartyHud implements IGuiOverlay {
    private static int shownTop = -1;

    /** Where the party frames on the left start this frame (-1 if not shown) - the popup feed stays above them. */
    public static int shownTop() { return shownTop; }

    /** "cur/max", rounded. */
    public static String nums(float cur, float max) { return Math.round(cur) + "/" + Math.round(max); }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        shownTop = -1;
        if (mc.options.hideGui || mc.player == null) return;
        if (com.xenowarriors.client.KhHud.active()) return;      // the Kingdom Hearts / Blazing Wind HUDs cover this
        Font font = mc.font;
        // your own health in numbers, just left of the hearts (the Karma bar over the skills has its numbers already)
        if (mc.gameMode != null && mc.gameMode.canHurtPlayer()) {
            String own = nums(mc.player.getHealth(), mc.player.getMaxHealth());
            com.xenowarriors.client.KhHud.small(g, font, own, width / 2F - 93 - font.width(own) * 0.75F, height - 38.5F, 0xFFFF7070, 0.75F);
        }
        if (PartyClient.members().size() < 2) return;
        // drawn at 70% size so it doesn't take up much of the screen
        float sc = 0.7F;
        g.pose().pushPose();
        g.pose().scale(sc, sc, 1F);
        // left side, kept above the chat area (bottom ~150 px) and under the top-left corner other mods use
        int members = Math.max(0, PartyClient.members().size() - 1);
        boolean ki = PartyKiClient.any();                                    // (DragonMineZ: a ki bar under the Karma bar)
        int rowH = ki ? 36 : 30, frameB = ki ? 29 : 23;
        int top = (int) Mth.clamp(height / 2F - 34, 40, Math.max(40, height - 150 - members * rowH * sc));
        int x = Math.round(5 / sc), y = Math.round(top / sc);
        shownTop = top - 2;
        for (PartySyncPacket.Member m : PartyClient.members()) {
            if (m.id().equals(mc.player.getUUID())) continue;
            CharacterType type = CharacterType.values()[Mth.clamp(m.type(), 0, CharacterType.values().length - 1)];
            int color = type == CharacterType.NONE ? 0xFFFFFFFF : type.getColor() | 0xFF000000;
            int w = 100, bw = w - 3 - 36;                                   // (the bars, their numbers to the right)
            g.fill(x - 2, y - 2, x + w + 2, y + frameB, 0x80101018);
            g.fill(x - 2, y - 2, x, y + frameB, color);
            String name = (m.leader() ? "★ " : "") + m.name() + (m.knockedOut() ? "  KO" : "");
            if (font.width(name) > w - 3) name = font.plainSubstrByWidth(name, Math.max(0, w - 3 - font.width("…"))).stripTrailing() + "…";
            g.drawString(font, name, x + 3, y, m.knockedOut() ? 0xFF6A5A : 0xFFFFFF);
            float hp = m.maxHealth() <= 0 ? 0 : Mth.clamp(m.health() / m.maxHealth(), 0F, 1F);
            g.fill(x + 3, y + 11, x + 3 + bw, y + 15, 0xFF3A1414);
            g.fill(x + 3, y + 11, x + 3 + Math.round(bw * hp), y + 15, hp > 0.5F ? 0xFFE04040 : hp > 0.25F ? 0xFFE08A30 : 0xFFFF3030);
            com.xenowarriors.client.KhHud.small(g, font, nums(m.health(), m.maxHealth()), x + 6 + bw, y + 10, 0xFFFFB8B8, 0.7F);
            float k = m.maxKarma() <= 0 ? 0 : Mth.clamp(m.karma() / m.maxKarma(), 0F, 1F);
            g.fill(x + 3, y + 18, x + 3 + bw, y + 21, 0xFF141A3A);
            g.fill(x + 3, y + 18, x + 3 + Math.round(bw * k), y + 21, 0xFF6A8CFF);
            com.xenowarriors.client.KhHud.small(g, font, nums(m.karma(), m.maxKarma()), x + 6 + bw, y + 17, 0xFFB8C8FF, 0.7F);
            float[] kk = ki ? PartyKiClient.of(m.id()) : null;
            if (kk != null) {
                g.fill(x + 3, y + 24, x + 3 + bw, y + 27, PartyKiClient.KI_BACK);
                g.fill(x + 3, y + 24, x + 3 + Math.round(bw * PartyKiClient.frac(kk)), y + 27, PartyKiClient.KI_COLOR);
                com.xenowarriors.client.KhHud.small(g, font, nums(kk[0], kk[1]), x + 6 + bw, y + 23, PartyKiClient.KI_TEXT, 0.7F);
            }
            y += rowH;
        }
        g.pose().popPose();
    }
}
