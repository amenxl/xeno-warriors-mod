package com.xenowarriors.client.bot;

import com.xenowarriors.bot.BotInventory;
import com.xenowarriors.bot.BotMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * A bot teammate's things: armour, offhand and hand on the left, its bag - or one of its three extra tabs (only a bot
 * has them) - then your inventory. Tab buttons along the top of the bag; "Sort" tidies its things now.
 */
public class BotInventoryScreen extends AbstractContainerScreen<BotMenu> {
    private static final int ACCENT = 0xFF40D0FF;
    private static final String[] TABS = {"Bag", "Extra 1", "Extra 2", "Extra 3"};
    private static final int TAB_Y = -14, TAB_H = 13, SORT_Y = 3, SORT_H = 12;

    public BotInventoryScreen(BotMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 200;
        imageHeight = BotMenu.playerTop() + 76 + 6;
        inventoryLabelX = 30;
        inventoryLabelY = BotMenu.playerTop() - 11;
        titleLabelX = 8;
    }

    /** Tab button i: x offset and width (relative to the screen's left). */
    @Override
    protected void init() {
        super.init();
        topPos = Math.max(topPos, Math.min(-TAB_Y + 2, height - imageHeight));   // (room above it for the tab row on a small window)
    }

    private int tabX(int i) { return 30 + i * 42; }
    private static int tabW(int i) { return i == 0 ? 28 : 40; }
    private int sortX() { return imageWidth - 34; }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (menu.hasTabs() && over(mouseX, mouseY, sortX(), SORT_Y, 30, SORT_H))
            g.renderTooltip(font, Component.literal("Tidy its things now (it also does this by itself)"), mouseX, mouseY);
    }

    private boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= leftPos + x && mx < leftPos + x + w && my >= topPos + y && my < topPos + y + h;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && menu.hasTabs()) {
            for (int i = 0; i <= BotInventory.TABS; i++)
                if (over(mx, my, tabX(i), TAB_Y, tabW(i), TAB_H)) { press(i); return true; }
            if (over(mx, my, sortX(), SORT_Y, 30, SORT_H)) { press(BotMenu.SORT); return true; }
        }
        return super.mouseClicked(mx, my, button);
    }

    /** A tab or the sort button (the server does it: vanilla's menu-button packet). */
    public void press(int id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode != null) mc.gameMode.handleInventoryButtonClick(menu.containerId, id);
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1F));
    }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int left, int top, int button) {
        if (menu.hasTabs() && my >= top + TAB_Y && my < top && mx >= left && mx < left + imageWidth) return false;   // (the tab row isn't outside: no dropping things there)
        return super.hasClickedOutside(mx, my, left, top, button);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        if (menu.hasTabs()) {
            for (int i = 0; i <= BotInventory.TABS; i++) {
                boolean on = menu.tab() == i, hover = over(mouseX, mouseY, tabX(i), TAB_Y, tabW(i), TAB_H);
                int tx = x + tabX(i), ty = y + TAB_Y;
                g.fill(tx, ty, tx + tabW(i), ty + TAB_H + (on ? 1 : 0), on ? 0xF0141A22 : hover ? 0xE0283844 : 0xD0101418);
                g.fill(tx, ty, tx + tabW(i), ty + 1, on ? ACCENT : 0xFF305060);
                g.drawCenteredString(font, TABS[i], tx + tabW(i) / 2, ty + 3, on ? 0xFFFFFFFF : 0xFF90A8B8);
            }
        }
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xF0141A22);
        g.fill(x, y, x + imageWidth, y + 2, ACCENT);
        for (Slot s : menu.slots) {
            if (!s.isActive()) continue;
            int sx = x + s.x - 1, sy = y + s.y - 1;
            boolean gear = s.index < BotMenu.GEAR, bag = s.index < BotMenu.GEAR + BotMenu.ROWS * 9;
            int edge = gear ? 0xFF24404A : bag ? (menu.tab() == 0 ? 0xFF22323A : 0xFF2A3A2A) : 0xFF2A2A34;
            g.fill(sx, sy, sx + 18, sy + 18, edge);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF0E1218);
        }
        if (menu.hasTabs()) {
            int sx = x + sortX(), sy = y + SORT_Y;
            boolean hover = over(mouseX, mouseY, sortX(), SORT_Y, 30, SORT_H);
            g.fill(sx, sy, sx + 30, sy + SORT_H, hover ? 0xE0305A40 : 0xD0183020);
            g.fill(sx, sy, sx + 30, sy + 1, 0xFF60D090);
            g.drawCenteredString(font, "Sort", sx + 15, sy + 2, 0xFFC8F0D0);
        }
        Slot hand = menu.slots.get(5);
        if (!hand.hasItem()) g.drawCenteredString(font, "⚔", x + hand.x + 8, y + hand.y + 4, 0xFF406070);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, 6, 0x9AE6FF, false);
        if (menu.tab() > 0) g.drawString(font, "Extra tab " + menu.tab() + " (bot only)", 30, 18 + 3 * 18 + 5, 0x80A0B0, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xA0A0B0, false);
    }
}
