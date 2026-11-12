package com.xenowarriors.client.bot;

import com.xenowarriors.compat.MissionBag;
import com.xenowarriors.compat.MissionBagMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** The party's mission bag (see MissionBag): page tabs along the top, a page of 54, then your inventory. Take only. */
public class MissionBagScreen extends AbstractContainerScreen<MissionBagMenu> {
    private static final int ACCENT = 0xFFFFB040, TAB_Y = -14, TAB_H = 13, TAB_W = 40;

    public MissionBagScreen(MissionBagMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 176;
        imageHeight = MissionBagMenu.playerTop() + 76 + 6;
        inventoryLabelY = MissionBagMenu.playerTop() - 11;
    }

    @Override
    protected void init() {
        super.init();
        topPos = Math.max(topPos, Math.min(-TAB_Y + 2, height - imageHeight));
    }

    private int tabX(int i) { return 8 + i * (TAB_W + 2); }

    private boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= leftPos + x && mx < leftPos + x + w && my >= topPos + y && my < topPos + y + h;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) for (int i = 0; i < MissionBag.PAGES; i++) if (over(mx, my, tabX(i), TAB_Y, TAB_W, TAB_H)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gameMode != null) mc.gameMode.handleInventoryButtonClick(menu.containerId, i);
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1F));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int left, int top, int button) {
        if (my >= top + TAB_Y && my < top && mx >= left && mx < left + imageWidth) return false;
        return super.hasClickedOutside(mx, my, left, top, button);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        for (int i = 0; i < MissionBag.PAGES; i++) {
            boolean on = menu.page() == i, hover = over(mouseX, mouseY, tabX(i), TAB_Y, TAB_W, TAB_H);
            int tx = x + tabX(i), ty = y + TAB_Y;
            g.fill(tx, ty, tx + TAB_W, ty + TAB_H + (on ? 1 : 0), on ? 0xF0221A12 : hover ? 0xE0443828 : 0xD0181410);
            g.fill(tx, ty, tx + TAB_W, ty + 1, on ? ACCENT : 0xFF604A30);
            g.drawCenteredString(font, "Page " + (i + 1), tx + TAB_W / 2, ty + 3, on ? 0xFFFFFFFF : 0xFFB8A890);
        }
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xF0221A12);
        g.fill(x, y, x + imageWidth, y + 2, ACCENT);
        for (Slot s : menu.slots) {
            int sx = x + s.x - 1, sy = y + s.y - 1;
            g.fill(sx, sy, sx + 18, sy + 18, s.index < MissionBagMenu.ROWS * 9 ? 0xFF4A3A22 : 0xFF2A2A34);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF120E0A);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, 6, 0xFFD890, false);
        String t = "take only";
        g.drawString(font, t, imageWidth - 8 - font.width(t), 6, 0x907860, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xB0B0C0, false);
    }
}
