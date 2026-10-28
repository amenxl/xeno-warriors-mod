package com.xenowarriors.client.journey;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.journey.JourneyRules;
import net.minecraft.client.gui.GuiGraphics;

/** Before a battle: pick which of your characters come in (the first one starts; the rest tag in when someone goes down). */
public class JourneyTeamScreen extends JourneyScreen {
    public JourneyTeamScreen() { super("Choose your team"); }

    @Override
    protected void draw(GuiGraphics g) {
        var st = JourneyClient.state;
        JourneyClient.Member me = JourneyClient.me(), mate = JourneyClient.partner();
        if (me == null) return;
        int kind = st.getInt("kind"), cur = st.getInt("cur");
        JourneyRules.Rule rule = JourneyRules.Rule.values()[Math.max(0, Math.min(JourneyRules.Rule.values().length - 1, st.getInt("rule")))];
        String scen = st.getString("scenario");
        int col = JourneyRules.STEP_COLOR[Math.max(0, Math.min(4, kind))];
        String head = kind == JourneyRules.BOSS ? "BOSS BATTLE" : (kind == JourneyRules.SCENARIO ? "SCENARIO " : "BATTLE ") + String.format("%02d", cur);
        sc(g, head, 10, 8, 2F, col);
        sc(g, JourneyRules.DIFF[st.getInt("diff")] + "  -  step " + cur + "/" + JourneyRules.STEPS, 10, 27, 0.6F, 0x8898A8);
        String left = "Starts in " + st.getInt("chooseLeft") + "s";
        sc(g, left, VW - 10 - font.width(left) * 0.7F, 10, 0.7F, 0xA8B8C8);
        int y = 38;
        if (!scen.isEmpty()) {
            g.fill(10, y, VW - 10, y + 26, 0xE0102028);
            box(g, 10, y, VW - 10, y + 26, 0xFF40D0FF);
            sc(g, scen.toUpperCase(), 15, y + 3, 0.85F, 0x40D0FF);
            sc(g, st.getString("scenarioText"), 15, y + 14, 0.6F, 0xD0E0E8);
            y += 30;
        }
        String foe = switch (st.getString("foe")) {
            case "GOV" -> "Government agents"; case "SR" -> "Sky Rebellion members"; case "SKY_HATERS" -> "Sky Haters"; case "DARK" -> "Dark Possessed"; default -> "Monsters";
        };
        sc(g, "Enemies: " + (kind == JourneyRules.BOSS ? "a boss and its helpers" : foe) + (st.getInt("waves") > 1 ? "  (" + st.getInt("waves") + " waves)" : ""), 12, y, 0.65F, 0xC8C8D0);
        sc(g, "Who can fight: " + rule.text, 12, y + 9, 0.65F, rule == JourneyRules.Rule.ANY ? 0xC8C8D0 : 0xFFD040);
        // the challenge
        g.fill(VW - 190, y - 2, VW - 10, y + 18, 0xE0282010);
        box(g, VW - 190, y - 2, VW - 10, y + 18, 0xFFE0B030);
        sc(g, "CHALLENGE (bonus JC)", VW - 186, y, 0.5F, 0xE0B030);
        String ch = st.getString("challenge");
        sc(g, ch, VW - 186, y + 7, Math.min(0.7F, 172F / font.width(ch)), 0xFFF0C0);
        y += 24;

        // your characters
        sc(g, me.ready() ? "You're ready" : "Click who comes in - the first one starts, the rest tag in (up to 4)", 12, y, 0.65F, me.ready() ? 0x5FD05F : 0xFFFFFF);
        y += 10;
        int n = me.picks().size(), cw = n > 4 ? 44 : 80, chh = n > 4 ? 70 : 88;
        int gx = (VW - n * (cw + 4)) / 2;
        for (int i = 0; i < n; i++) {
            CharacterType t = me.picks().get(i);
            int x = gx + i * (cw + 4);
            float hp = JourneyClient.hp(t);
            boolean down = hp <= 0, fits = rule.fits(t), can = !down && fits && !me.ready();
            int order = me.chosen().indexOf(t);
            boolean h = can && hover(x, y, x + cw, y + chh);
            g.fill(x, y, x + cw, y + chh, order >= 0 ? 0xF0183020 : h ? 0xF0202830 : 0xF0141820);
            g.fill(x, y, x + cw, y + 2, 0xFF000000 | (t.getColor() & 0xFFFFFF));
            int fs = cw - 16;
            face(g, t, x + 8, y + 6, fs);
            if (down || !fits) g.fill(x + 8, y + 6, x + 8 + fs, y + 6 + fs, 0xA0000000);
            String nm = name(t);
            float k = Math.min(0.7F, (cw - 4F) / font.width(nm));
            sc(g, nm, x + cw / 2F - font.width(nm) * k / 2F, y + fs + 9, k, 0xF0F0F0);
            hpBar(g, x + 5, y + fs + 19, cw - 10, 4, hp);
            String status = down ? "DOWN" : !fits ? "Can't fight here" : Math.round(hp * 100) + "%";
            float sk = Math.min(0.55F, (cw - 4F) / font.width(status));
            sc(g, status, x + cw / 2F - font.width(status) * sk / 2F, y + fs + 26, sk, down ? 0xE04040 : !fits ? 0x8A8A94 : 0xC0D0C0);
            if (order >= 0) {
                box(g, x, y, x + cw, y + chh, 0xFF5FD05F);
                g.fill(x + 2, y + 4, x + 12, y + 14, 0xFF5FD05F);
                sc(g, String.valueOf(order + 1), x + 4.5F, y + 5.5F, 0.8F, 0x102010);
                if (order == 0) sc(g, "STARTS", x + cw - 4 - font.width("STARTS") * 0.45F, y + 5, 0.45F, 0x5FD05F);
            } else box(g, x, y, x + cw, y + chh, h ? 0xFF8A9AAA : 0xFF2A3038);
            if (can) area(x, y, x + cw, y + chh, () -> JourneyClient.act(6, t.ordinal(), 0));
        }
        y += chh + 6;
        if (mate != null) {
            String s = mate.out() ? mate.name() + " has nobody who can fight here - they'll watch" : mate.name() + (mate.ready() ? " is ready with:" : " is choosing...");
            sc(g, s, 12, y + 4, 0.65F, mate.ready() ? 0x50A0FF : 0x8090A0);
            for (int i = 0; i < mate.chosen().size(); i++) face(g, mate.chosen().get(i), 12 + (int) (font.width(s) * 0.65F) + 6 + i * 16, y, 14);
        }
        button(g, VW - 110, VH - 22, VW - 10, VH - 6, me.ready() ? "Change team" : "READY", 0x5FD05F, me.ready() || !me.chosen().isEmpty(), () -> JourneyClient.act(7, 0, 0));
        if (!me.ready() && me.chosen().isEmpty()) sc(g, "Pick at least one", VW - 114 - font.width("Pick at least one") * 0.6F, VH - 17, 0.6F, 0x8090A0);
    }

    @Override public boolean shouldCloseOnEsc() { return true; }
}
