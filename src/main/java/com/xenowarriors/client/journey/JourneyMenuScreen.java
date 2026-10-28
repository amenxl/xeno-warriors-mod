package com.xenowarriors.client.journey;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.journey.JourneyMap;
import com.xenowarriors.journey.JourneyRules;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.List;

/**
 * The Journey menu between steps: Heal/Enhance (heal, revive, power up with Journey Coins), the map of the path,
 * Battle Rewards (what each step paid) and Retreat. Opens on a battle's results first.
 */
public class JourneyMenuScreen extends JourneyScreen {
    private boolean results;
    private long retreatAt;

    public JourneyMenuScreen(boolean results) { super("Journey"); this.results = results; }

    @Override
    protected void draw(GuiGraphics g) {
        var st = JourneyClient.state;
        int phase = JourneyClient.phase(), diff = st.getInt("diff"), cur = st.getInt("cur"), jc = st.getInt("jc");
        boolean shop = phase == 1;
        int[] prices = st.getIntArray("prices");
        // header, like the path's sign: difficulty and step
        sc(g, JourneyRules.DIFF[diff], 10, 6, 2.4F, JourneyRules.DIFF_COLOR[diff]);
        sc(g, "Stage " + (st.getInt("stage") + 1) + "   " + String.format("%02d/%02d", cur, st.getInt("rows")), 12 + font.width(JourneyRules.DIFF[diff]) * 2.4F, 14, 1.2F, 0xFFFFFF);
        // coins
        g.fill(VW - 96, 6, VW - 10, 24, 0xE0101820);
        box(g, VW - 96, 6, VW - 10, 24, 0xFF40D0FF);
        diamond(g, VW - 86, 15, 5, 0xFF40D0FF);
        sc(g, jc + " JC", VW - 76, 10, 1F, 0xFFFFFF);
        sc(g, "Journey Coins", VW - 96, 26, 0.5F, 0x7090A0);

        // the party: heal / revive
        sc(g, "HEAL / ENHANCE" + (shop ? "" : "  (opens after the battle)"), 10, 32, 0.7F, shop ? 0x40D0FF : 0x707A86);
        List<JourneyClient.Member> ms = JourneyClient.members();
        int y = 42;
        for (JourneyClient.Member m : ms) for (CharacterType t : m.picks()) {
            float hp = JourneyClient.hp(t);
            boolean down = hp <= 0;
            g.fill(10, y, 236, y + 18, 0xE0101820);
            g.fill(10, y, 12, y + 18, 0xFF000000 | (t.getColor() & 0xFFFFFF));
            face(g, t, 14, y + 1, 16);
            if (down) g.fill(14, y + 1, 30, y + 17, 0xA0400000);
            sc(g, name(t), 34, y + 2, 0.65F, down ? 0xE06060 : 0xFFFFFF);
            sc(g, ms.size() > 1 ? (m.me() ? "you" : m.name()) : "", 34, y + 10, 0.45F, m.me() ? 0x5FD05F : 0x50A0FF);
            hpBar(g, 90, y + 7, 60, 4, hp);
            sc(g, down ? "DOWN" : Math.round(hp * 100) + "%", 154, y + 6, 0.55F, down ? 0xE04040 : 0xC0D0C0);
            if (down) button(g, 176, y + 2, 234, y + 16, "Revive  " + prices[JourneyRules.S_REVIVE], 0xE0B030, shop && jc >= prices[JourneyRules.S_REVIVE], () -> JourneyClient.act(8, JourneyRules.S_REVIVE, t.ordinal()));
            else {
                button(g, 176, y + 2, 204, y + 16, "+40% " + prices[JourneyRules.S_HEAL], 0x50D060, shop && hp < 1 && jc >= prices[JourneyRules.S_HEAL], () -> JourneyClient.act(8, JourneyRules.S_HEAL, t.ordinal()));
                button(g, 206, y + 2, 234, y + 16, "Full " + prices[JourneyRules.S_FULL], 0x50D060, shop && hp < 1 && jc >= prices[JourneyRules.S_FULL], () -> JourneyClient.act(8, JourneyRules.S_FULL, t.ordinal()));
            }
            y += 20;
        }

        // enhance
        int rx = 246;
        button(g, rx, 42, VW - 10, 58, "Party Heal +25%  -  " + prices[JourneyRules.S_PARTY] + " JC", 0x50D060, shop && jc >= prices[JourneyRules.S_PARTY], () -> JourneyClient.act(8, JourneyRules.S_PARTY, -1));
        boolean pw = st.getBoolean("powerNext");
        button(g, rx, 61, VW - 10, 77, pw ? "Power Up ready for the next battle" : "Power Up (+30% damage next battle)  -  " + prices[JourneyRules.S_POWER] + " JC", 0xFFB040,
                shop && !pw && jc >= prices[JourneyRules.S_POWER], () -> JourneyClient.act(8, JourneyRules.S_POWER, -1));

        // the map
        sc(g, "MAP", rx, 82, 0.7F, 0x40D0FF);
        boolean cleared = st.getBoolean("cleared");
        CompoundTag map = st.getCompound("map");
        ListTag ns = map.getList("nodes", Tag.TAG_COMPOUND);
        int rows = Math.max(1, map.getInt("rows")), hereId = st.getInt("here");
        int mw = VW - 10 - rx;
        float[][] at = new float[ns.size()][];
        for (int i = 0; i < ns.size(); i++) {
            CompoundTag n = ns.getCompound(i);
            at[i] = new float[]{rx + 6 + n.getInt("r") * (mw - 12) / (float) rows, 101 + n.getInt("l") * 8};
        }
        for (int i = 0; i < ns.size(); i++) for (int to : ns.getCompound(i).getIntArray("n")) {
            if (to < 0 || to >= ns.size()) continue;
            int s1 = ns.getCompound(i).getInt("s"), s2 = ns.getCompound(to).getInt("s");
            int col = s2 == JourneyMap.CLOSED || s1 == JourneyMap.CLOSED ? 0xFF24242A : (s1 == JourneyMap.DONE || s1 == JourneyMap.HERE) && (s2 != JourneyMap.AHEAD || cleared && s1 == JourneyMap.HERE) ? 0xFF40D0FF : 0xFF3A4450;
            line(g, at[i][0], at[i][1], at[to][0], at[to][1], col);
        }
        for (int i = 0; i < ns.size(); i++) {
            CompoundTag n = ns.getCompound(i);
            int st8 = n.getInt("s"), kind = Math.max(0, Math.min(JourneyRules.STEP_NAME.length - 1, n.getInt("k")));
            int c = i == hereId ? 0xFFFF4040 : st8 == JourneyMap.DONE ? 0xFF5A5A64 : st8 == JourneyMap.CLOSED ? 0xFF2A2A30 : 0xFF000000 | JourneyRules.STEP_COLOR[kind];
            diamond(g, (int) at[i][0], (int) at[i][1], i == hereId ? 4 : kind == JourneyRules.BOSS ? 4 : 3, c);
            if (hover(at[i][0] - 4, at[i][1] - 4, at[i][0] + 4, at[i][1] + 4)) {
                String s = String.format("%02d ", n.getInt("r")) + (st8 == JourneyMap.CLOSED ? "CLOSED" : JourneyRules.STEP_NAME[kind]);
                sc(g, s, Math.min(VW - 10 - font.width(s) * 0.6F, at[i][0] - font.width(s) * 0.3F), 88, 0.6F, 0xFFFFFF);
            }
        }
        // event boosts
        int[] bs = st.getIntArray("boosts");
        StringBuilder sb = new StringBuilder();
        for (JourneyRules.Boost b : JourneyRules.Boost.values()) {
            int n = b.ordinal() < bs.length ? bs[b.ordinal()] : 0;
            if (n > 0) sb.append(sb.length() > 0 ? ",  " : "").append(b.title).append(" x").append(n);
        }
        sc(g, sb.length() == 0 ? "No event boosts yet - step on EVENT panels" : "Boosts: " + sb, rx, 117, Math.min(0.55F, (mw - 2F) / Math.max(1, font.width("Boosts: " + sb))), sb.length() == 0 ? 0x707A86 : 0xFFD040);

        // battle rewards
        sc(g, "BATTLE REWARDS", rx, 128, 0.7F, 0x40D0FF);
        ListTag log = st.getList("log", Tag.TAG_STRING);
        int ly = 138;
        for (int i = Math.max(0, log.size() - 7); i < log.size(); i++) {
            String s = log.getString(i);
            float k = Math.min(0.5F, (VW - 12F - rx) / font.width(s));
            sc(g, s, rx, ly, k, 0xC0C8D0);
            ly += 7;
        }

        // buttons
        int by = VH - 22;
        int hk = hereId >= 0 && hereId < ns.size() ? ns.getCompound(hereId).getInt("k") : 0;
        boolean retry = shop && !cleared && cur > 0 && JourneyRules.fight(hk);
        if (retry) button(g, rx, by, rx + 74, by + 16, "Retry battle", 0xFF7040, true, () -> { JourneyClient.act(10, 0, 0); onClose(); });
        boolean confirm = net.minecraft.Util.getMillis() - retreatAt < 3000;
        button(g, VW - 84, by, VW - 10, by + 16, confirm ? "Click to confirm" : "Retreat", 0xA05050, true, () -> {
            if (confirm) { JourneyClient.act(9, 0, 0); onClose(); } else retreatAt = net.minecraft.Util.getMillis();
        });
        if (confirm) sc(g, "Retreat ends your journey - no reward", VW - 10 - font.width("Retreat ends your journey - no reward") * 0.5F, by - 7, 0.5F, 0xE06060);

        if (results && st.getCompound("results").contains("won")) drawResults(g, st.getCompound("results"));
    }

