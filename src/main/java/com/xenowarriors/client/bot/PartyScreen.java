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

/**
 * The party menu: everyone in the party, your bots' settings (mode, their menu), and adding bots. It's laid out for the
 * world's party limit (/xeno partylimit): a slot for everyone who can be in it, the empty ones with an "Add" button;
 * smaller rows for a big party, and two columns once one won't fit on the screen.
 */
public class PartyScreen extends Screen {
    private static final int CW = 260, GAP = 8;
    private final List<PartySyncPacket.Member> rows = new ArrayList<>();
    private int slots, rowH, cols, perCol;
    private boolean compact;

    public PartyScreen() { super(Component.literal("Party")); }

    private int w() { return cols * CW + (cols - 1) * GAP; }
    private int x0() { return (width - w()) / 2; }
    private int h() { return 40 + perCol * rowH + 30; }
    private int y0() { return Math.max(10, (height - h()) / 2); }
    private int slotX(int i) { return x0() + (i / perCol) * (CW + GAP); }
    private int slotY(int i) { return y0() + 30 + (i % perCol) * rowH; }

    private void layout() {
        slots = Math.max(Math.max(1, PartyClient.limit()), rows.size());
        compact = slots > 6;
        rowH = compact ? 20 : 26;
        cols = 1;
        perCol = slots;
        if (40 + slots * rowH + 30 > height - 20 && width >= 2 * CW + GAP + 8) { cols = 2; perCol = (slots + 1) / 2; }
    }

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
        layout();
        int bh = compact ? 16 : 18, by = compact ? 2 : 2;
        for (int i = 0; i < slots; i++) {
            int x = slotX(i), y = slotY(i);
            if (i < rows.size()) {
                PartyBotEntity b = myBot(rows.get(i));
                if (b == null) continue;
                int id = b.getId();
                addRenderableWidget(Button.builder(Component.literal(PartyBotEntity.MODES[b.mode()]), btn -> {
                    int next = (b.mode() + 1) % 3;
                    ModNetwork.sendToServer(new BotActionPacket(id, PartyBots.ACT_MODE, next));
                    btn.setMessage(Component.literal(PartyBotEntity.MODES[next]));
                }).bounds(x + CW - 118, y + by, 64, bh).build());
                addRenderableWidget(Button.builder(Component.literal("Menu"), btn -> ModNetwork.sendToServer(new BotActionPacket(id, PartyBots.ACT_MENU, 0)))
                        .bounds(x + CW - 50, y + by, 44, bh).build());
            } else if (rows.size() > 0 || i == 0) {                                  // an empty slot: add a bot teammate into it
                addRenderableWidget(Button.builder(Component.literal("+ Add bot"), b -> minecraft.player.connection.sendCommand("xeno bot"))
                        .bounds(x + CW - 70, y + by, 64, bh).build());
            }
        }
        int y = y0() + 30 + perCol * rowH, x = x0();
        addRenderableWidget(Button.builder(Component.literal("Add bot teammates"), b -> { minecraft.player.connection.sendCommand("xeno bot"); })
                .bounds(x + 8, y + 6, 110, 20).build());
        if (net.minecraftforge.fml.ModList.get().isLoaded("dragonminez"))       // the party's DragonMineZ mission items
            addRenderableWidget(Button.builder(Component.literal("Mission Bag"), b -> minecraft.player.connection.sendCommand("xeno missionbag"))
                    .bounds(x + 122, y + 6, 66, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(x + w() - 68, y + 6, 60, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x = x0(), y = y0(), h = h();
        g.fill(x - 2, y - 2, x + w() + 2, y + h + 2, 0xFF6CC4FF);
        g.fill(x, y, x + w(), y + h, 0xF0101018);
        g.drawString(font, "PARTY  (" + rows.size() + "/" + PartyClient.limit() + ")", x + 8, y + 10, 0xC8E1FF);
        if (rows.isEmpty()) g.drawString(font, "Just you. Invite people or add bot teammates.", x + 8, y + 22, 0x999999, false);
        for (int i = 0; i < slots; i++) {
            int sx = slotX(i), sy = slotY(i);
            if (i >= rows.size()) {
                g.fill(sx + 4, sy, sx + CW - 4, sy + rowH - 3, 0x18FFFFFF);
                g.drawString(font, "Empty slot", sx + 10, sy + (rowH - 3) / 2 - 4, 0x666A70, false);
                continue;
            }
            PartySyncPacket.Member m = rows.get(i);
            CharacterType t = CharacterType.values()[Math.max(0, Math.min(CharacterType.values().length - 1, m.type()))];
            g.fill(sx + 4, sy, sx + CW - 4, sy + rowH - 3, 0x40FFFFFF & (t.getColor() | 0xFF000000));
            String sub = (m.bot() ? "BOT • " : "") + t.getDisplayName().getString() + (m.knockedOut() ? " • DOWN" : "");
            if (compact) {                                                           // one line: name, then what they are
                String name = m.name() + (m.leader() ? " ★" : "");
                int room = CW - 130 - 10;
                if (font.width(name) > room) name = font.plainSubstrByWidth(name, room - font.width("…")) + "…";
                g.drawString(font, name, sx + 10, sy + 5, t.getColor());
                String small = font.plainSubstrByWidth(sub, Math.max(0, room - font.width(name) - 6));
                if (!small.isEmpty()) g.drawString(font, small, sx + 16 + font.width(name), sy + 5, m.knockedOut() ? 0xFF6060 : 0x888888, false);
            } else {
                g.drawString(font, m.name() + (m.leader() ? " ★" : ""), sx + 10, sy + 3, t.getColor());
                g.drawString(font, sub, sx + 10, sy + 13, m.knockedOut() ? 0xFF6060 : 0xAAAAAA, false);
            }
        }
        super.render(g, mx, my, pt);
    }

    @Override public boolean isPauseScreen() { return false; }
}
