package com.xenowarriors.client.screen;

import com.xenowarriors.network.BountyClaimPacket;
import com.xenowarriors.network.BountyPacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;

/** The Bounty Board: three wanted posters pinned to a wooden board. */
public class BountyScreen extends Screen {
    private final BountyPacket m;
    private static final int W = 300, H = 170;
    private static final String[] TIER = {"", "Easy", "Medium", "Hard"};
    private static final int[] TIER_COL = {0, 0xFF3A7A2A, 0xFF9A6A12, 0xFF9A2A2A};

    public BountyScreen(BountyPacket m) { super(Component.literal("Bounty Board")); this.m = m; }

    private int x0() { return (width - W) / 2; }
    private int y0() { return (height - H) / 2; }

    @Override
    protected void init() {
        for (int i = 0; i < m.ids().length; i++) {
            int idx = i;
            boolean done = m.have()[i] >= m.need()[i];
            Button b = Button.builder(Component.literal(m.claimed()[i] ? "Claimed" : done ? "Turn in" : "In progress"), btn -> ModNetwork.sendToServer(new BountyClaimPacket(idx)))
                    .bounds(x0() + 12 + i * 96, y0() + H - 34, 84, 18).build();
            b.active = done && !m.claimed()[i];
            addRenderableWidget(b);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x = x0(), y = y0();
        g.fill(x - 4, y - 4, x + W + 4, y + H + 4, 0xFF3A2614);
        g.fill(x, y, x + W, y + H, 0xFF7A5230);
        for (int k = 0; k < H; k += 12) g.fill(x, y + k, x + W, y + k + 1, 0xFF6A4424);     // planks
        g.drawCenteredString(font, "BOUNTY BOARD", x + W / 2, y + 6, 0xFFFFE6B0);
        for (int i = 0; i < m.ids().length; i++) {
            int px = x + 10 + i * 96, py = y + 20;
            g.fill(px, py, px + 88, py + 108, m.claimed()[i] ? 0xFFB8B0A0 : 0xFFEADFC4);    // poster
            g.fill(px + 40, py - 2, px + 48, py + 4, 0xFFAA2A2A);                            // pin
            g.drawCenteredString(font, "WANTED", px + 44, py + 8, 0xFF6A1A12);
            String name = EntityType.byString(m.ids()[i]).map(t -> t.getDescription().getString()).orElse(m.ids()[i]);
            if (font.width(name) <= 82) g.drawCenteredString(font, name, px + 44, py + 26, 0xFF2A2A2A);
            else {                                    // long names ("Possessed Villager") on two smaller lines instead of cut off
                var lines = font.split(net.minecraft.network.chat.Component.literal(name), (int) (82 / 0.8F));
                for (int li = 0; li < Math.min(2, lines.size()); li++) {
                    g.pose().pushPose();
                    g.pose().translate(px + 44, py + 21 + li * 9, 0);
                    g.pose().scale(0.8F, 0.8F, 1F);
                    g.drawCenteredString(font, lines.get(li), 0, 0, 0xFF2A2A2A);
                    g.pose().popPose();
                }
            }
            g.drawCenteredString(font, "Defeat " + m.need()[i], px + 44, py + 42, 0xFF4A3A2A);
            int t = Math.max(1, Math.min(3, m.tier()[i]));
            g.drawCenteredString(font, TIER[t], px + 44, py + 56, TIER_COL[t]);
            float f = Math.min(1F, m.have()[i] / (float) Math.max(1, m.need()[i]));
            g.fill(px + 8, py + 74, px + 80, py + 80, 0xFFC8BCA0);
            g.fill(px + 8, py + 74, px + 8 + (int) (72 * f), py + 80, f >= 1 ? 0xFF4AAA3A : 0xFFC88A2A);
            g.drawCenteredString(font, Math.min(m.have()[i], m.need()[i]) + "/" + m.need()[i], px + 44, py + 86, 0xFF4A3A2A);
        }
        int mins = m.refreshIn() / 60;
        g.drawCenteredString(font, "New bounties in " + mins + " min", x + W / 2, y + H - 12, 0xFFE8D2A8);
        super.render(g, mx, my, pt);
    }

    @Override public boolean isPauseScreen() { return false; }
}
