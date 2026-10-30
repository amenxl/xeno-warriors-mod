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
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * "Choose Your Warrior": a big model of the character you're looking at on the left (name, title, elements under it),
 * and a slanted grid of portrait cards on the right. The team logos along the top switch between the teams from the show;
 * characters coming later sit in the grid as locked cards. Click a card to look at it, click it again (or SELECT) to play.
 */
public class CharacterSelectScreen extends Screen {
    /** The characters sorted into their teams from the show. */
    public enum Group {
        FREEDOM("Freedom Fighters", "logo_freedom_fighters_icon", 0xC8282E, new CharacterType[]{CharacterType.MIKIKO, CharacterType.ULTIMA, CharacterType.KAERU, CharacterType.YURI, CharacterType.ZANE}),
        SKY_PATROL("Sky Patrol", "logo_sky_patrol", 0x8A3AE8, new CharacterType[]{CharacterType.RHALIX, CharacterType.KYRIE, CharacterType.LEXY,
                CharacterType.MAX, CharacterType.SAM, CharacterType.TECHNICA}),
        ORENJI("Orenji Clan", "logo_orenji", 0xE8701C, new CharacterType[]{CharacterType.MIKIKO, CharacterType.KAERU}),
        GOVERNMENT("Government", "logo_government", 0x6A3AC8, new CharacterType[]{}, "Kyle"),
        DEMONS("Ai-oni's Demons", "logo_demons", 0xC41E30, new CharacterType[]{CharacterType.AIONI}, "Zack", "Kaitlyn"),
        SKY_REBELLION("Sky Rebellion", "logo_sky_rebellion", 0x9A2A2A, new CharacterType[]{CharacterType.LOGAN, CharacterType.KILL, CharacterType.KEITA}, "SR Leader"),
        DISCO("Disco Dojo", "logo_disco_dojo", 0xA05AE0, new CharacterType[]{CharacterType.STEVIE, CharacterType.GARRY}),
        WANDERERS("Wanderers", "logo_wanderers", 0x6A8AD8, new CharacterType[]{}, "Chase");

        public final String title;
        public final ResourceLocation logo;
        public final int color;
        public final CharacterType[] members;
        public final String[] upcoming;

        Group(String title, String logo, int color, CharacterType[] members, String... upcoming) {
            this.title = title; this.color = color; this.members = members; this.upcoming = upcoming;
            this.logo = logo == null ? null : com.xenowarriors.XenoWarriors.id("textures/gui/" + logo + ".png");
        }

        public static Group of(CharacterType t) {
            for (Group g : values()) for (CharacterType m : g.members) if (m == t) return g;
            return FREEDOM;
        }
    }

    private static Group group = Group.FREEDOM;
    private static boolean opened;
    private CharacterType focus;                       // the one shown big on the left
    private final boolean canClose;

    private record Card(CharacterType type, String upcoming, int x, int y, int w, int h) {
        boolean hit(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }
    private final List<Card> cards = new ArrayList<>();
    private final List<int[]> tabs = new ArrayList<>();    // {x, y, w, h, group ordinal}

    public CharacterSelectScreen() {
        super(Component.translatable("screen.xenowarriors.select.title"));
        this.canClose = ClientCharacterData.getLocal() != CharacterType.NONE;
        if (!opened) { opened = true; if (canClose) group = Group.of(ClientCharacterData.getLocal()); }
        if (canClose && Group.of(ClientCharacterData.getLocal()) == group) focus = ClientCharacterData.getLocal();
        if (testFocus != null) { group = Group.of(testFocus); focus = testFocus; }
        ModNetwork.sendToServer(new com.xenowarriors.network.CharLevelsPacket(new int[0]));      // ask for our level with everyone
        ModNetwork.sendToServer(new com.xenowarriors.network.TakenCharsPacket(new String[0], true));   // and who's playing who
    }

    private static int level(CharacterType t) { return com.xenowarriors.network.CharLevelsPacket.CLIENT[t.ordinal()]; }

    /** UI test: look at this character. */
    public static void focusForTest(CharacterType t) { testFocus = t; }
    private static CharacterType testFocus;

    /** UI test: flip to the next team. */
    public static void nextPageForTest() { group = Group.values()[(group.ordinal() + 1) % Group.values().length]; }

