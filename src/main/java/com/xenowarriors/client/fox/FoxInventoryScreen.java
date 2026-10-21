package com.xenowarriors.client.fox;

import com.xenowarriors.fox.FoxMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** The fox's bag: armor slots on the left, its storage, then your inventory. */
public class FoxInventoryScreen extends AbstractContainerScreen<FoxMenu> {
    private static final int ACCENT = 0xFFFF8A2A;

    public FoxInventoryScreen(FoxMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 200;
        imageHeight = 18 + Math.max(menu.rows, FoxMenu.GEAR) * 18 + 14 + 76 + 6;
        inventoryLabelX = 30;
        inventoryLabelY = 18 + Math.max(menu.rows, FoxMenu.GEAR) * 18 + 3;
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
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xF0181820);
        g.fill(x, y, x + imageWidth, y + 2, ACCENT);
        g.fill(x + 1, y + imageHeight - 1, x + imageWidth - 1, y + imageHeight, 0xFF0A0A10);
        for (Slot s : menu.slots) {
            int sx = x + s.x - 1, sy = y + s.y - 1;
            boolean foxSlot = s.index < FoxMenu.GEAR + menu.rows * 9;
            g.fill(sx, sy, sx + 18, sy + 18, s.index < FoxMenu.GEAR ? 0xFF4A3424 : foxSlot ? 0xFF3A2A22 : 0xFF2A2A34);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF101016);
        }
        // a sword icon hint on the empty mouth slot
        Slot weapon = menu.slots.get(2);
        if (!weapon.hasItem()) g.drawCenteredString(font, "\u2694", x + weapon.x + 8, y + weapon.y + 4, 0xFF5A4A40);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, 6, 0xFFB060, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xA0A0B0, false);
    }
}
