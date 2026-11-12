package com.xenowarriors.client.kaeru;

import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.ScentPacket;
import com.xenowarriors.network.ToggleActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * Kaeru's Scent Tracking picker: a card for everything she can smell nearby - the creature itself, its name and how far -
 * enemies first (red), then animals and others (green), then people (blue). Click one to follow its scent: it's
 * highlighted through walls and its trail shows (ScentClient).
 */
public class ScentScreen extends Screen {
    private final List<ScentPacket.Entry> entries;
    private static final int CW = 64, CH = 78, GAP = 6;
    private int cols, x0, y0;

    public ScentScreen(List<ScentPacket.Entry> entries) {
        super(Component.literal("Scent Tracking"));
        this.entries = entries;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        cols = Math.max(1, Math.min(8, (width - 20) / (CW + GAP)));
        int rows = Math.max(1, (entries.size() + cols - 1) / cols);
        int maxRows = Math.max(1, (height - 60) / (CH + GAP));
        rows = Math.min(rows, maxRows);
        int shownCols = Math.min(cols, Math.max(1, entries.size()));
        x0 = (width - shownCols * (CW + GAP) + GAP) / 2;
        y0 = Math.max(34, (height - rows * (CH + GAP)) / 2 + 8);
    }

    private int[] at(int i) { return new int[]{x0 + (i % cols) * (CW + GAP), y0 + (i / cols) * (CH + GAP)}; }

    private static int color(int kind) { return kind == 0 ? 0xE04040 : kind == 2 ? 0x5A9AFF : 0x5AD05A; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, "Scent Tracking - pick a scent to follow", width / 2, 12, 0xFFB050);
        if (entries.isEmpty()) g.drawCenteredString(font, "Nothing to smell nearby.", width / 2, height / 2, 0xA0A0A0);
        int maxRows = Math.max(1, (height - 60) / (CH + GAP));
        for (int i = 0; i < Math.min(entries.size(), cols * maxRows); i++) {
            var e = entries.get(i);
            int[] a = at(i);
            boolean hov = mx >= a[0] && mx < a[0] + CW && my >= a[1] && my < a[1] + CH;
            int c = color(e.kind());
            g.fill(a[0] - 1, a[1] - 1, a[0] + CW + 1, a[1] + CH + 1, hov ? 0xFFFFFFFF : 0xFF000000 | c);
            g.fill(a[0], a[1], a[0] + CW, a[1] + CH, hov ? 0xF0303038 : 0xF0141418);
            if (minecraft.level != null && minecraft.level.getEntity(e.id()) instanceof LivingEntity le) {   // the creature itself
                g.enableScissor(a[0] + 1, a[1] + 1, a[0] + CW - 1, a[1] + CH - 22);
                float h = Math.max(0.6F, le.getBbHeight()), w = Math.max(0.6F, le.getBbWidth());
                int size = (int) Math.max(6, Math.min(26, 42 / Math.max(h, w)));
                try {
                    InventoryScreen.renderEntityInInventoryFollowsMouse(g, a[0] + CW / 2, a[1] + CH - 26, size, (float) (a[0] + CW / 2 - mx), (float) (a[1] + 20 - my), le);
                } catch (Throwable ignored) {}
                g.disableScissor();
            } else g.drawCenteredString(font, "?", a[0] + CW / 2, a[1] + 24, 0x808080);
            fit(g, e.name(), a[0] + CW / 2, a[1] + CH - 19, CW - 4, 0xFFFFFF);
            fit(g, e.distance() + " blocks", a[0] + CW / 2, a[1] + CH - 9, CW - 4, 0xFF000000 | c);
        }
        g.drawCenteredString(font, "Esc: never mind", width / 2, height - 14, 0x808080);
        super.render(g, mx, my, pt);
    }

    private void fit(GuiGraphics g, String s, int cx, int y, int room, int col) {
        float k = Math.min(1F, room / (float) Math.max(1, font.width(s)));
        g.pose().pushPose(); g.pose().translate(cx, y, 0); g.pose().scale(k, k, 1F);
        g.drawCenteredString(font, s, 0, 0, col); g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int maxRows = Math.max(1, (height - 60) / (CH + GAP));
        for (int i = 0; i < Math.min(entries.size(), cols * maxRows); i++) {
            int[] a = at(i);
            if (mx >= a[0] && mx < a[0] + CW && my >= a[1] && my < a[1] + CH) {
                ModNetwork.sendToServer(new ToggleActionPacket(ToggleActionPacket.SCENT_PICK, String.valueOf(entries.get(i).id())));
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    /** (Tests) picks the first card. */
    public void pickFirstForTest() { if (!entries.isEmpty()) ModNetwork.sendToServer(new ToggleActionPacket(ToggleActionPacket.SCENT_PICK, String.valueOf(entries.get(0).id()))); }
}
