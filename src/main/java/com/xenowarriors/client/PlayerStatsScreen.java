package com.xenowarriors.client;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.PlayerStatsPacket;
import com.xenowarriors.network.PlayerStatsRequestPacket;
import com.xenowarriors.stats.Stat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The M menu. "Warriors" lists every player on the server (warrior, level, health, karma, stats); "My Warrior"
 * shows your own progress in detail, with Mikiko's button for entering or leaving the Hekublade.
 */
public class PlayerStatsScreen extends Screen {
    private static final int ROW = 34, W = 300;
    private static int tab;
    private List<PlayerStatsPacket.Entry> players = new ArrayList<>();
    private PlayerStatsPacket.Me me;
    private int scroll, refresh;
    private Button tabPlayers, tabMe, realm, plea;

    public PlayerStatsScreen() {
        super(Component.literal("Warriors"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new PlayerStatsScreen());
        ModNetwork.sendToServer(new PlayerStatsRequestPacket());
    }

    public static void receive(List<PlayerStatsPacket.Entry> list, PlayerStatsPacket.Me me) {
        if (Minecraft.getInstance().screen instanceof PlayerStatsScreen s) {
            s.players = list;
            s.me = me;
            s.updateButtons();
        }
    }

    @Override
    protected void init() {
        int x = (width - W) / 2;
        tabPlayers = addRenderableWidget(Button.builder(Component.literal("Warriors"), b -> setTab(0)).bounds(x, 6, 70, 16).build());
        tabMe = addRenderableWidget(Button.builder(Component.literal("My Warrior"), b -> setTab(1)).bounds(x + 74, 6, 70, 16).build());
        realm = addRenderableWidget(Button.builder(Component.literal("Enter the Hekublade"), b -> {
            if (minecraft.player != null) minecraft.player.connection.sendCommand("xeno blade realm");
            onClose();
        }).bounds(width / 2 - 75, height - 52, 150, 20).build());
        plea = addRenderableWidget(Button.builder(Component.literal("Ask to be freed"), b -> {
            if (minecraft.player != null) minecraft.player.connection.sendCommand("xeno blade plea");
        }).bounds(width / 2 - 75, height - 52, 150, 20).build());
        updateButtons();
    }

    private void setTab(int t) {
        tab = t;
        scroll = 0;
        updateButtons();
    }

    private void updateButtons() {
        if (realm == null) return;
        tabPlayers.active = tab != 0;
        tabMe.active = tab != 1;
        boolean mikiko = ClientCharacterData.getLocal() == CharacterType.MIKIKO;
        boolean prisoner = me != null && me.prisoner();
        realm.visible = tab == 1 && mikiko && me != null && !prisoner;
        realm.setMessage(Component.literal(me != null && me.inRealm() ? "Leave the Hekublade" : "Enter the Hekublade"));
        plea.visible = tab == 1 && prisoner;
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
        if (players.isEmpty()) {
            g.drawCenteredString(font, "Loading...", width / 2, 50, 0xAAAAAA);
        } else if (tab == 0) {
            renderPlayers(g);
        } else {
            renderMe(g);
        }
        g.drawCenteredString(font, "Press M or Esc to close", width / 2, height - 16, 0x777777);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private static CharacterType typeOf(PlayerStatsPacket.Entry e) {
        for (CharacterType t : CharacterType.values()) if (t.getId().equals(e.type())) return t;
        return CharacterType.NONE;
    }

    private void renderPlayers(GuiGraphics g) {
        int x = (width - W) / 2, top = 28;
        int visible = Math.max(1, (height - 70) / ROW);
        for (int i = 0; i < visible && i + scroll < players.size(); i++) {
            PlayerStatsPacket.Entry e = players.get(i + scroll);
            CharacterType type = typeOf(e);
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
            bar(g, x + 160, y + 3, "SPD", e.speed(), e.speed() / (float) Stat.MAX_LEVEL, 0x55DDFF);
            bar(g, x + 160, y + 12, "ATK", e.attack(), e.attack() / (float) Stat.MAX_LEVEL, 0xFF7744);
            bar(g, x + 160, y + 21, "DEF", e.defense(), e.defense() / (float) Stat.MAX_LEVEL, 0x88DD66);
            g.drawString(font, "Karma " + Math.round(e.karma()), x + 236, y + 4, 0xC77DFF);
        }
    }

    private void renderMe(GuiGraphics g) {
        PlayerStatsPacket.Entry self = players.stream().filter(PlayerStatsPacket.Entry::self).findFirst().orElse(null);
        if (self == null || me == null) return;
        CharacterType type = typeOf(self);
        int color = type.getColor() | 0xFF000000;
        int x = (width - W) / 2, y = 28;
        g.fill(x, y, x + W, height - 58, 0xC0101018);
        g.fill(x, y, x + W, y + 1, color);
        Component warrior = type == CharacterType.NONE ? Component.literal("No warrior") : type.getDisplayName();
        g.drawString(font, warrior, x + 8, y + 6, color);
        g.drawString(font, "Character Level " + Math.max(1, self.level()) + " / 150", x + 8, y + 18, 0xFFD966);
        g.drawString(font, "+" + self.level() / 10 + " bonus hearts  (next at Lv " + Math.min(150, (self.level() / 10 + 1) * 10) + ")",
                x + 150, y + 18, 0xFF8080);
        int by = y + 34;
        statRow(g, x + 8, by, "Speed", self.speed(), me.speedXp(), 0x55DDFF);
        statRow(g, x + 8, by + 14, "Attack", self.attack(), me.attackXp(), 0xFF7744);
        statRow(g, x + 8, by + 28, "Defense", self.defense(), me.defenseXp(), 0x88DD66);
        int ly = by + 48;
        line(g, x + 8, ly, "Karma", Math.round(self.karma()) + " / 100", 0xC77DFF);
        line(g, x + 8, ly + 12, "Health", Math.round(self.health()) + " / " + Math.round(self.maxHealth()), 0xFF6060);
        line(g, x + 8, ly + 24, "Karma Sensing", me.senseRange() + " blocks, " + me.senseSeconds() + "s  ("
                + Math.min(me.senseUses(), 100) + "/100 practice)", 0xB45CFF);
        if (type == CharacterType.MIKIKO) {
            line(g, x + 8, ly + 36, "Foxes", String.valueOf(me.pets()), 0xFFA040);
            line(g, x + 8, ly + 48, "Wind flight", self.level() >= 50 ? "Unlocked (" + me.flightMinutes() + " min flown)"
                    : "Unlocks at Character Level 50", 0xDDEEFF);
            line(g, x + 8, ly + 60, "Sealed in the Hekublade", String.valueOf(me.sealed()), 0xFF5A3A);
        } else if (type == CharacterType.RHALIX) {
            line(g, x + 8, ly + 36, "Winged companions", String.valueOf(me.pets()), 0xC77DFF);
        }
        if (me.prisoner()) {
            g.drawCenteredString(font, "You're sealed inside a Hekublade.", width / 2, height - 70, 0xFF5A3A);
        }
    }

    private void statRow(GuiGraphics g, int x, int y, String name, int level, float xp, int color) {
        g.drawString(font, name, x, y, 0xDDDDDD);
        g.drawString(font, "Lv " + level, x + 52, y, 0xFFFFFF);
        int bx = x + 90, w = 150;
        g.fill(bx, y + 2, bx + w, y + 7, 0xFF2A2A34);
        g.fill(bx, y + 2, bx + Math.round(w * (level >= Stat.MAX_LEVEL ? 1F : xp)), y + 7, 0xFF000000 | color);
        g.drawString(font, level >= Stat.MAX_LEVEL ? "MAX" : Math.round(xp * 100) + "%", bx + w + 6, y, 0x999999);
    }

    private void line(GuiGraphics g, int x, int y, String label, String value, int color) {
        g.drawString(font, label + ":", x, y, 0xAAAAAA);
        g.drawString(font, value, x + 130, y, color);
    }

    private void bar(GuiGraphics g, int x, int y, String label, int level, float fill, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.7F, 0.7F, 1);
        g.drawString(font, label + " " + level, 0, 0, 0xDDDDDD);
        g.pose().popPose();
        int bx = x + 34, w = 36;
        g.fill(bx, y + 1, bx + w, y + 5, 0xFF2A2A34);
        g.fill(bx, y + 1, bx + Math.round(w * Math.min(1F, fill)), y + 5, 0xFF000000 | color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
