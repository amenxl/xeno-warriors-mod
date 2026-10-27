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
    /** The characters sorted into their teams from the show; each tab shows its logo, its playable characters and who's coming later. */
    public enum Group {
        FREEDOM("Freedom Fighters", "Freedom", "logo_freedom_fighters", 0xC8282E, new CharacterType[]{CharacterType.MIKIKO, CharacterType.ULTIMA}, "Yuri"),
        SKY_PATROL("Sky Patrol", "Sky Patrol", "logo_sky_patrol", 0x8A3AE8, new CharacterType[]{CharacterType.RHALIX, CharacterType.KYRIE, CharacterType.LEXY,
                CharacterType.MAX, CharacterType.SAM, CharacterType.TECHNICA}),
        GOVERNMENT("Government", "Gov", "logo_government", 0x6A3AC8, new CharacterType[]{}, "Kyle"),
        ORENJI("Orenji Clan", "Orenji", null, 0xE8701C, new CharacterType[]{CharacterType.KAERU}),
        DEMONS("Ai-oni's Demons", "Demons", null, 0xC41E30, new CharacterType[]{CharacterType.AIONI}, "Zack", "Kaitlyn"),
        SKY_REBELLION("Sky Rebellion", "SR", null, 0x9A2A2A, new CharacterType[]{}, "SR Leader"),
        DISCO("Disco Dojo", "Dojo", null, 0xA05AE0, new CharacterType[]{}, "Stevie", "Garry"),
        WANDERERS("Wanderers", "Others", null, 0x6A8AD8, new CharacterType[]{}, "Chase", "Zane");

        public final String title, tab;
        public final net.minecraft.resources.ResourceLocation logo;
        public final int color;
        public final CharacterType[] members;
        public final String[] upcoming;

        Group(String title, String tab, String logo, int color, CharacterType[] members, String... upcoming) {
            this.title = title; this.tab = tab; this.color = color; this.members = members; this.upcoming = upcoming;
            this.logo = logo == null ? null : com.xenowarriors.XenoWarriors.id("textures/gui/" + logo + ".png");
        }

        public static Group of(CharacterType t) {
            for (Group g : values()) for (CharacterType m : g.members) if (m == t) return g;
            return FREEDOM;
        }
    }

    private static Group group = Group.FREEDOM;
    private static boolean opened;
    /** Four characters per page; the arrows flip through the rest of the team. */
    private static final int PER_PAGE = 4;
    private static int page;

    private static CharacterType[] shown() {
        CharacterType[] all = group.members;
        if (all.length == 0) return all;
        int pages = (all.length + PER_PAGE - 1) / PER_PAGE;
        page = Math.floorMod(page, pages);
        int from = page * PER_PAGE, to = Math.min(all.length, from + PER_PAGE);
        return java.util.Arrays.copyOfRange(all, from, to);
    }

    /** UI test: flip to the next team. */
    public static void nextPageForTest() { group = Group.values()[(group.ordinal() + 1) % Group.values().length]; page = 0; }

    private static int pages() { return Math.max(1, (group.members.length + PER_PAGE - 1) / PER_PAGE); }
    private static final int GAP = 24;

    /** Players who already have a character (opened via /xeno choose) can back out with Esc. */
    private final boolean canClose;

    public CharacterSelectScreen() {
        super(Component.translatable("screen.xenowarriors.select.title"));
        this.canClose = ClientCharacterData.getLocal() != CharacterType.NONE;
        if (!opened) { opened = true; if (canClose) group = Group.of(ClientCharacterData.getLocal()); }
    }

    private int cardLeft(int index) {
        CharacterType[] choices = shown();
        int total = choices.length * CARD_WIDTH + (choices.length - 1) * GAP;
        return (width - total) / 2 + index * (CARD_WIDTH + GAP);
    }

    /** Small windows: shorter cards, the coming-later characters as one row of name chips. */
    private boolean compact() { return height < 380; }

    private int cardH() { return Math.max(110, Math.min(CARD_HEIGHT, height - cardTop() - (group.upcoming.length > 0 ? (compact() ? 30 : 70) : 22))); }

    private int tabsY() { return compact() ? 15 : 26; }
    private int logoH() { return compact() ? 22 : 40; }

    private int cardTop() {
        return tabsY() + 18 + logoH() + (compact() ? 4 : 8);
    }

    private static final int SLOT_W = 46, SLOT_H = 40, SLOT_GAP = 4;

    /** The locked future characters: two rows of 7 under the cards. */
    private void drawUpcoming(GuiGraphics g, int mouseX, int mouseY) {
        if (compact()) {                                                  // one row of small name chips
            int n = group.upcoming.length;
            if (n == 0) return;
            int cw = Math.min(60, (width - 10) / n), x0 = (width - n * cw) / 2, y = cardTop() + cardH() + (pages() > 1 ? 15 : 6);
            for (int i = 0; i < n; i++) {
                int x = x0 + i * cw, c = group.color;
                boolean hov = mouseX >= x && mouseX < x + cw - 2 && mouseY >= y && mouseY < y + 12;
                g.fill(x, y, x + cw - 2, y + 12, hov ? 0xFF000000 | c : 0x66000000 | (c & 0xFFFFFF));
                g.fill(x + 1, y + 1, x + cw - 3, y + 11, 0xE0101016);
                g.pose().pushPose();
                g.pose().translate(x + (cw - 2) / 2F, y + 3, 0);
                float sc = Math.min(0.8F, (cw - 5) / (float) Math.max(1, font.width(group.upcoming[i])));
                g.pose().scale(sc, sc, 1F);
                g.drawCenteredString(font, group.upcoming[i], 0, 0, hov ? c : 0x888888);
                g.pose().popPose();
                if (hov) g.renderTooltip(font, Component.literal(group.upcoming[i] + " - locked (coming in a later update)"), mouseX, mouseY);
            }
            return;
        }
        if (group.upcoming.length == 0) return;
        int perRow = Math.min(5, group.upcoming.length), top = cardTop() + cardH() + 22;
        int rowW = perRow * SLOT_W + (perRow - 1) * SLOT_GAP;
        int sw = SLOT_W, x0 = (width - rowW) / 2;
        if (rowW > width - 10) { sw = (width - 10 - (perRow - 1) * SLOT_GAP) / perRow; x0 = 5; }
        g.drawCenteredString(font, "Coming later", width / 2, top - 10, 0x777777);
        for (int i = 0; i < group.upcoming.length; i++) {
            int x = x0 + (i % perRow) * (sw + SLOT_GAP), y = top + (i / perRow) * (SLOT_H + SLOT_GAP);
            boolean hov = mouseX >= x && mouseX < x + sw && mouseY >= y && mouseY < y + SLOT_H;
            int c = group.color;
            g.fill(x - 1, y - 1, x + sw + 1, y + SLOT_H + 1, hov ? 0xFF000000 | c : 0x66000000 | (c & 0xFFFFFF));
            g.fill(x, y, x + sw, y + SLOT_H, 0xE0101016);
            int cx = x + sw / 2;
            g.fill(cx - 4, y + 4, cx + 4, y + 12, 0xFF2A2A34);              // silhouette: head
            g.fill(cx - 6, y + 13, cx + 6, y + 25, 0xFF2A2A34);             // body
            g.fill(cx - 3, y + 17, cx + 3, y + 22, 0xFF55555F);             // padlock
            g.fill(cx - 2, y + 15, cx + 2, y + 17, 0xFF55555F);
            g.pose().pushPose();
            g.pose().translate(cx, y + SLOT_H - 9, 0);
            float sc = Math.min(1F, (sw - 4) / (float) Math.max(1, font.width(group.upcoming[i])));
            g.pose().scale(sc, sc, 1F);
            g.drawCenteredString(font, group.upcoming[i], 0, 0, hov ? c : 0x888888);
            g.pose().popPose();
            if (hov) g.renderTooltip(font, Component.literal(group.upcoming[i] + " - locked (coming in a later update)"), mouseX, mouseY);
        }
    }

    @Override
    protected void init() {
        Group[] gs = Group.values();
        int tw = Math.min(70, (width - 10 - (gs.length - 1) * 2) / gs.length), tx = (width - gs.length * tw - (gs.length - 1) * 2) / 2;
        for (int i = 0; i < gs.length; i++) {
            Group gr = gs[i];
            Button b = addRenderableWidget(Button.builder(Component.literal(gr.tab), x -> { group = gr; page = 0; rebuildWidgets(); })
                    .bounds(tx + i * (tw + 2), tabsY(), tw, 16).build());
            b.active = gr != group;
        }
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
                            CARD_WIDTH < 100 ? type.getDisplayName() : Component.translatable("screen.xenowarriors.select.play_as", type.getDisplayName()),
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
        float ts = compact() ? 1.2F : 1.8F;
        g.pose().pushPose();
        g.pose().scale(ts, ts, 1F);
        g.drawCenteredString(font, title, (int) (width / 2 / ts), (int) ((compact() ? 3 : 6) / ts), 0xFFFFFF);
        g.pose().popPose();
        // the team: its logo (or a name banner in its colour)
        int ly = tabsY() + 19, lh = logoH();
        if (group.logo != null) {
            int lw = lh * 2;
            g.blit(group.logo, width / 2 - lw / 2, ly, lw, lh, 0, 0, 256, 128, 256, 128);
        } else {
            float bs = compact() ? 1.4F : 2.2F;
            g.pose().pushPose();
            g.pose().translate(width / 2F, ly + lh / 2F - 4 * bs, 0);
            g.pose().scale(bs, bs, 1F);
            g.drawCenteredString(font, group.title, 0, 0, 0xFF000000 | group.color);
            g.pose().popPose();
        }
        if (group.members.length == 0)
            g.drawCenteredString(font, "No one from the " + group.title + " is playable yet", width / 2, top + 20, 0x888888);

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
            fit(g, type.getFullName(), centerX, top + 8, type.getColor());
            fit(g, type.getTitle(), centerX, top + 20, 0xDDDDDD);
            fit(g, type.getElements(), centerX, top + 32, 0x999999);
            g.pose().popPose();
        }

        super.render(g, mouseX, mouseY, partialTick);
        drawUpcoming(g, mouseX, mouseY);
    }

    /** Centered text shrunk to fit inside a card. */
    private void fit(GuiGraphics g, Component text, int cx, int y, int color) {
        int w = font.width(text), room = CARD_WIDTH - 6;
        if (w <= room) { g.drawCenteredString(font, text, cx, y, color); return; }
        float sc = room / (float) w;
        g.pose().pushPose();
        g.pose().translate(cx, y + (1 - sc) * 4, 0);
        g.pose().scale(sc, sc, 1F);
        g.drawCenteredString(font, text, 0, 0, color);
        g.pose().popPose();
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
