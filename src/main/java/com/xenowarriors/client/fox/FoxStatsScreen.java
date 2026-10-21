package com.xenowarriors.client.fox;

import com.xenowarriors.fox.FoxLevels;
import com.xenowarriors.network.FoxStatsPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

/** A card for one of Mikiko's foxes: a live picture of it, its name, level and XP, and its stats. */
public class FoxStatsScreen extends Screen {
    private static final int W = 230, H = 178;
    private static final int ORANGE = 0xFFFF8A2A, SNOW = 0xFFDDE8F2;
    private final FoxStatsPacket s;
    private int skin;

    private FoxStatsScreen(FoxStatsPacket stats) {
        super(Component.literal(stats.name()));
        this.s = stats;
        this.skin = stats.skin();
    }

    public static void open(FoxStatsPacket stats) {
        Minecraft.getInstance().setScreen(new FoxStatsScreen(stats));
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    @Override
    protected void init() {
        int x = left(), y = top();
        addRenderableWidget(Button.builder(Component.literal("Bag"), b ->
                com.xenowarriors.network.ModNetwork.sendToServer(new com.xenowarriors.network.OpenFoxBagPacket(s.fox())))
                .bounds(x + 8, y + H - 26, 76, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Rename"),
                b -> minecraft.setScreen(new FoxNameScreen(s.fox(), s.name(), false))).bounds(x + 92, y + H - 26, 64, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose()).bounds(x + 160, y + H - 26, 62, 20).build());
        // skin picker: < Skin >
        addRenderableWidget(Button.builder(Component.literal("<"), b -> pickSkin(-1)).bounds(x + 92, y + 124, 16, 16).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> pickSkin(1)).bounds(x + W - 24, y + 124, 16, 16).build());
    }

    private void pickSkin(int dir) {
        int n = com.xenowarriors.fox.FoxSkins.ALL.length;
        skin = Math.floorMod(skin + dir, n);
        if (com.xenowarriors.fox.FoxSkins.unlocked(skin, s.level())) {
            com.xenowarriors.network.ModNetwork.sendToServer(new com.xenowarriors.network.SetFoxSkinPacket(s.fox(), skin));
            FoxClientFx.setSkin(s.entityId(), skin);
            FoxClientFx.preview(s.entityId(), -1);
        } else {
            FoxClientFx.preview(s.entityId(), skin);   // locked: just a peek
        }
    }

    @Override
    public void removed() {
        FoxClientFx.preview(s.entityId(), -1);
        super.removed();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = left(), y = top();
        int accent = s.snow() ? SNOW : ORANGE;
        g.fill(x, y, x + W, y + H, 0xE0101018);
        g.fill(x, y, x + W, y + 2, accent);
        g.fill(x + 6, y + 8, x + 84, y + H - 32, 0x18FFFFFF);

        // the fox itself, looking at your mouse
        if (minecraft.level != null && minecraft.level.getEntity(s.entityId()) instanceof LivingEntity fox) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + 45, y + 100, 42,
                    (float) (x + 45 - mouseX), (float) (y + 70 - mouseY), fox);
        }

        int tx = x + 92;
        g.drawString(font, s.name(), tx, y + 10, 0xFFFFFF);
        String lv = "Lv " + s.level() + (s.level() >= FoxLevels.MAX ? " (MAX)" : "");
        g.drawString(font, lv, x + W - 8 - font.width(lv), y + 10, accent);

        // XP bar
        int barY = y + 24, barW = W - 100;
        g.fill(tx, barY, tx + barW, barY + 5, 0xFF2A2A34);
        float frac = s.xpNeeded() <= 0 ? 1F : Math.min(1F, s.xp() / s.xpNeeded());
        g.fill(tx, barY, tx + Math.round(barW * frac), barY + 5, accent);
        String xpText = s.xpNeeded() <= 0 ? "Max level" : (int) s.xp() + " / " + s.xpNeeded() + " XP";
        g.drawString(font, xpText, tx, barY + 8, 0xFFA0A0B0);

        int ly = y + 48;
        row(g, tx, ly, "❤ Health", String.format("%.0f / %.0f", s.health(), s.maxHealth()), 0xFFFF5A5A);
        row(g, tx, ly + 12, "⚔ Bite", String.format("%.1f", s.attack()), 0xFFFFB04A);
        row(g, tx, ly + 24, "» Speed", Math.round(s.speed() / 0.3F * 100F) + "%", 0xFF5CE1E6);
        row(g, tx, ly + 36, "☠ Defeated", String.valueOf(s.kills()), 0xFFB0A0FF);
        if (s.days() >= 0) row(g, tx, ly + 48, "☀ Together", s.days() == 1 ? "1 day" : s.days() + " days", 0xFFFFE070);
        row(g, tx, ly + 60, "Bag", com.xenowarriors.fox.FoxSkins.bagSlots(s.level()) + " slots", 0xFFC0A080);

        var sk = com.xenowarriors.fox.FoxSkins.get(skin);
        boolean unlocked = com.xenowarriors.fox.FoxSkins.unlocked(skin, s.level());
        int cx = (x + 108 + x + W - 24) / 2;
        g.drawCenteredString(font, "Skin: " + sk.name(), cx, y + 125, unlocked ? 0xFFFFFF : 0x808088);
        g.drawCenteredString(font, unlocked ? "Wearing" : "Unlocks at Lv " + sk.level(), cx, y + 137,
                unlocked ? 0x7CD65A : 0xFF6A5A);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void row(GuiGraphics g, int x, int y, String label, String value, int color) {
        g.drawString(font, label, x, y, color);
        g.drawString(font, value, left() + W - 8 - font.width(value), y, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
