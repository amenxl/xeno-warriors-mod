package com.xenowarriors.client;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.PlayerStatsPacket;
import com.xenowarriors.network.PlayerStatsRequestPacket;
import com.xenowarriors.stats.Stat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** The M menu: every player on the server with their warrior, Character Level, stats, karma and health. */
public class PlayerStatsScreen extends Screen {
    private static final int ROW = 34, W = 300;
    private List<PlayerStatsPacket.Entry> players = new ArrayList<>();
    private int scroll, refresh;

    public PlayerStatsScreen() {
        super(Component.literal("Warriors"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new PlayerStatsScreen());
        ModNetwork.sendToServer(new PlayerStatsRequestPacket());
    }

    public static void receive(List<PlayerStatsPacket.Entry> list) {
        if (Minecraft.getInstance().screen instanceof PlayerStatsScreen s) s.players = list;
    }

    @Override
    public void tick() {
        if (++refresh % 40 == 0) ModNetwork.sendToServer(new PlayerStatsRequestPacket());
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ModKeyMappings.STATS_MENU.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        int visible = Math.max(1, (height - 70) / ROW);
        scroll = Math.max(0, Math.min(Math.max(0, players.size() - visible), scroll - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = (width - W) / 2, top = 24;
        g.drawCenteredString(font, "Warriors on this server", width / 2, 10, 0xFFFFFF);
        if (players.isEmpty()) {
            g.drawCenteredString(font, "Loading...", width / 2, top + 20, 0xAAAAAA);
            return;
        }
        int visible = Math.max(1, (height - 70) / ROW);
        for (int i = 0; i < visible && i + scroll < players.size(); i++) {
            PlayerStatsPacket.Entry e = players.get(i + scroll);
            CharacterType type = CharacterType.NONE;
            for (CharacterType t : CharacterType.values()) if (t.getId().equals(e.type())) type = t;
            int color = type.getColor() | 0xFF000000;
            int y = top + i * ROW;
            g.fill(x, y, x + W, y + ROW - 4, e.self() ? 0xD0202030 : 0xC0101018);
            g.fill(x, y, x + 2, y + ROW - 4, color);
            String who = e.name() + (e.self() ? " (you)" : e.party() ? " (party)" : "");
            g.drawString(font, who, x + 7, y + 4, 0xFFFFFF);
            Component warrior = type == CharacterType.NONE ? Component.literal("No warrior") : type.getDisplayName();
            g.drawString(font, warrior, x + 7, y + 16, color);
            String lv = type == CharacterType.NONE ? "-" : "Lv " + Math.max(1, e.level());
            g.drawString(font, lv, x + 100, y + 4, 0xFFD966);
            g.drawString(font, "❤ " + Math.round(e.health()) + "/" + Math.round(e.maxHealth()), x + 100, y + 16, 0xFF6060);
            bar(g, x + 160, y + 3, "SPD", e.speed(), 0x55DDFF);
            bar(g, x + 160, y + 12, "ATK", e.attack(), 0xFF7744);
            bar(g, x + 160, y + 21, "DEF", e.defense(), 0x88DD66);
            g.drawString(font, "Karma " + Math.round(e.karma()), x + 236, y + 4, 0xC77DFF);
        }
        g.drawCenteredString(font, "Press M or Esc to close", width / 2, height - 16, 0x777777);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void bar(GuiGraphics g, int x, int y, String label, int level, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.7F, 0.7F, 1);
        g.drawString(font, label + " " + level, 0, 0, 0xDDDDDD);
        g.pose().popPose();
        int bx = x + 34, w = 36;
        g.fill(bx, y + 1, bx + w, y + 5, 0xFF2A2A34);
        g.fill(bx, y + 1, bx + Math.round(w * Math.min(1F, level / (float) Stat.MAX_LEVEL)), y + 5, 0xFF000000 | color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
