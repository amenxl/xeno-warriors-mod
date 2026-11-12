package com.xenowarriors.client.screen;

import com.xenowarriors.network.C2Packet;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * The Gov terminal's C2 Procedures page: the three paths a Human You character can take (you.YouPaths), what each needs,
 * and a confirm step (it's for good). An Artificial Skylander picks Lunar or Solar first.
 */
public class C2Screen extends Screen {
    private final C2Packet data;
    /** Path being confirmed (-1: none); a Skylander's form. */
    private int confirming = -1, form = 1;

    public C2Screen(C2Packet data) { super(Component.literal("C2 Procedures")); this.data = data; }

    public static void received(C2Packet m) {
        Minecraft mc = Minecraft.getInstance();
        if (m.mode() == -2) { if (mc.screen instanceof C2Screen) mc.setScreen(null); return; }
        mc.setScreen(new C2Screen(m));
    }

    /** From the Supply Terminal's button. */
    public static void ask() { ModNetwork.sendToServer(new C2Packet(List.of(), 0, "", -3, 0)); }

    private int panelW() { return Math.min(440, width - 20); }
    private int left() { return width / 2 - panelW() / 2; }
    private int top() { return Math.max(40, height / 2 - 110); }
    private static final int CARD_H = 62;

    @Override
    protected void init() {
        int x0 = left(), w = panelW(), y0 = top();
        if (confirming >= 0) {
            C2Packet.Row r = data.rows().get(confirming);
            int cy = y0 + 70;
            if (r.id() == com.xenowarriors.you.YouSlot.PATH_SKYLANDER) {
                addRenderableWidget(Button.builder(Component.literal((form == 1 ? "> " : "") + "Lunar form"), b -> { form = 1; rebuildWidgets(); }).bounds(width / 2 - 104, cy, 100, 20).build());
                addRenderableWidget(Button.builder(Component.literal((form == 2 ? "> " : "") + "Solar form"), b -> { form = 2; rebuildWidgets(); }).bounds(width / 2 + 4, cy, 100, 20).build());
                cy += 28;
            }
            addRenderableWidget(Button.builder(Component.literal("Sign and begin"), b -> ModNetwork.sendToServer(new C2Packet(List.of(), 0, "", r.id(), form))).bounds(width / 2 - 104, cy, 100, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Back"), b -> { confirming = -1; rebuildWidgets(); }).bounds(width / 2 + 4, cy, 100, 20).build());
            return;
        }
        for (int i = 0; i < data.rows().size(); i++) {
            int idx = i;
            C2Packet.Row r = data.rows().get(i);
            int y = y0 + i * (CARD_H + 4);
            Button b = addRenderableWidget(Button.builder(Component.literal("Undergo"), bt -> { confirming = idx; rebuildWidgets(); }).bounds(x0 + w - 70, y + CARD_H - 24, 64, 20).build());
            b.active = r.why().isEmpty();
        }
        addRenderableWidget(Button.builder(Component.literal("Back to the terminal"), b -> onClose()).bounds(width / 2 - 70, Math.min(height - 26, y0 + 3 * (CARD_H + 4) + 6), 140, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xE0050A12);
        for (int y = 0; y < height; y += 3) g.fill(0, y, width, y + 1, 0x0A40C0FF);
        int x0 = left(), w = panelW(), y0 = top();
        g.fill(x0 - 2, y0 - 30, x0 + w + 2, y0 - 29, 0xFF40C8FF);
        g.drawString(font, "AURORA GOVERNMENT  //  C2 - HUMAN MODIFICATION PROGRAM", x0, y0 - 24, 0xFF8FE4FF, false);
        g.drawString(font, data.who(), x0, y0 - 12, 0xFFDDEEFF, false);
        if (confirming >= 0) {
            C2Packet.Row r = data.rows().get(confirming);
            g.drawCenteredString(font, "Undergo the " + r.title() + " procedure?", width / 2, y0 + 14, 0xFFFFFFFF);
            g.drawCenteredString(font, "This can't be undone. Cost: " + com.xenowarriors.economy.Money.fmt(r.price()) + " Money" + (r.card() ? " and your Demon Program Card" : ""), width / 2, y0 + 28, 0xFFFF9A6A);
            if (r.id() == com.xenowarriors.you.YouSlot.PATH_SKYLANDER) g.drawCenteredString(font, "Pick your form: Lunar is stronger at night, Solar by day.", width / 2, y0 + 50, 0xFFB0C8E0);
            super.render(g, mx, my, pt);
            return;
        }
        for (int i = 0; i < data.rows().size(); i++) {
            C2Packet.Row r = data.rows().get(i);
            int y = y0 + i * (CARD_H + 4);
            boolean ok = r.why().isEmpty();
            g.fill(x0, y, x0 + w, y + CARD_H, 0xB00E1A28);
            g.fill(x0, y, x0 + 3, y + CARD_H, ok ? 0xFF40C8FF : 0xFF5A6774);
            g.drawString(font, r.title(), x0 + 8, y + 4, ok ? 0xFFFFFFFF : 0xFFB0B8C0, false);
            String reqs = "Lv " + r.level() + " - " + r.need() + " - " + com.xenowarriors.economy.Money.fmt(r.price()) + " Money" + (r.card() ? " + card" : "");
            while (font.width(reqs) > w - 16 - font.width(r.title()) - 10 && reqs.length() > 4) reqs = reqs.substring(0, reqs.length() - 2);   // (never past the edge)
            g.drawString(font, reqs, x0 + 8 + font.width(r.title()) + 10, y + 4, 0xFF7CC8FF, false);
            List<FormattedCharSequence> lines = font.split(Component.literal(r.desc()), w - 90);
            for (int k = 0; k < Math.min(3, lines.size()); k++) g.drawString(font, lines.get(k), x0 + 8, y + 16 + k * 10, 0xFFC8D4E0, false);
            if (!ok) g.drawString(font, r.why(), x0 + 8, y + CARD_H - 11, 0xFFFF8A6A, false);
        }
        super.render(g, mx, my, pt);
    }

    @Override public boolean isPauseScreen() { return false; }
}