    private static boolean locked(CharacterType t) { return com.xenowarriors.client.ClientSkillState.season() < t.season(); }
    /** Someone else is playing them (and there's still someone free to pick instead). */
    private static boolean taken(CharacterType t) {
        return t != null && t != ClientCharacterData.getLocal() && !com.xenowarriors.network.TakenCharsPacket.who(t).isEmpty() && com.xenowarriors.network.TakenCharsPacket.CLIENT_ANY_FREE;
    }
    private static boolean pickable(CharacterType t) { return t != null && !locked(t) && !taken(t); }

    private int tabH() { return height < 300 ? 18 : 26; }

    @Override
    protected void init() {
        // team tabs across the top (logo, or the team's name)
        tabs.clear();
        Group[] gs = Group.values();
        int th = tabH(), tw = Math.min(th * 2 + 8, (width - 120) / gs.length), x0 = width - gs.length * (tw + 3) - 8;
        for (int i = 0; i < gs.length; i++) tabs.add(new int[]{x0 + i * (tw + 3), 6, tw, th, i});
        // the grid: slanted rows of portrait cards on the right
        cards.clear();
        List<Object[]> entries = new ArrayList<>();
        for (CharacterType t : group.members) entries.add(new Object[]{t, null});
        for (String u : group.upcoming) entries.add(new Object[]{null, u});
        int gridX = width * 46 / 100, gridTop = th + 16, gridH = height - gridTop - 40;
        int cols = 3, rows = Math.max(2, (entries.size() + cols - 1) / cols);
        int cw = Math.min(80, (width - gridX - 50) / cols - 4), ch = Math.min((int) (cw * 1.3F), (gridH - (rows - 1) * 4) / rows);
        int slant = ch / 4;                             // each row steps right, like a slanted grid
        for (int i = 0; i < entries.size(); i++) {
            int r = i / cols, c = i % cols;
            int x = gridX + r * slant + c * (cw + 4), y = gridTop + r * (ch + 4);
            cards.add(new Card((CharacterType) entries.get(i)[0], (String) entries.get(i)[1], x, y, cw, ch));
        }
        if (testFocus != null) focus = testFocus;
        if (focus == null || Group.of(focus) != group) focus = group.members.length > 0 ? group.members[0] : null;
        Button sel = addRenderableWidget(Button.builder(Component.literal("SELECT"), b -> { if (pickable(focus)) choose(focus); })
                .bounds(width - 76, height - 30, 68, 22).build());
        sel.active = pickable(focus);
        // your own warriors: the You character slots
        addRenderableWidget(Button.builder(Component.literal("YOUR OWN \u2605"), b -> ModNetwork.sendToServer(new com.xenowarriors.network.YouActionPacket(com.xenowarriors.network.YouActionPacket.OPEN, 0, null)))
                .bounds(width - 76 - 96, height - 30, 90, 22).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Make your own warrior with your Minecraft skin: pick a name, race and element. 10 slots."))).build());
        if (canClose) addRenderableWidget(Button.builder(Component.literal("<"), b -> onClose()).bounds(8, 6, 20, th).build());
    }

    private void choose(CharacterType type) {
        ModNetwork.sendToServer(new SelectCharacterPacket(type));
        onClose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int[] t : tabs) if (mx >= t[0] && mx < t[0] + t[2] && my >= t[1] && my < t[1] + t[3]) {
            group = Group.values()[t[4]]; focus = null; rebuildWidgets(); click(); return true;
        }
        for (Card c : cards) if (c.hit(mx, my) && c.type != null) {
            if (c.type == focus && pickable(c.type)) choose(c.type);
            else { focus = c.type; rebuildWidgets(); }
            click(); return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void click() {
        minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1F));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // background: near black with faint diagonal bands in the team's colour
        g.fill(0, 0, width, height, 0xF00C0C10);
        int col = group.color & 0xFFFFFF;
        for (int i = -height; i < width; i += 46)
            for (int y = 0; y < height; y += 2) g.fill(i + y / 2, y, i + y / 2 + 16, y + 2, 0x10000000 | col);
        int div = width * 43 / 100;                                       // the slanted edge between the model and the grid
        for (int y = 0; y < height; y += 2) g.fill(div + y / 3, y, div + y / 3 + 2, y + 2, 0x66000000 | col);

        // team tabs
        for (int[] t : tabs) {
            Group gr = Group.values()[t[4]];
            boolean on = gr == group, hov = mouseX >= t[0] && mouseX < t[0] + t[2] && mouseY >= t[1] && mouseY < t[1] + t[3];
            g.fill(t[0] - 1, t[1] - 1, t[0] + t[2] + 1, t[1] + t[3] + 1, on ? 0xFF000000 | gr.color : hov ? 0xAA000000 | gr.color : 0x55333338);
            g.fill(t[0], t[1], t[0] + t[2], t[1] + t[3], on ? 0xF0262230 : 0xE0101014);
            if (gr.logo != null) {
                int lw = Math.min(t[2] - 2, (t[3] - 2) * 2), lh = lw / 2;
                g.blit(gr.logo, t[0] + (t[2] - lw) / 2, t[1] + (t[3] - lh) / 2, lw, lh, 0, 0, 256, 128, 256, 128);
            } else {
                drawFit(g, gr.title, t[0] + t[2] / 2, t[1] + t[3] / 2 - 4, t[2] - 4, on || hov ? 0xFF000000 | gr.color : 0x999999);
            }
            if (hov) g.renderTooltip(font, Component.literal(gr.title), mouseX, mouseY);
        }
        // the team's name under the tabs
        g.drawString(font, group.title.toUpperCase(), tabs.get(0)[0], tabH() + 9, 0xFF000000 | group.color);

        // the big model on the left
        if (focus != null) {
            int cx = width * 22 / 100, feet = height - 50, size = Math.min((height - 70) * 10 / 28, width / 7);
            if (locked(focus)) {
                g.drawCenteredString(font, "?", cx, height / 2 - 10, 0x555555);
            } else {
                ClientCharacterData.beginPreview(focus);
                try {
                    InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx, feet, size, (float) (cx - mouseX), (float) (feet - size * 1.6F - mouseY), minecraft.player);
                } finally { ClientCharacterData.endPreview(); }
            }
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            float ns = height < 300 ? 1.4F : 2F;
            g.pose().pushPose();
            g.pose().translate(12, height - 44, 0);
            g.pose().scale(ns, ns, 1F);
            g.drawString(font, locked(focus) ? Component.literal("???") : focus.getFullName(), 0, 0, 0xFFFFFF);
            g.pose().popPose();
            Component sub = locked(focus) ? Component.literal("Season " + focus.season() + " - /xeno season unlock " + focus.season())
                    : level(focus) > 0 ? Component.literal("").append(focus.getTitle()).append(Component.literal("   Lv." + level(focus)).withStyle(net.minecraft.ChatFormatting.GOLD)) : focus.getTitle();
            g.drawString(font, sub, 12, height - 26, locked(focus) ? 0xAA5555 : 0xCCCCCC);
            String who = com.xenowarriors.network.TakenCharsPacket.who(focus);
            if (!locked(focus) && !who.isEmpty() && focus != ClientCharacterData.getLocal()) {
                String s = taken(focus) ? who + " is playing them - pick someone else" : who + " is playing them (everyone's taken, so you can double up)";
                int sw = font.width(s) + 8;
                g.fill(12, height - 58, 12 + sw, height - 47, 0xD0000000);
                g.drawString(font, s, 16, height - 56, taken(focus) ? 0xFF7070 : 0xFFD070);
            }
            if (!locked(focus)) {
                Component el = focus.getElements();
                int w = font.width(el) + 8;
                g.fill(12, height - 15, 12 + w, height - 4, 0xD0000000 | (focus.getColor() & 0xFFFFFF));
                g.drawString(font, el, 16, height - 13, 0xFFFFFF);
            }
            g.pose().popPose();
        } else {
            // wrapped to the left panel so it never runs off the left edge on small windows
            var lines = font.split(Component.literal("No one from the " + group.title + " is playable yet"), Math.max(60, width * 40 / 100));
            int ly = height / 2 - lines.size() * 5;
            for (var line : lines) { g.drawCenteredString(font, line, width * 22 / 100, ly, 0x888888); ly += 10; }
        }

