package com.xenowarriors.client.screen;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.SelectCharacterPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

/** The "Choose Your Warrior" screen shown when a player first joins. */
public class CharacterSelectScreen extends Screen {
    private static final int CARD_WIDTH_MAX = 150;
    private int CARD_WIDTH = 150;

    private static boolean locked(CharacterType t) { return com.xenowarriors.client.ClientSkillState.season() < t.season(); }
    private static final int CARD_HEIGHT = 176;
    /** Characters coming later: blank, locked slots (name + color) under the playable ones. */
    public static final String[] UPCOMING = {"Sam", "Lexy", "Max", "Kyle", "Chase", "Zack", "Kyrie", "Kaitlyn", "Zane", "Stevie", "Garry", "Technica", "Yuri", "SR Leader"};
    public static final int[] UPCOMING_COLOR = {0x4AA8FF, 0xC83A3A, 0x6AD08A, 0x8A8A9A, 0x6A6A8A, 0x9A3A3A, 0xE8B0D8, 0xD86AA0, 0x4A6AD8, 0xA05AE0, 0x4AC85A, 0x5AC8E8, 0x6AE08A, 0x9A2A2A};
    private static final int GAP = 24;

    /** Players who already have a character (opened via /xeno choose) can back out with Esc. */
    private final boolean canClose;

    public CharacterSelectScreen() {
        super(Component.translatable("screen.xenowarriors.select.title"));
        this.canClose = ClientCharacterData.getLocal() != CharacterType.NONE;
    }

    private int cardLeft(int index) {
        CharacterType[] choices = CharacterType.PLAYABLE;
        int total = choices.length * CARD_WIDTH + (choices.length - 1) * GAP;
        return (width - total) / 2 + index * (CARD_WIDTH + GAP);
    }

    private int cardTop() {
        return Math.max(34, (height - CARD_HEIGHT - 2 * SLOT_H - 22) / 2 + 6);
    }

    private static final int SLOT_W = 46, SLOT_H = 40, SLOT_GAP = 4;

    /** The locked future characters: two rows of 7 under the cards. */
    private void drawUpcoming(GuiGraphics g, int mouseX, int mouseY) {
        int perRow = 7, top = cardTop() + CARD_HEIGHT + 14;
        int rowW = perRow * SLOT_W + (perRow - 1) * SLOT_GAP;
        int sw = SLOT_W, x0 = (width - rowW) / 2;
        if (rowW > width - 10) { sw = (width - 10 - (perRow - 1) * SLOT_GAP) / perRow; x0 = 5; }
        g.drawCenteredString(font, "Coming later", width / 2, top - 10, 0x777777);
        for (int i = 0; i < UPCOMING.length; i++) {
            int x = x0 + (i % perRow) * (sw + SLOT_GAP), y = top + (i / perRow) * (SLOT_H + SLOT_GAP);
            boolean hov = mouseX >= x && mouseX < x + sw && mouseY >= y && mouseY < y + SLOT_H;
            int c = UPCOMING_COLOR[i];
            g.fill(x - 1, y - 1, x + sw + 1, y + SLOT_H + 1, hov ? 0xFF000000 | c : 0x66000000 | (c & 0xFFFFFF));
            g.fill(x, y, x + sw, y + SLOT_H, 0xE0101016);
            int cx = x + sw / 2;
            g.fill(cx - 4, y + 4, cx + 4, y + 12, 0xFF2A2A34);              // silhouette: head
            g.fill(cx - 6, y + 13, cx + 6, y + 25, 0xFF2A2A34);             // body
            g.fill(cx - 3, y + 17, cx + 3, y + 22, 0xFF55555F);             // padlock
            g.fill(cx - 2, y + 15, cx + 2, y + 17, 0xFF55555F);
            g.pose().pushPose();
            g.pose().translate(cx, y + SLOT_H - 9, 0);
            float sc = Math.min(1F, (sw - 4) / (float) Math.max(1, font.width(UPCOMING[i])));
            g.pose().scale(sc, sc, 1F);
            g.drawCenteredString(font, UPCOMING[i], 0, 0, hov ? c : 0x888888);
            g.pose().popPose();
            if (hov) g.renderTooltip(font, Component.literal(UPCOMING[i] + " - locked (coming in a later update)"), mouseX, mouseY);
        }
    }

    @Override
    protected void init() {
        CharacterType[] choices = CharacterType.PLAYABLE;
        CARD_WIDTH = Math.min(CARD_WIDTH_MAX, (width - 20 - (choices.length - 1) * GAP) / choices.length);   // three cards fit
        for (int i = 0; i < choices.length; i++) {
            CharacterType type = choices[i];
            int x = cardLeft(i);
            addRenderableWidget(Button.builder(
                            Component.translatable("screen.xenowarriors.select.play_as", type.getDisplayName()),
                            b -> choose(type))
                    .bounds(x + 10, cardTop() + CARD_HEIGHT - 28, CARD_WIDTH - 20, 20)
                    .build()).active = !locked(type);
        }
    }

    private void choose(CharacterType type) {
        ModNetwork.sendToServer(new SelectCharacterPacket(type));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        int top = cardTop();
        g.pose().pushPose();
        g.pose().scale(2F, 2F, 1F);
        g.drawCenteredString(font, title, width / 4, (top - 34) / 2, 0xFFFFFF);
        g.pose().popPose();
        g.drawCenteredString(font, Component.translatable("screen.xenowarriors.select.subtitle"),
                width / 2, top - 12, 0xAAAAAA);

        CharacterType[] choices = CharacterType.PLAYABLE;
        for (int i = 0; i < choices.length; i++) {
            CharacterType type = choices[i];
            int x = cardLeft(i);
            int centerX = x + CARD_WIDTH / 2;
            boolean hovered = mouseX >= x && mouseX < x + CARD_WIDTH && mouseY >= top && mouseY < top + CARD_HEIGHT;

            // card: colored border + dark inside
            int border = hovered ? type.getColor() : (type.getColor() & 0x00FFFFFF) | 0x99000000;
            g.fill(x - 2, top - 2, x + CARD_WIDTH + 2, top + CARD_HEIGHT + 2, border);
            g.fill(x, top, x + CARD_WIDTH, top + CARD_HEIGHT, 0xE0101016);

            // names
            if (locked(type)) {                 // no spoilers: a locked card shows nothing about who it is
                g.drawCenteredString(font, "???", centerX, top + 8, 0x888888);
                g.drawCenteredString(font, "Season " + type.season() + " content", centerX, top + 20, 0xAA5555);
                g.drawCenteredString(font, "/xeno season unlock " + type.season(), centerX, top + 32, 0x777777);
                g.drawCenteredString(font, "?", centerX, top + CARD_HEIGHT / 2 - 10, 0x555555);
                continue;
            }
            g.drawCenteredString(font, type.getFullName(), centerX, top + 8, type.getColor());
            g.drawCenteredString(font, type.getTitle(), centerX, top + 20, 0xDDDDDD);
            g.drawCenteredString(font, type.getElements(), centerX, top + 32, 0x999999);

            // 3D preview of the player wearing this character
            int modelY = top + CARD_HEIGHT - 42;
            ClientCharacterData.beginPreview(type);
            try {
                InventoryScreen.renderEntityInInventoryFollowsMouse(g, centerX, modelY, 50,
                        (float) (centerX - mouseX), (float) (modelY - 80 - mouseY), minecraft.player);
            } finally {
                ClientCharacterData.endPreview();
            }
        }

        super.render(g, mouseX, mouseY, partialTick);
        drawUpcoming(g, mouseX, mouseY);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return canClose;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
