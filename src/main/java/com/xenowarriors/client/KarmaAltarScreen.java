package com.xenowarriors.client;

import com.xenowarriors.altar.GearUpgrades;
import com.xenowarriors.altar.KarmaAltarMenu;
import com.xenowarriors.mikiko.BladeUpgrades;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The altar: blade slot, material slot, both edges' levels, and what the next upgrade costs. */
public class KarmaAltarScreen extends AbstractContainerScreen<KarmaAltarMenu> {
    private static final int SKY = 0xFF7FD8FF, DARK = 0xFFC07BFF;
    private Button upgrade;

    public KarmaAltarScreen(KarmaAltarMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = 73;
    }

    @Override
    protected void init() {
        super.init();
        upgrade = addRenderableWidget(Button.builder(Component.literal("Upgrade"), b -> {
            if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
        }).bounds(leftPos + 108, topPos + 34, 60, 18).build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        int lvl = menu.currentLevel();
        upgrade.active = lvl >= 0 && lvl < menu.maxLevel() && menu.material().getCount() >= menu.materialsNeeded(lvl);
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
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xF0141220);
        g.fill(x, y, x + imageWidth / 2, y + 2, SKY);
        g.fill(x + imageWidth / 2, y, x + imageWidth, y + 2, DARK);
        for (Slot s : menu.slots) {
            int sx = x + s.x - 1, sy = y + s.y - 1;
            int frame = s.index == KarmaAltarMenu.BLADE ? 0xFF8A5A2A : s.index == KarmaAltarMenu.MATERIAL ? 0xFF4A3A6A : 0xFF2A2A34;
            g.fill(sx, sy, sx + 18, sy + 18, frame);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF0E0C16);
        }
        Slot blade = menu.slots.get(KarmaAltarMenu.BLADE), mat = menu.slots.get(KarmaAltarMenu.MATERIAL);
        if (!blade.hasItem()) g.drawCenteredString(font, "⛏", x + blade.x + 8, y + blade.y + 4, 0xFF6A5A40);
        if (!mat.hasItem()) g.drawCenteredString(font, "✦", x + mat.x + 8, y + mat.y + 4, 0xFF5A4A70);
        g.drawCenteredString(font, "+", x + 71, y + 39, 0xFF8080A0);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, 0xFFE0C0FF, false);
        ItemStack it = menu.item();
        String target = menu.targetKind();
        if ("gear".equals(target)) {
            boolean sk = "sky".equals(GearUpgrades.path(it));
            g.drawString(font, (sk ? "Sky Forged " : "Dark Forged ") + pips(GearUpgrades.level(it), GearUpgrades.MAX), 8, 18, sk ? SKY : DARK, false);
        } else if (menu.mikiko()) {
            // his blade's edges, read from him (no need to put the blade in)
            g.drawString(font, "Sky Edge " + pips(menu.bladeSky(), BladeUpgrades.MAX), 8, 18, SKY, false);
            g.drawString(font, "Dark Edge " + pips(menu.bladeDark(), BladeUpgrades.MAX), 92, 18, DARK, false);
        } else g.drawString(font, "Forge Sky / Corrupted gear", 8, 18, 0xFFB0A0D0, false);
        int lvl = menu.currentLevel();
        String line;
        int color = 0xA0A0B0;
        String mat = KarmaAltarMenu.path(menu.material());
        if (target == null) line = it.isEmpty() ? "Put in Sky or Corrupted gear" : "That can't be upgraded here";
        else if (mat == null) line = "gear".equals(target) ? ("sky".equals(GearUpgrades.path(it)) ? "Add Sky Crystals" : "Add Corrupted Shards")
                : "Add Sky Crystals or Shards for the blade";
        else if (lvl < 0) line = "sky".equals(GearUpgrades.path(it)) ? "Sky gear takes Sky Crystals" : "Corrupted gear takes Shards";
        else if (lvl >= menu.maxLevel()) line = "gear".equals(target) ? "This is fully forged" : "This edge is maxed";
        else {
            line = ("blade".equals(target) ? "Hekublade " : "") + "Lv " + (lvl + 1) + ": " + menu.materialsNeeded(lvl) + "x, " + menu.karmaNeeded(lvl) + " Karma";
            color = "sky".equals(mat) ? SKY : DARK;
        }
        g.drawString(font, line, 8, 58, color, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xA0A0B0, false);
    }

    private static String pips(int n, int max) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < max; i++) s.append(i < n ? '◆' : '◇');
        return s.toString();
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        super.renderTooltip(g, mouseX, mouseY);
        if (upgrade.isHovered()) {
            String p = KarmaAltarMenu.path(menu.material());
            boolean gear = "gear".equals(menu.targetKind());
            Component tip = gear ? Component.literal("+" + (int) GearUpgrades.DAMAGE + " damage (weapons), +" + Math.round(GearUpgrades.DIG * 100)
                    + "% digging (tools), +" + Math.round(GearUpgrades.DURABILITY * 100) + "% durability, full repair")
                    : "sky".equals(p) ? Component.literal("Sky Edge: more damage, much more vs flying targets; hits lift them")
                    : "dark".equals(p) ? Component.literal("Dark Edge: more damage and life drain; level 3 withers")
                    : Component.literal("Needs a material");
            g.renderTooltip(font, tip, mouseX, mouseY);
        }
    }
}
