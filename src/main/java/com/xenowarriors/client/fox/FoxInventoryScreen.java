package com.xenowarriors.client.fox;

import com.xenowarriors.fox.FoxMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** The fox's bag: armor slots on the left, its storage, then your inventory. */
public class FoxInventoryScreen extends AbstractContainerScreen<FoxMenu> {
    private static final int ACCENT = 0xFFFF8A2A;

    public FoxInventoryScreen(FoxMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 200;
        imageHeight = 18 + Math.max(menu.rows, 2) * 18 + 14 + 76 + 6;
        inventoryLabelX = 30;
        inventoryLabelY = 18 + Math.max(menu.rows, 2) * 18 + 3;
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
            boolean foxSlot = s.index < 2 + menu.rows * 9;
            g.fill(sx, sy, sx + 18, sy + 18, s.index < 2 ? 0xFF4A3424 : foxSlot ? 0xFF3A2A22 : 0xFF2A2A34);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF101016);
        }
        // a little picture of the fox under its armor slots
        if (menu.fox() != null && menu.rows >= 2) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + 16, y + 18 + menu.rows * 18 - 2, 14,
                    (float) (x + 16 - mouseX), (float) (y + 40 - mouseY), menu.fox());
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, 6, 0xFFB060, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xA0A0B0, false);
    }
}
