package com.xenowarriors.client.journey;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.journey.JourneyRules;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/** Journey setup: the difficulty, and each of you picks your 4 characters (8 when alone). */
public class JourneyLobbyScreen extends JourneyScreen {
    public JourneyLobbyScreen() { super("Journey"); }

    @Override
    public void onClose() { super.onClose(); }

    @Override
    protected void draw(GuiGraphics g) {
        var st = JourneyClient.state;
        List<JourneyClient.Member> ms = JourneyClient.members();
        JourneyClient.Member me = JourneyClient.me(), mate = JourneyClient.partner();
        if (me == null) return;
        int cap = st.getInt("cap"), diff = st.getInt("diff"), stage = st.getInt("stage"), maxIdx = st.getInt("maxIdx"), max = maxIdx / JourneyRules.STAGES;
        boolean host = st.getBoolean("host");
        // header
        sc(g, "JOURNEY", 10, 8, 2.4F, 0xFFFFFF);
        sc(g, ms.size() == 1 ? "Solo: pick 8 characters (fewer Journey Coins)" : "Duo: you and " + mate.name() + " pick 4 each",
                10, 32, 0.6F, 0xA8B8C8);
        // difficulty
        sc(g, "DIFFICULTY - clear Stage 3 to open the next" + (host ? "" : " (the host picks)"), 196, 6, 0.55F, 0x8898A8);
        for (int i = 0; i < 5; i++) {
            int x0 = 196 + i * 40, y0 = 14, col = JourneyRules.DIFF_COLOR[i];
            boolean locked = i > max, sel = i == diff;
            g.fill(x0, y0, x0 + 38, y0 + 16, sel ? 0xFF000000 | (col & 0x3F3F3F) + 0x101010 : 0xE0141820);
            box(g, x0, y0, x0 + 38, y0 + 16, sel ? 0xFF000000 | col : locked ? 0xFF2A2A30 : 0xFF404A54);
            String n = JourneyRules.DIFF[i];
            float k = Math.min(0.75F, 34F / font.width(n));
            sc(g, n, x0 + 19 - font.width(n) * k / 2F, y0 + 3, k, locked ? 0x50505A : col);
            sc(g, locked ? "locked" : sel ? "selected" : "", x0 + 19 - font.width(locked ? "locked" : "selected") * 0.45F / 2F, y0 + 11, 0.45F, locked ? 0x60606A : 0xFFFFFF);
            if (host && !locked && !sel) { final int d = i; area(x0, y0, x0 + 38, y0 + 16, () -> JourneyClient.act(2, d, 0)); }
        }
        // stage (longer each time, pays more)
        for (int i = 0; i < JourneyRules.STAGES; i++) {
            int x0 = 196 + i * 67, y0 = 32, col = JourneyRules.DIFF_COLOR[diff];
            boolean locked = diff * JourneyRules.STAGES + i > maxIdx, sel = i == stage;
            g.fill(x0, y0, x0 + 65, y0 + 10, sel ? 0xFF000000 | (col & 0x3F3F3F) + 0x101010 : 0xE0141820);
            box(g, x0, y0, x0 + 65, y0 + 10, sel ? 0xFF000000 | col : locked ? 0xFF2A2A30 : 0xFF404A54);
            String n = "STAGE " + (i + 1) + "  " + (locked ? "locked" : JourneyRules.STAGE_ROWS[i] + " steps");
            sc(g, n, x0 + 32.5F - font.width(n) * 0.5F / 2F, y0 + 3, 0.5F, locked ? 0x50505A : sel ? 0xFFFFFF : col);
            if (host && !locked && !sel) { final int s = i; area(x0, y0, x0 + 65, y0 + 10, () -> JourneyClient.act(11, s, 0)); }
        }

        // the characters
        List<CharacterType> avail = new ArrayList<>();
        for (CharacterType t : CharacterType.PLAYABLE) if (t.season() <= st.getInt("season")) avail.add(t);
        int per = Math.min(9, avail.size()), cw = 40, ch = 50;
        int gx = (VW - per * (cw + 3)) / 2, gy = 44;
        for (int i = 0; i < avail.size(); i++) {
            CharacterType t = avail.get(i);
            int x = gx + (i % per) * (cw + 3), y = gy + (i / per) * (ch + 3);
            int mine = me.picks().indexOf(t);
            boolean theirs = mate != null && mate.picks().contains(t);
            boolean full = mine < 0 && !theirs && me.picks().size() >= cap;
            boolean h = hover(x, y, x + cw, y + ch) && !theirs;
            g.fill(x, y, x + cw, y + ch, mine >= 0 ? 0xF0183020 : theirs ? 0xF0141C30 : h ? 0xF0202830 : 0xF0141820);
            g.fill(x, y, x + cw, y + 2, 0xFF000000 | (t.getColor() & 0xFFFFFF));
            face(g, t, x + 7, y + 5, 26);
            if (theirs || full) g.fill(x + 7, y + 5, x + 33, y + 31, 0x90000000);
            String n = name(t);
            float k = Math.min(0.6F, (cw - 4F) / font.width(n));
            sc(g, n, x + cw / 2F - font.width(n) * k / 2F, y + 34, k, 0xE8E8F0);
            if (t.skylander()) sc(g, "Skylander", x + cw / 2F - font.width("Skylander") * 0.42F / 2F, y + 42, 0.42F, 0x80C8FF);
            else if (t.skyPatrol()) sc(g, "Sky Patrol", x + cw / 2F - font.width("Sky Patrol") * 0.42F / 2F, y + 42, 0.42F, 0xF080C0);
            else sc(g, "Human", x + cw / 2F - font.width("Human") * 0.42F / 2F, y + 42, 0.42F, 0xC0C0A0);
            if (mine >= 0) { box(g, x, y, x + cw, y + ch, 0xFF5FD05F); g.fill(x + cw - 9, y + 3, x + cw - 2, y + 11, 0xFF5FD05F); sc(g, String.valueOf(mine + 1), x + cw - 7.5F, y + 3.5F, 0.7F, 0x102010); }
            else if (theirs) { box(g, x, y, x + cw, y + ch, 0xFF50A0FF); sc(g, mate.name(), x + 2, y + 6, 0.45F, 0x80C0FF); }
            else box(g, x, y, x + cw, y + ch, h ? 0xFF8A9AAA : 0xFF2A3038);
            if (!theirs && (mine >= 0 || !full)) area(x, y, x + cw, y + ch, () -> JourneyClient.act(1, t.ordinal(), 0));
        }

        // your team / partner's team
        int py = 156;
        int col = 0;
        for (JourneyClient.Member m : ms) {
            int x0 = ms.size() == 1 ? 10 : 10 + col * 190, x1 = ms.size() == 1 ? VW - 10 : x0 + 184;
            g.fill(x0, py, x1, py + 40, 0xE0101820);
            box(g, x0, py, x1, py + 40, m.ready() ? 0xFF5FD05F : 0xFF303A44);
            sc(g, (m.me() ? "YOU" : m.name()) + "  " + m.picks().size() + "/" + cap, x0 + 4, py + 3, 0.7F, m.me() ? 0x5FD05F : 0x50A0FF);
            sc(g, m.ready() ? "READY" : "picking...", x1 - 4 - font.width(m.ready() ? "READY" : "picking...") * 0.6F, py + 3, 0.6F, m.ready() ? 0x5FD05F : 0x8090A0);
            for (int i = 0; i < cap; i++) {
                int fx = x0 + 4 + i * 22, fy = py + 13;
                g.fill(fx, fy, fx + 20, fy + 20, 0xFF0A0E12);
                if (i < m.picks().size()) face(g, m.picks().get(i), fx + 1, fy + 1, 18);
                box(g, fx, fy, fx + 20, fy + 20, 0xFF2A3440);
            }
            col++;
        }
        if (ms.size() == 1) sc(g, "Want a partner? Party up with one player - they join from Main Menu > Journey.", 10, py + 43, 0.55F, 0x707A86);

        // buttons
        int by = 204;
        boolean full = me.picks().size() == cap;
        button(g, 10, by, 80, by + 16, "Leave", 0xA05050, true, () -> JourneyClient.act(5, 0, 0));
        if (ms.size() > 1 && !host) button(g, VW - 110, by, VW - 10, by + 16, me.ready() ? "Not ready" : "Ready", 0x5FD05F, full, () -> JourneyClient.act(3, 0, 0));
        if (host) {
            boolean ok = full;
            for (JourneyClient.Member m : ms) if (!m.me() && (!m.ready() || m.picks().size() != cap)) ok = false;
            button(g, VW - 120, by, VW - 10, by + 16, "START JOURNEY", JourneyRules.DIFF_COLOR[diff], ok, () -> JourneyClient.act(4, 0, 0));
            if (!ok) {
                String why = !full ? "Pick " + (cap - me.picks().size()) + " more" : "Waiting for " + (mate == null ? "" : mate.name()) + " to be ready";
                sc(g, why, VW - 124 - font.width(why) * 0.6F, by + 5, 0.6F, 0x8090A0);
            }
        }
    }
}
