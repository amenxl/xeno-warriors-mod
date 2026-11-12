package com.xenowarriors.client;

import com.xenowarriors.network.BoostBagPacket;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.statboost.BoostBag;
import com.xenowarriors.statboost.BoostStat;
import com.xenowarriors.statboost.BoostTier;
import com.xenowarriors.statboost.StatBoosts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The Boost Bag: every Stat Boost - a column per stat, a row per tier (+1 to +100) - with how many you have.
 * Left-click takes a stack out, right-click uses one, shift + right-click uses all of that kind.
 */
public class BoostBagScreen extends Screen {
    private static final int CELL = 24;
    private int[] counts;

    public BoostBagScreen(int[] counts) { super(Component.literal("Boost Bag")); this.counts = counts; }

    /** The server's answer: open it, or refresh it if it's open. */
    public static void show(int[] counts) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof BoostBagScreen s) s.counts = counts;
        else mc.setScreen(new BoostBagScreen(counts));
    }

    private int cols() { return BoostStat.values().length; }
    private int rows() { return BoostTier.values().length; }
    private int x0() { return (width - cols() * CELL) / 2; }
    private int y0() { return (height - rows() * CELL) / 2; }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Put my boosts in"), b -> ModNetwork.sendToServer(new BoostBagPacket(BoostBagPacket.DEPOSIT, -1, 0, new int[0])))
                .bounds(width / 2 - 104, y0() + rows() * CELL + 20, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(width / 2 + 4, y0() + rows() * CELL + 20, 100, 20).build());
    }

    private int kindAt(double mx, double my) {
        int c = (int) Math.floor((mx - x0()) / CELL), r = (int) Math.floor((my - y0()) / CELL);
        if (mx < x0() || my < y0() || c < 0 || c >= cols() || r < 0 || r >= rows()) return -1;
        return BoostBag.index(BoostStat.values()[c], BoostTier.values()[r]);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int k = kindAt(mx, my);
        if (k >= 0 && k < counts.length && counts[k] > 0) {
            boolean shift = hasShiftDown();
            if (button == 0) ModNetwork.sendToServer(new BoostBagPacket(BoostBagPacket.TAKE, k, shift ? 1 : 64, new int[0]));
            else if (button == 1) ModNetwork.sendToServer(new BoostBagPacket(BoostBagPacket.USE, k, shift ? 2 : 1, new int[0]));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x0 = x0(), y0 = y0();
        long total = 0;
        for (int c : counts) total += c;
        g.drawCenteredString(font, "BOOST BAG", width / 2, y0 - 30, 0xFFD24A);
        g.drawCenteredString(font, String.format("%,d", total) + " boosts inside", width / 2, y0 - 18, 0xAAAAAA);
        g.fill(x0 - 4, y0 - 4, x0 + cols() * CELL + 4, y0 + rows() * CELL + 4, 0xE0101018);
        int hover = kindAt(mx, my);
        for (int c = 0; c < cols(); c++) for (int r = 0; r < rows(); r++) {
            BoostStat s = BoostStat.values()[c];
            BoostTier t = BoostTier.values()[r];
            int k = BoostBag.index(s, t), n = k < counts.length ? counts[k] : 0;
            int x = x0 + c * CELL, y = y0 + r * CELL;
            g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, k == hover ? 0xFF404058 : n > 0 ? 0xFF242436 : 0xFF16161E);
            ItemStack st = StatBoosts.stack(s, t);
            g.renderItem(st, x + 4, y + 3);
            if (n == 0) { g.pose().pushPose(); g.pose().translate(0, 0, 250); g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0xB0101018); g.pose().popPose(); }   // (none: greyed out, over the icon)
            else {
                String num = n > 999 ? (n / 1000) + "k" : String.valueOf(n);
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.drawString(font, num, x + CELL - 2 - font.width(num), y + CELL - 9, 0xFFFFFF, true);
                g.pose().popPose();
            }
        }
        g.drawCenteredString(font, "Left-click: take a stack   Right-click: use one   Shift + right-click: use them all", width / 2, y0 + rows() * CELL + 7, 0x8899AA);
        super.render(g, mx, my, pt);
        if (hover >= 0) {
            BoostStat s = BoostBag.statOf(hover);
            BoostTier t = BoostBag.tierOf(hover);
            List<Component> tip = new ArrayList<>();
            tip.add(StatBoosts.stack(s, t).getHoverName());
            tip.add(Component.literal("In the bag: " + String.format("%,d", hover < counts.length ? counts[hover] : 0)).withStyle(net.minecraft.ChatFormatting.GOLD));
            g.renderComponentTooltip(font, tip, mx, my);
        }
    }

    @Override public boolean isPauseScreen() { return false; }

    /** (Tests) what it shows. */
    public int[] counts() { return counts; }
}
