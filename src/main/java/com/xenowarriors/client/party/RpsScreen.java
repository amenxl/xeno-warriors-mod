package com.xenowarriors.client.party;

import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.RpsPacket;
import com.xenowarriors.network.RpsPickPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A bot's rock, paper, scissors challenge at a party: three hands to pick from, a countdown, then both hands and who
 * won. A draw goes again. (bot.BotRps decides; this only shows it and sends your pick.)
 */
public class RpsScreen extends Screen {
    private static final String[] HANDS = {"Rock", "Paper", "Scissors"};
    private static final int W = 240, H = 150;
    private final int botId;
    private final String name;
    private int round, botPick = -1, myPick = -1, outcome = -1, closeIn = -1, ticks;
    private boolean sent;

    public RpsScreen(RpsPacket m) {
        super(Component.literal("Rock, paper, scissors"));
        botId = m.botId(); name = m.name(); round = m.round();
    }

    @Override public boolean isPauseScreen() { return false; }

    /** From the server. */
    public static void received(RpsPacket m) {
        Minecraft mc = Minecraft.getInstance();
        if (m.outcome() == 3) { if (mc.screen instanceof RpsScreen) mc.setScreen(null); return; }
        if (m.outcome() < 0) { mc.setScreen(new RpsScreen(m)); return; }
        if (!(mc.screen instanceof RpsScreen s) || s.botId != m.botId()) return;
        s.botPick = m.botPick(); s.myPick = m.myPick(); s.outcome = m.outcome();
        if (m.outcome() == 0 && m.round() > s.round) { s.round = m.round(); s.sent = false; s.ticks = 0; s.rebuildWidgets(); }   // (a draw: go again)
        else s.closeIn = 60;
        s.rebuildWidgets();
    }

    private int x0() { return (width - W) / 2; }
    private int y0() { return (height - H) / 2; }

    @Override
    protected void init() {
        int bw = 70;
        for (int i = 0; i < 3; i++) {
            int k = i;
            Button b = addRenderableWidget(Button.builder(Component.literal(HANDS[i]), x -> pick(k)).bounds(x0() + 10 + i * (bw + 5), y0() + H - 30, bw, 20).build());
            b.active = !sent && closeIn < 0;
        }
    }

    public void pick(int k) {
        if (sent || closeIn >= 0) return;
        sent = true;
        myPick = k;
        ModNetwork.sendToServer(new RpsPickPacket(botId, k));
        rebuildWidgets();
    }

    @Override
    public void tick() {
        ticks++;
        if (closeIn > 0 && --closeIn == 0) onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x = x0(), y = y0();
        g.fill(x - 2, y - 2, x + W + 2, y + H + 2, 0xFFFFD24A);
        g.fill(x, y, x + W, y + H, 0xF0141420);
        g.drawCenteredString(font, name + " challenges you!", width / 2, y + 8, 0xFFE070);
        g.drawCenteredString(font, "Rock, paper, scissors" + (round > 1 ? " - round " + round : ""), width / 2, y + 20, 0xC0C0C0);
        hand(g, x + W / 4, y + 66, myPick, "You", outcome == 1);
        hand(g, x + 3 * W / 4, y + 66, closeIn >= 0 || outcome == 0 ? botPick : -1, name, outcome == 2);
        g.drawCenteredString(font, "vs", width / 2, y + 62, 0x909090);
        String line;
        int c = 0xFFFFFF;
        if (outcome == 1 && closeIn >= 0) { line = "You win! (XP" + (com.xenowarriors.client.compat.DmzMenu.available() ? " and TP" : "") + ")"; c = 0x7CFF7C; }
        else if (outcome == 2) { line = name + " wins!"; c = 0xFF8080; }
        else if (outcome == 0 && closeIn >= 0) { line = "Three draws - call it even!"; c = 0xC0C0C0; }
        else if (outcome == 0 && !sent) { line = "A draw - again! Pick your hand"; c = 0xFFE070; }
        else if (sent) line = "Shoot!";
        else { int left = Math.max(0, 10 - ticks / 20); line = "Pick your hand... (" + left + ")"; }
        g.drawCenteredString(font, line, width / 2, y + 100, c);
        super.render(g, mx, my, pt);
    }

    /** A hand drawn from fills: a fist, a flat hand, two fingers. */
    private void hand(GuiGraphics g, int cx, int cy, int which, String who, boolean won) {
        int skin = 0xFFE0B080, edge = won ? 0xFF7CFF7C : 0xFF6A4A30;
        g.drawCenteredString(font, who, cx, cy - 30, won ? 0x7CFF7C : 0xFFFFFF);
        if (which < 0) { g.drawCenteredString(font, "?", cx, cy - 4, 0x909090); return; }
        switch (which) {
            case 0 -> { g.fill(cx - 11, cy - 11, cx + 11, cy + 11, edge); g.fill(cx - 9, cy - 9, cx + 9, cy + 9, skin); for (int i = 0; i < 3; i++) g.fill(cx - 9, cy - 5 + i * 5, cx + 5, cy - 4 + i * 5, edge); }
            case 1 -> { g.fill(cx - 12, cy - 14, cx + 12, cy + 12, edge); g.fill(cx - 10, cy - 12, cx + 10, cy + 10, skin); for (int i = 1; i < 4; i++) g.fill(cx - 10 + i * 5, cy - 12, cx - 9 + i * 5, cy - 2, edge); }
            default -> {
                g.fill(cx - 9, cy - 1, cx + 9, cy + 12, edge); g.fill(cx - 7, cy + 1, cx + 7, cy + 10, skin);
                g.fill(cx - 8, cy - 16, cx - 2, cy, edge); g.fill(cx - 7, cy - 15, cx - 3, cy + 1, skin);
                g.fill(cx + 2, cy - 16, cx + 8, cy, edge); g.fill(cx + 3, cy - 15, cx + 7, cy + 1, skin);
            }
        }
        g.drawCenteredString(font, HANDS[which], cx, cy + 16, 0xE0E0E0);
    }
}
