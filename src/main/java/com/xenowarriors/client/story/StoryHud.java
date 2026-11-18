package com.xenowarriors.client.story;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** On a story mission (or a side mission): the objective, top-left. */
public class StoryHud implements IGuiOverlay {
    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.screen != null) return;
        String obj = StoryClient.state.getString("obj"), side = StoryClient.state.getString("sideObj");
        int y = 4;
        for (String s : new String[]{obj, side}) {
            if (s == null || s.isEmpty()) continue;
            int w = Math.min(width / 2, mc.font.width(s) + 10);
            g.fill(4, y, 4 + w, y + 13, 0x90101018);
            g.fill(4, y, 6, y + 13, 0xFFE8B030);
            g.drawString(mc.font, mc.font.plainSubstrByWidth(s, w - 8), 9, y + 3, 0xFFF0E0B0, true);
            y += 16;
        }
    }
}
