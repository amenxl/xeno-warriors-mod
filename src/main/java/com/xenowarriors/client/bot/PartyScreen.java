package com.xenowarriors.client.bot;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.party.PartyClient;
import com.xenowarriors.network.BotActionPacket;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.PartySyncPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/** The party menu: everyone in the party, your bots' settings (mode, their menu), and adding bots. */
public class PartyScreen extends Screen {
    private static final int W = 260, ROW = 26;
    private final List<PartySyncPacket.Member> rows = new ArrayList<>();

    public PartyScreen() { super(Component.literal("Party")); }

    private int x0() { return (width - W) / 2; }
    private int y0() { return Math.max(20, (height - (40 + Math.max(1, rows.size()) * ROW + 30)) / 2); }

    private PartyBotEntity myBot(PartySyncPacket.Member m) {
        if (!m.bot() || minecraft.level == null) return null;
        for (Entity e : minecraft.level.entitiesForRendering())
            if (e instanceof PartyBotEntity b && b.getUUID().equals(m.id()) && minecraft.player.getUUID().equals(b.ownerId())) return b;
        return null;
    }

    @Override
    protected void init() {
        rows.clear();
        rows.addAll(PartyClient.members());
        int y = y0() + 30;
        for (PartySyncPacket.Member m : rows) {
            PartyBotEntity b = myBot(m);
            if (b != null) {
                int id = b.getId();
                addRenderableWidget(Button.builder(Component.literal(PartyBotEntity.MODES[b.mode()]), btn -> {
                    int next = (b.mode() + 1) % 3;
                    ModNetwork.sendToServer(new BotActionPacket(id, PartyBots.ACT_MODE, next));
                    btn.setMessage(Component.literal(PartyBotEntity.MODES[next]));
                }).bounds(x0() + W - 118, y + 2, 64, 18).build());
                addRenderableWidget(Button.builder(Component.literal("Menu"), btn -> ModNetwork.sendToServer(new BotActionPacket(id, PartyBots.ACT_MENU, 0)))
                        .bounds(x0() + W - 50, y + 2, 44, 18).build());
            }
            y += ROW;
        }
        if (rows.isEmpty()) y += ROW;
        addRenderableWidget(Button.builder(Component.literal("Add bot teammates"), b -> { minecraft.player.connection.sendCommand("xeno bot"); })
                .bounds(x0() + 8, y + 6, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(x0() + W - 68, y + 6, 60, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x = x0(), y = y0(), h = 40 + Math.max(1, rows.size()) * ROW + 30;
        g.fill(x - 2, y - 2, x + W + 2, y + h + 2, 0xFF6CC4FF);
        g.fill(x, y, x + W, y + h, 0xF0101018);
        g.drawString(font, "PARTY  (" + rows.size() + "/4)", x + 8, y + 10, 0xC8E1FF);
        int ry = y + 30;
        if (rows.isEmpty()) g.drawString(font, "Just you. Invite people or add bot teammates.", x + 8, ry + 6, 0x999999, false);
        for (PartySyncPacket.Member m : rows) {
            CharacterType t = CharacterType.values()[Math.max(0, Math.min(CharacterType.values().length - 1, m.type()))];
            g.fill(x + 4, ry, x + W - 4, ry + ROW - 3, 0x40FFFFFF & (t.getColor() | 0xFF000000));
            g.drawString(font, m.name() + (m.leader() ? " ★" : ""), x + 10, ry + 3, t.getColor());
            String sub = (m.bot() ? "BOT • " : "") + t.getDisplayName().getString() + (m.knockedOut() ? " • DOWN" : "");
            g.drawString(font, sub, x + 10, ry + 13, m.knockedOut() ? 0xFF6060 : 0xAAAAAA, false);
            ry += ROW;
        }
        super.render(g, mx, my, pt);
    }

    @Override public boolean isPauseScreen() { return false; }
}
