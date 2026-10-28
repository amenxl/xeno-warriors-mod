package com.xenowarriors.client.screen;

import com.xenowarriors.gov.GovShop;
import com.xenowarriors.network.GovShopPacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** The Government Supply Terminal: a cold blue kiosk screen listing Government gear for Money. */
public class GovShopScreen extends Screen {
    private final long money;
    private final int discount;
    private int scroll;
    private static final int ROW = 26, COLS = 2;

    public GovShopScreen(long money, int discount) { super(Component.literal("Government Supply Terminal")); this.money = money; this.discount = discount; }

    public static void open(long money, int discount) {
        Minecraft mc = Minecraft.getInstance();
        int keep = mc.screen instanceof GovShopScreen g ? g.scroll : 0;
        GovShopScreen s = new GovShopScreen(money, discount);
        s.scroll = keep;
        mc.setScreen(s);
    }

    private int panelW() { return Math.min(420, width - 20); }
    private int left() { return width / 2 - panelW() / 2; }
    private int top() { return Math.max(44, height / 2 - 110); }
    private long price(int i) { return GovShop.OFFERS.get(i).price() * (100 - discount) / 100; }

    @Override
    protected void init() {
        int cw = (panelW() - 12) / COLS;
        boolean creative = minecraft != null && minecraft.player != null && minecraft.player.isCreative();
        for (int i = 0; i < GovShop.OFFERS.size(); i++) {
            int idx = i, c = i % COLS, r = i / COLS;
            int x = left() + 4 + c * (cw + 4), y = top() + r * ROW;
            if (y + ROW > height - 24) continue;
            Button b = addRenderableWidget(Button.builder(Component.literal("Buy"), bt -> ModNetwork.sendToServer(new GovShopPacket(0, 0, idx)))
                    .bounds(x + cw - 38, y + 3, 34, 18).build());
            b.active = creative || money >= price(i);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xE0050A12);
        for (int y = 0; y < height; y += 3) g.fill(0, y, width, y + 1, 0x0A40C0FF);          // scanlines
        int x0 = left(), w = panelW(), y0 = top();
        g.fill(x0 - 2, y0 - 34, x0 + w + 2, y0 - 33, 0xFF40C8FF);
        g.drawString(font, "AURORA GOVERNMENT  //  SUPPLY TERMINAL", x0, y0 - 28, 0xFF8FE4FF, false);
        String bal = "Balance: " + com.xenowarriors.economy.Money.fmt(money) + " Money" + (discount > 0 ? "   (your standing: -" + discount + "%)" : discount < 0 ? "   (your standing: +" + (-discount) + "%)" : "");
        g.drawString(font, bal, x0, y0 - 16, discount > 0 ? 0xFF7CFFB0 : discount < 0 ? 0xFFFF9A6A : 0xFFDDEEFF, false);
        int cw = (w - 12) / COLS;
        for (int i = 0; i < GovShop.OFFERS.size(); i++) {
            int c = i % COLS, r = i / COLS;
            int x = x0 + 4 + c * (cw + 4), y = y0 + r * ROW;
            if (y + ROW > height - 24) continue;
            var o = GovShop.OFFERS.get(i);
            boolean hov = mx >= x && mx < x + cw && my >= y && my < y + ROW - 2;
            g.fill(x, y, x + cw, y + ROW - 2, hov ? 0xC0183048 : 0xB00E1A28);
            g.fill(x, y, x + 2, y + ROW - 2, 0xFF40C8FF);
            ItemStack icon = o.make().get();
            g.renderItem(icon, x + 5, y + 4);
            g.drawString(font, o.name(), x + 25, y + 3, 0xFFFFFFFF, false);
            long p = price(i);
            g.drawString(font, com.xenowarriors.economy.Money.fmt(p), x + 25, y + 13, money >= p ? 0xFF7CFFB0 : 0xFFFF6A6A, false);
            if (hov) g.renderTooltip(font, Component.literal(o.desc()), mx, my);
        }
        g.drawCenteredString(font, "All purchases are logged by the Government of Aurora.", width / 2, height - 14, 0xFF4F7088);
        super.render(g, mx, my, pt);
    }

    @Override public boolean isPauseScreen() { return false; }
}