    private void drawResults(GuiGraphics g, CompoundTag r) {
        g.flush();
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        drawResultsPanel(g, r);
        g.pose().popPose();
    }

    private void drawResultsPanel(GuiGraphics g, CompoundTag r) {
        g.fill(0, 0, VW, VH, 0xE0000000);
        boolean won = r.getBoolean("won");
        int x0 = 80, y0 = 30, x1 = VW - 80, y1 = VH - 30;
        g.fill(x0, y0, x1, y1, 0xF0101820);
        box(g, x0, y0, x1, y1, won ? 0xFFFFD040 : 0xFFFF4040);
        String t = won ? (r.getBoolean("boss") ? "BOSS DEFEATED" : "VICTORY") : "DEFEAT";
        sc(g, t, VW / 2F - font.width(t), y0 + 8, 2F, won ? 0xFFD040 : 0xFF4040);
        ListTag ps = r.getList("players", Tag.TAG_COMPOUND);
        int y = y0 + 34;
        for (int i = 0; i < ps.size(); i++) {
            CompoundTag p = ps.getCompound(i);
            int rank = Math.max(0, Math.min(4, p.getInt("rank")));
            sc(g, p.getString("name"), x0 + 14, y + 4, 0.8F, 0xFFFFFF);
            sc(g, "Score " + String.format("%,d", p.getInt("score")), x0 + 14, y + 14, 0.65F, 0xA8B8C8);
            if (won) sc(g, JourneyRules.RANK[rank], x1 - 70, y, 2.4F, JourneyRules.RANK_COLOR[rank]);
            sc(g, "+" + p.getInt("jc") + " JC", x1 - 44, y + 7, 0.8F, 0x40D0FF);
            y += 28;
        }
        boolean ok = r.getBoolean("challengeOk");
        sc(g, (ok ? "✔ " : "✘ ") + "Challenge: " + r.getString("challenge"), x0 + 14, y + 4, Math.min(0.65F, (x1 - x0 - 28F) / font.width("xx Challenge: " + r.getString("challenge"))), ok ? 0x5FD05F : 0xE06060);
        sc(g, won ? "Total: +" + r.getInt("gained") + " Journey Coins" : "Revive your party at Heal/Enhance, then Retry battle", x0 + 14, y + 16, 0.7F, 0xFFFFFF);
        sc(g, "Click to continue", VW / 2F - font.width("Click to continue") * 0.3F, y1 - 12, 0.6F, 0x8090A0);
        area(0, 0, VW, VH, () -> results = false);
    }

    private static void line(GuiGraphics g, float x0, float y0, float x1, float y1, int c) {
        int n = (int) Math.max(1, Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)));
        for (int i = 0; i <= n; i++) { int x = Math.round(x0 + (x1 - x0) * i / n), y = Math.round(y0 + (y1 - y0) * i / n); g.fill(x, y, x + 1, y + 1, c); }
    }

    static void diamond(GuiGraphics g, int cx, int cy, int r, int c) {
        for (int d = -r; d <= r; d++) { int w = r - Math.abs(d); g.fill(cx - w, cy + d, cx + w + 1, cy + d + 1, c); }
    }
}
