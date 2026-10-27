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
    public static final String[] UPCOMING = {"Kyle", "Chase", "Zack", "Kaitlyn", "Zane", "Stevie", "Garry", "Technica", "Yuri", "SR Leader"};
    public static final int[] UPCOMING_COLOR = {0x8A8A9A, 0x6A6A8A, 0x9A3A3A, 0xD86AA0, 0x4A6AD8, 0xA05AE0, 0x4AC85A, 0x5AC8E8, 0x6AE08A, 0x9A2A2A};
    /** Four characters per page; the arrows flip through the rest. */
    private static final int PER_PAGE = 4;
    private static int page;

    private static CharacterType[] shown() {
        CharacterType[] all = CharacterType.PLAYABLE;
        int pages = (all.length + PER_PAGE - 1) / PER_PAGE;
        page = Math.floorMod(page, pages);
        int from = page * PER_PAGE, to = Math.min(all.length, from + PER_PAGE);
        return java.util.Arrays.copyOfRange(all, from, to);
    }

    /** UI test: flip to the next page. */
    public static void nextPageForTest() { page++; }

    private static int pages() { return (CharacterType.PLAYABLE.length + PER_PAGE - 1) / PER_PAGE; }
    private static final int GAP = 24;

    /** Players who already have a character (opened via /xeno choose) can back out with Esc. */
    private final boolean canClose;

    public CharacterSelectScreen() {
        super(Component.translatable("screen.xenowarriors.select.title"));
        this.canClose = ClientCharacterData.getLocal() != CharacterType.NONE;
    }

    private int cardLeft(int index) {
        CharacterType[] choices = shown();
        int total = choices.length * CARD_WIDTH + (choices.length - 1) * GAP;
        return (width - total) / 2 + index * (CARD_WIDTH + GAP);
    }

    /** Small windows: shorter cards, the coming-later characters as one row of name chips. */
    private boolean compact() { return height < 330; }

    private int cardH() { return compact() ? Math.max(120, Math.min(CARD_HEIGHT, height - cardTop() - 24)) : CARD_HEIGHT; }

    private int cardTop() {
        return compact() ? 30 : Math.max(34, (height - CARD_HEIGHT - 2 * SLOT_H - 22) / 2 + 6);
    }

    private static final int SLOT_W = 46, SLOT_H = 40, SLOT_GAP = 4;

    /** The locked future characters: two rows of 7 under the cards. */
    private void drawUpcoming(GuiGraphics g, int mouseX, int mouseY) {
        if (compact()) {                                                  // one row of small name chips
            int n = UPCOMING.length, cw = Math.min(46, (width - 10) / n), x0 = (width - n * cw) / 2, y = cardTop() + cardH() + (pages() > 1 ? 15 : 6);
            for (int i = 0; i < n; i++) {
                int x = x0 + i * cw, c = UPCOMING_COLOR[i];
                boolean hov = mouseX >= x && mouseX < x + cw - 2 && mouseY >= y && mouseY < y + 12;
                g.fill(x, y, x + cw - 2, y + 12, hov ? 0xFF000000 | c : 0x66000000 | (c & 0xFFFFFF));
                g.fill(x + 1, y + 1, x + cw - 3, y + 11, 0xE0101016);
                g.pose().pushPose();
                g.pose().translate(x + (cw - 2) / 2F, y + 3, 0);
                float sc = Math.min(0.6F, (cw - 5) / (float) Math.max(1, font.width(UPCOMING[i])));
                g.pose().scale(sc, sc, 1F);
                g.drawCenteredString(font, UPCOMING[i], 0, 0, hov ? c : 0x888888);
                g.pose().popPose();
                if (hov) g.renderTooltip(font, Component.literal(UPCOMING[i] + " - locked (coming in a later update)"), mouseX, mouseY);
            }
            return;
        }
        int perRow = 5, top = cardTop() + CARD_HEIGHT + 22;
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
        CharacterType[] choices = shown();
        CARD_WIDTH = Math.min(CARD_WIDTH_MAX, (width - 60 - (PER_PAGE - 1) * GAP) / PER_PAGE);   // four cards fit, arrows either side
        if (pages() > 1) {
            int cy = cardTop() + cardH() / 2 - 10;
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; rebuildWidgets(); }).bounds(cardLeft(0) - 26, cy, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); }).bounds(cardLeft(choices.length - 1) + CARD_WIDTH + 6, cy, 20, 20).build());
        }
        for (int i = 0; i < choices.length; i++) {
            CharacterType type = choices[i];
            int x = cardLeft(i);
            addRenderableWidget(Button.builder(
                            Component.translatable("screen.xenowarriors.select.play_as", type.getDisplayName()),
                            b -> choose(type))
                    .bounds(x + 10, cardTop() + cardH() - 28, CARD_WIDTH - 20, 20)
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

        int top = cardTop(), ch = cardH();
        float ts = compact() ? 1.3F : 2F;
        g.pose().pushPose();
        g.pose().scale(ts, ts, 1F);
        g.drawCenteredString(font, title, (int) (width / 2 / ts), (int) ((compact() ? 4 : top - 34) / ts), 0xFFFFFF);
        g.pose().popPose();
        g.drawCenteredString(font, Component.translatable("screen.xenowarriors.select.subtitle"),
                width / 2, top - (compact() ? 11 : 12), 0xAAAAAA);

        CharacterType[] choices = shown();
        if (pages() > 1) g.drawCenteredString(font, "Page " + (page + 1) + " / " + pages(), width / 2, top + ch + 4, 0x888888);
        for (int i = 0; i < choices.length; i++) {
            CharacterType type = choices[i];
            int x = cardLeft(i);
            int centerX = x + CARD_WIDTH / 2;
            boolean hovered = mouseX >= x && mouseX < x + CARD_WIDTH && mouseY >= top && mouseY < top + ch;

            // card: colored border + dark inside
            int border = hovered ? type.getColor() : (type.getColor() & 0x00FFFFFF) | 0x99000000;
            g.fill(x - 2, top - 2, x + CARD_WIDTH + 2, top + ch + 2, border);
            g.fill(x, top, x + CARD_WIDTH, top + ch, 0xE0101016);

            // names
            if (locked(type)) {                 // no spoilers: a locked card shows nothing about who it is
                g.drawCenteredString(font, "???", centerX, top + 8, 0x888888);
                g.drawCenteredString(font, "Season " + type.season() + " content", centerX, top + 20, 0xAA5555);
                g.drawCenteredString(font, "/xeno season unlock " + type.season(), centerX, top + 32, 0x777777);
                g.drawCenteredString(font, "?", centerX, top + ch / 2 - 10, 0x555555);
                continue;
            }
            // 3D preview of the player wearing this character (drawn first, so the names stay readable over wings)
            int modelY = top + ch - 34;
            int size = compact() ? Math.max(30, (ch - 80) / 2) : 50;
            g.enableScissor(x, top + 40, x + CARD_WIDTH, top + ch - 30);
            ClientCharacterData.beginPreview(type);
            try {
                InventoryScreen.renderEntityInInventoryFollowsMouse(g, centerX, modelY, size,
                        (float) (centerX - mouseX), (float) (modelY - size * 1.6F - mouseY), minecraft.player);
            } finally {
                ClientCharacterData.endPreview();
                g.disableScissor();
            }
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            g.drawCenteredString(font, type.getFullName(), centerX, top + 8, type.getColor());
            g.drawCenteredString(font, type.getTitle(), centerX, top + 20, 0xDDDDDD);
            g.drawCenteredString(font, type.getElements(), centerX, top + 32, 0x999999);
            g.pose().popPose();
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
