package com.xenowarriors.client.journey;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.journey.JourneyRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * On a journey: the difficulty and step in the top-left with the whole party's faces and health under it (like the
 * path screen), your Journey Coins, and in a battle your score, the challenge and the clock.
 */
public class JourneyHud implements IGuiOverlay {
    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || !JourneyClient.active() || JourneyClient.phase() == 0) return;
        Font font = mc.font;
        var st = JourneyClient.state;
        int diff = Math.max(0, Math.min(4, st.getInt("diff"))), cur = st.getInt("cur"), phase = JourneyClient.phase();
        JourneyScreen.sc(g, font, JourneyRules.DIFF[diff], 6, 4, 1.6F, JourneyRules.DIFF_COLOR[diff]);
        JourneyScreen.sc(g, font, String.format("%02d/%02d", cur, JourneyRules.STEPS), 8 + font.width(JourneyRules.DIFF[diff]) * 1.6F, 9, 0.9F, 0xFFFFFF);
        // the party
        int x = 6, y = 20;
        for (JourneyClient.Member m : JourneyClient.members()) for (CharacterType t : m.picks()) {
            float hp = JourneyClient.hp(t);
            boolean on = m.active() == t && phase == 3 && !m.out();
            g.fill(x - 1, y - 1, x + 19, y + 19, on ? 0xFFFFFFFF : m.me() ? 0xFF2A5A30 : 0xFF2A3A5A);
            JourneyScreen.face(g, t, x, y, 18);
            if (hp <= 0) { g.fill(x, y, x + 18, y + 18, 0xB0300000); JourneyScreen.sc(g, font, "X", x + 6, y + 5, 1F, 0xFF4040); }
            JourneyScreen.hpBar(g, x, y + 20, 18, 2, hp);
            x += 21;
        }
        // coins
        String jc = st.getInt("jc") + " JC";
        g.fill(6, 46, 12 + font.width(jc), 57, 0x90101820);
        g.drawString(font, jc, 9, 48, 0xFF40D0FF, true);
        int rx = width - 6;
        if (phase == 3) {                         // (left side, under the party: the top-right is where stat toasts go)
            JourneyClient.Member me = JourneyClient.me();
            int ly = 62;
            if (me != null) {
                JourneyScreen.sc(g, font, "SCORE", 6, ly, 0.6F, 0xA8B8C8);
                JourneyScreen.sc(g, font, String.format("%,d", me.score()), 6, ly + 7, 1.5F, 0xFFFFFF);
                ly += 21;
                JourneyClient.Member mate = JourneyClient.partner();
                if (mate != null) { JourneyScreen.sc(g, font, mate.name() + " " + String.format("%,d", mate.score()), 6, ly, 0.6F, 0x80B0E0); ly += 8; }
            }
            boolean ok = st.getBoolean("challengeOk");
            JourneyScreen.sc(g, font, (ok ? "\u2714 " : "\u2718 ") + st.getString("challenge"), 6, ly, 0.6F, ok ? 0xFFD040 : 0xE06060);
            int sec = st.getInt("time");
            String info = String.format("%d:%02d", sec / 60, sec % 60) + "   enemies " + st.getInt("enemies") + (st.getInt("waves") > 0 ? "  +" + st.getInt("waves") + " wave" : "");
            JourneyScreen.sc(g, font, info, 6, ly + 8, 0.6F, 0xC0C8D0);
        } else if (phase == 2) {
            String s = "Choosing your team... " + st.getInt("chooseLeft") + "s  (Main Menu > Journey)";
            JourneyScreen.sc(g, font, s, rx - font.width(s) * 0.7F, 6, 0.7F, 0xFFD040);
        } else if (phase == 1 && mc.screen == null) {
            boolean cleared = st.getBoolean("cleared");
            String s = cleared ? "Walk to the next platform  -  Main Menu > Journey: Heal / Enhance" : "Main Menu > Journey: revive your party, then Retry battle";
            JourneyScreen.sc(g, font, s, width / 2F - font.width(s) * 0.35F, height - 72, 0.7F, cleared ? 0xA8E0FF : 0xFFB040);
        }
    }
}
