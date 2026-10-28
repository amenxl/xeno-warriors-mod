package com.xenowarriors.client.journey;

import com.xenowarriors.character.CharacterType;
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
        sc(g, String.format("%02d/%02d", cur, JourneyRules.STEPS), 12 + font.width(JourneyRules.DIFF[diff]) * 2.4F, 14, 1.2F, 0xFFFFFF);
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
        int[] nodes = st.getIntArray("nodes");
        boolean cleared = st.getBoolean("cleared");
        int mw = VW - 10 - rx;
        for (int i = 0; i < nodes.length; i++) {
            float x = rx + 6 + i * (mw - 12) / (float) Math.max(1, nodes.length - 1);
            int yy = 100 - (i % 2) * 5;
            if (i + 1 < nodes.length) {
                float x2 = rx + 6 + (i + 1) * (mw - 12) / (float) Math.max(1, nodes.length - 1);
                g.fill((int) x, yy, (int) x2, yy + 1, i < cur || (i == cur && cleared) ? 0xFF40D0FF : 0xFF303A44);
            }
            int c = i < cur || (i == cur && cleared) ? 0xFF5A5A64 : 0xFF000000 | JourneyRules.STEP_COLOR[nodes[i]];
            diamond(g, (int) x, yy, i == cur ? 5 : 4, i == cur ? 0xFFFF4040 : c);
            if (hover(x - 5, yy - 5, x + 5, yy + 5)) {
                String s = String.format("%02d ", i) + JourneyRules.STEP_NAME[nodes[i]];
                sc(g, s, Math.min(VW - 10 - font.width(s) * 0.6F, x - font.width(s) * 0.3F), 108, 0.6F, 0xFFFFFF);
            }
        }
        if (cur + 1 < nodes.length) sc(g, "Next: " + JourneyRules.STEP_NAME[nodes[cur + 1]] + (cleared ? "" : "  (clear this step first)"), rx, 116, 0.55F, 0xA8B8C8);

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
        boolean retry = shop && !cleared && cur > 0 && nodes.length > cur && nodes[cur] != JourneyRules.HEAL;
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

    static void diamond(GuiGraphics g, int cx, int cy, int r, int c) {
        for (int d = -r; d <= r; d++) { int w = r - Math.abs(d); g.fill(cx - w, cy + d, cx + w + 1, cy + d + 1, c); }
    }
}