        // the grid of portrait cards
        for (Card c : cards) {
            boolean hov = c.hit(mouseX, mouseY), sel = c.type != null && c.type == focus;
            int tc = c.type == null ? 0x2A2A30 : c.type.getColor() & 0xFFFFFF;
            int edge = sel ? 0xFFFFFFFF : hov ? 0xFF000000 | tc : 0xFF000000 | ((tc >> 1) & 0x7F7F7F);
            int e = sel ? 2 : 1;
            g.fill(c.x - e, c.y - e, c.x + c.w + e, c.y + c.h + e, edge);
            g.fill(c.x, c.y, c.x + c.w, c.y + c.h, 0xFF17161C);
            g.fill(c.x, c.y, c.x + c.w, c.y + c.h / 2, 0x22000000 | tc);
            if (c.type == null || locked(c.type)) {                   // locked: a dark silhouette
                int cx = c.x + c.w / 2, cy = c.y + c.h / 2 - 6;
                g.fill(cx - 8, cy - 14, cx + 8, cy + 2, 0xFF2A2A34);
                g.fill(cx - 14, cy + 4, cx + 14, c.y + c.h - 12, 0xFF2A2A34);
                g.drawCenteredString(font, "?", cx, cy - 9, 0xFF55555F);
                g.fill(c.x, c.y + c.h - 11, c.x + c.w, c.y + c.h, 0xE0000000);
                drawFit(g, c.type == null ? c.upcoming : "???", c.x + c.w / 2, c.y + c.h - 10, c.w - 4, 0x888888);
                if (hov) g.renderTooltip(font, Component.literal(c.type == null ? c.upcoming + " - coming in a later update" : "Season " + c.type.season() + " content"), mouseX, mouseY);
                continue;
            }
            // portrait: head and shoulders, zoomed in
            g.enableScissor(c.x, c.y, c.x + c.w, c.y + c.h - 11);
            ClientCharacterData.beginPreview(c.type);
            try {
                int size = (int) (c.h * 1.1F);
                int feet = c.y + (int) (size * 2.1F);
                InventoryScreen.renderEntityInInventoryFollowsMouse(g, c.x + c.w / 2, feet, size, -8F, 0F, minecraft.player);
            } finally { ClientCharacterData.endPreview(); g.disableScissor(); }
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            g.fill(c.x, c.y + c.h - 11, c.x + c.w, c.y + c.h, 0xE0000000);
            g.fill(c.x, c.y + c.h - 12, c.x + c.w, c.y + c.h - 11, 0xFF000000 | tc);
            drawFit(g, c.type.getDisplayName().getString(), c.x + c.w / 2, c.y + c.h - 10, c.w - 4, sel ? 0xFFFFFF : 0xDDDDDD);
            if (ClientCharacterData.getLocal() == c.type) g.drawString(font, "★", c.x + c.w - 9, c.y + 2, 0xFFE040);   // your current character
            String who = com.xenowarriors.network.TakenCharsPacket.who(c.type);
            if (!who.isEmpty() && ClientCharacterData.getLocal() != c.type) {          // someone else is playing them
                if (taken(c.type)) g.fill(c.x, c.y, c.x + c.w, c.y + c.h - 12, 0x99000000);
                int bw = Math.min(c.w - 2, font.width("TAKEN") + 6), by = c.y + c.h / 2 - 6;
                g.fill(c.x + (c.w - bw) / 2, by, c.x + (c.w + bw) / 2, by + 11, taken(c.type) ? 0xE0A01818 : 0xE0A07018);
                drawFit(g, "TAKEN", c.x + c.w / 2, by + 2, bw - 2, 0xFFFFFF);
                drawFit(g, who, c.x + c.w / 2, by + 13, c.w - 4, 0xFFDDDD);
                if (hov) g.renderTooltip(font, Component.literal(who + " is playing " + c.type.getDisplayName().getString()), mouseX, mouseY);
            }
            int lv = level(c.type);
            if (lv > 0) {                                                        // your level with them, on the card
                String ls = "Lv." + lv;
                int lw = font.width(ls) + 4;
                g.fill(c.x + 1, c.y + 1, c.x + 1 + lw, c.y + 11, 0xC0000000);
                g.fill(c.x + 1, c.y + 11, c.x + 1 + lw, c.y + 12, 0xFF000000 | tc);
                g.drawString(font, ls, c.x + 3, c.y + 2, 0xFFE8C040);
            }
            g.pose().popPose();
        }

        // "SELECT" down the right edge
        g.pose().pushPose();
        g.pose().translate(width - 16, height / 2F, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-90));
        g.pose().scale(1.8F, 1.8F, 1F);
        g.drawCenteredString(font, "SELECT", 0, 0, 0x66FFFFFF);
        g.pose().popPose();

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawFit(GuiGraphics g, String s, int cx, int y, int room, int color) {
        float sc = Math.min(1F, room / (float) Math.max(1, font.width(s)));
        g.pose().pushPose();
        g.pose().translate(cx, y + (1 - sc) * 4, 0);
        g.pose().scale(sc, sc, 1F);
        g.drawCenteredString(font, s, 0, 0, color);
        g.pose().popPose();
    }

    @Override
    public boolean shouldCloseOnEsc() { return canClose; }

    @Override
    public boolean isPauseScreen() { return false; }
}
