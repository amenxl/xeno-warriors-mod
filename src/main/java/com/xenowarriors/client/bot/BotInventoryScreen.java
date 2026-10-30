package com.xenowarriors.client.bot;

import com.xenowarriors.bot.BotMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** A bot teammate's things: armour, offhand and hand on the left, its bag, then your inventory. */
public class BotInventoryScreen extends AbstractContainerScreen<BotMenu> {
    private static final int ACCENT = 0xFF40D0FF;

    public BotInventoryScreen(BotMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 200;
        imageHeight = BotMenu.playerTop() + 76 + 6;
        inventoryLabelX = 30;
        inventoryLabelY = BotMenu.playerTop() - 11;
        titleLabelX = 8;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xF0141A22);
        g.fill(x, y, x + imageWidth, y + 2, ACCENT);
        for (Slot s : menu.slots) {
            int sx = x + s.x - 1, sy = y + s.y - 1;
            boolean gear = s.index < BotMenu.GEAR, bag = s.index < BotMenu.GEAR + BotMenu.ROWS * 9;
            g.fill(sx, sy, sx + 18, sy + 18, gear ? 0xFF24404A : bag ? 0xFF22323A : 0xFF2A2A34);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF0E1218);
        }
        Slot hand = menu.slots.get(5);
        if (!hand.hasItem()) g.drawCenteredString(font, "⚔", x + hand.x + 8, y + hand.y + 4, 0xFF406070);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, 6, 0x9AE6FF, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xA0A0B0, false);
    }
}
