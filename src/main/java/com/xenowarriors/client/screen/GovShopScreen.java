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

/**
 * The Government Supply Terminal: a cold blue kiosk screen listing Government gear for Money. Scrolls (mouse wheel)
 * when the stock doesn't fit. Gear that needs more standing (the stronger Karma Scanners) shows what it takes.
 */
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
    /** Season 2/3 stock this world hasn't unlocked: shown as "Locked", not by name. */
    private static boolean locked(int i) { return GovShop.OFFERS.get(i).season() > com.xenowarriors.client.ClientSkillState.season(); }
    /** Needs more Government standing than you have. */
    private boolean restricted(int i) {
        boolean creative = minecraft != null && minecraft.player != null && minecraft.player.isCreative();
        return !creative && !GovShop.tierOf(discount).atLeast(GovShop.OFFERS.get(i).need());
    }
    /** Rows that fit on screen, and how many rows there are. */
    private int visibleRows() { return Math.max(1, (height - 24 - top()) / ROW); }
    private int totalRows() { return (GovShop.OFFERS.size() + COLS - 1) / COLS; }
    private int maxScroll() { return Math.max(0, totalRows() - visibleRows()); }
    /** Screen row of offer i (negative or past the end = scrolled out). */
    private int rowOf(int i) { return i / COLS - scroll; }

    /** Scrolled to the end (tests). */
    public void scrollToEnd() { scroll = maxScroll(); rebuildWidgets(); }

    @Override
    protected void init() {
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        int cw = (panelW() - 12) / COLS;
        boolean creative = minecraft != null && minecraft.player != null && minecraft.player.isCreative();
        for (int i = 0; i < GovShop.OFFERS.size(); i++) {
            int idx = i, c = i % COLS, r = rowOf(i);
            if (r < 0 || r >= visibleRows()) continue;
            int x = left() + 4 + c * (cw + 4), y = top() + r * ROW;
            Button b = addRenderableWidget(Button.builder(Component.literal("Buy"), bt -> ModNetwork.sendToServer(new GovShopPacket(0, 0, idx)))
                    .bounds(x + cw - 38, y + 3, 34, 18).build());
            b.active = !locked(i) && !restricted(i) && (creative || money >= price(i));
        }
        if (c2Button()) addRenderableWidget(Button.builder(Component.literal("C2 Procedures"), bt -> com.xenowarriors.client.screen.C2Screen.ask())   // (your own characters: the Government's paths)
                .bounds(left() + panelW() - 100, top() - 40, 100, 18).build());
    }

    private boolean c2Button() { return com.xenowarriors.client.ClientCharacterData.you(Minecraft.getInstance().player) != null; }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int s = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(delta)));
        if (s != scroll) { scroll = s; rebuildWidgets(); }
        return true;
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
        Component tip = null;
        for (int i = 0; i < GovShop.OFFERS.size(); i++) {
            int c = i % COLS, r = rowOf(i);
            if (r < 0 || r >= visibleRows()) continue;
            int x = x0 + 4 + c * (cw + 4), y = y0 + r * ROW;
            var o = GovShop.OFFERS.get(i);
            boolean hov = mx >= x && mx < x + cw && my >= y && my < y + ROW - 2;
            g.fill(x, y, x + cw, y + ROW - 2, hov ? 0xC0183048 : 0xB00E1A28);
            g.fill(x, y, x + 2, y + ROW - 2, restricted(i) ? 0xFF000000 | o.need().color : 0xFF40C8FF);
            if (locked(i)) {
                g.drawString(font, "Locked", x + 25, y + 3, 0xFF7A8794, false);
                g.drawString(font, "Season " + o.season(), x + 25, y + 13, 0xFF5A6774, false);
                continue;
            }
            ItemStack icon = o.make().get();
            g.renderItem(icon, x + 5, y + 4);
            g.drawString(font, o.name(), x + 25, y + 3, restricted(i) ? 0xFFB0B8C0 : 0xFFFFFFFF, false);
            long p = price(i);
            if (restricted(i)) g.drawString(font, "Needs " + o.need().title + " standing", x + 25, y + 13, 0xFF000000 | o.need().color, false);
            else g.drawString(font, com.xenowarriors.economy.Money.fmt(p), x + 25, y + 13, money >= p ? 0xFF7CFFB0 : 0xFFFF6A6A, false);
            if (hov) tip = Component.literal(o.desc());
        }
        if (maxScroll() > 0) {
            String more = (scroll < maxScroll() ? "v " : "") + "Scroll for more  (" + (scroll + 1) + "/" + (maxScroll() + 1) + ")" + (scroll > 0 ? " ^" : "");
            g.drawString(font, more, x0 + w - font.width(more), y0 - 16, 0xFF8FE4FF, false);
        }
        g.drawCenteredString(font, "All purchases are logged by the Government of Aurora.", width / 2, height - 14, 0xFF4F7088);
        super.render(g, mx, my, pt);
        if (tip != null) g.renderTooltip(font, tip, mx, my);
    }

    @Override public boolean isPauseScreen() { return false; }
}
