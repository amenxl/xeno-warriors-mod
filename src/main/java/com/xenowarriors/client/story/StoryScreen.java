package com.xenowarriors.client.story;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.network.StoryActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The Story menu (Main Menu > Story, /xeno story, or DragonMineZ's quest screen): the Season 1 storylines and their
 * missions, the next mission's Karma Level requirement, and buttons to start it, visit the storyline's hub (talk to
 * people, take side missions) or leave. Underneath: everyone you've met, your bond with them and whether you can play them.
 */
public class StoryScreen extends Screen {
    private final Screen parent;
    private String sel;

    public StoryScreen(Screen parent) { super(Component.literal("Story Mode")); this.parent = parent; }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(parent); }

    public void refresh() { init(); }

    private List<CompoundTag> paths() {
        List<CompoundTag> l = new ArrayList<>();
        for (Tag t : StoryClient.state.getList("paths", Tag.TAG_COMPOUND)) l.add((CompoundTag) t);
        return l;
    }

    private CompoundTag path(String id) { for (CompoundTag p : paths()) if (p.getString("id").equals(id)) return p; return null; }

    @Override
    protected void init() {
        clearWidgets();
        CompoundTag st = StoryClient.state;
        String cur = st.getString("cur");
        if (sel == null || path(sel) == null) sel = !cur.isEmpty() ? cur : paths().isEmpty() ? null : paths().get(0).getString("id");
        int bx = width - 132, by = height - 30;
        if (!st.getBoolean("active")) {
            addRenderableWidget(Button.builder(Component.literal("Start Story Mode"), b -> StoryClient.act(StoryActionPacket.START, "", 0)).bounds(bx - 120, by, 240, 20).build());
            return;
        }
        boolean inRun = st.contains("obj");
        if (inRun) {
            addRenderableWidget(Button.builder(Component.literal(st.getBoolean("inHub") ? "Leave" : "Leave mission"), b -> StoryClient.act(StoryActionPacket.QUIT, "", 0)).bounds(bx, by, 120, 20).build());
            if (st.getBoolean("host") && !st.getBoolean("inHub"))
                addRenderableWidget(Button.builder(Component.literal("Resume scene"), b -> { StoryClient.act(StoryActionPacket.BEGIN, "", 0); onClose(); }).bounds(bx - 126, by, 120, 20).build());
            return;
        }
        CompoundTag p = sel == null ? null : path(sel);
        if (p != null && p.getInt("status") == 1)
            addRenderableWidget(Button.builder(Component.literal("Begin this storyline"), b -> StoryClient.act(StoryActionPacket.PICK, sel, 0)).bounds(bx - 126, by, 246, 20).build());
        if (p != null && p.getInt("status") == 2) {
            addRenderableWidget(Button.builder(Component.literal("Start mission"), b -> { StoryClient.act(StoryActionPacket.BEGIN, "", 0); onClose(); }).bounds(bx, by, 120, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Visit (talk / sides)"), b -> { StoryClient.act(StoryActionPacket.HUB, "", 0); onClose(); }).bounds(bx - 126, by, 120, 20).build());
        }
    }

    // ------------------------------------------------------------------ looks

    private static final int CARD_W = 160, CARD_H = 30, LIST_X = 12, LIST_Y = 34;

    /** Who each storyline is about (the face on its card). */
    private static String face(String path) {
        return switch (path) { case "rhalix" -> "rhalix"; case "mikiko" -> "mikiko"; case "sr" -> "senju"; case "gov" -> "kyle"; case "finale" -> "ultima"; default -> "mikiko"; };
    }

    private static int pathColor(String path) {
        return switch (path) { case "rhalix" -> 0xA02CD2; case "mikiko" -> 0xE8801A; case "sr" -> 0xC03030; case "gov" -> 0x6A3AC8; case "finale" -> 0x1FE07A; default -> 0xE8B030; };
    }

    /** The storyline's banner (textures/gui/story/<path>.png: the user's art of Rhalix / Mikiko, both for the finale). */
    private static net.minecraft.resources.ResourceLocation banner(String path) {
        return com.xenowarriors.XenoWarriors.id("textures/gui/story/" + (path == null ? "finale" : path) + ".png");
    }

    private static net.minecraft.resources.ResourceLocation portrait(String who) {
        return com.xenowarriors.XenoWarriors.id("textures/gui/hud/portrait_" + who + "_normal.png");
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int y = LIST_Y;
        for (CompoundTag p : paths()) {
            if (mx >= LIST_X && mx < LIST_X + CARD_W && my >= y && my < y + CARD_H) {
                sel = p.getString("id");
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1F));
                init();
                return true;
            }
            y += CARD_H + 4;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void cards(GuiGraphics g, int mx, int my) {
        int y = LIST_Y;
        for (CompoundTag p : paths()) {
            String id = p.getString("id");
            int status = p.getInt("status"), col = pathColor(id);
            boolean on = id.equals(sel), over = mx >= LIST_X && mx < LIST_X + CARD_W && my >= y && my < y + CARD_H;
            g.fill(LIST_X, y, LIST_X + CARD_W, y + CARD_H, on ? 0xE0000000 | dim(col, 0.45F) : over ? 0xD0282834 : 0xC0181820);
            g.fill(LIST_X, y, LIST_X + 3, y + CARD_H, status == 0 ? 0xFF555560 : 0xFF000000 | col);
            if (on) outline(g, LIST_X, y, LIST_X + CARD_W, y + CARD_H, 0xFF000000 | col);
            g.blit(portrait(face(id)), LIST_X + 6, y + 3, 24, 24, 0, 0, 128, 128, 128, 128);
            if (status == 0) g.fill(LIST_X + 6, y + 3, LIST_X + 30, y + 27, 0xA0101014);
            String t = font.plainSubstrByWidth(p.getString("title"), CARD_W - 40);
            g.drawString(font, t, LIST_X + 34, y + 5, status == 0 ? 0xFF8A8A94 : 0xFFFFFFFF, false);
            var ms = p.getList("missions", Tag.TAG_COMPOUND);
            int done = 0; for (int i = 0; i < ms.size(); i++) if (ms.getCompound(i).getBoolean("done")) done++;
            String sub = status == 3 ? "Complete" : status == 2 ? "Playing - " + done + "/" + ms.size() : status == 1 ? "Available" : "Locked";
            g.drawString(font, sub, LIST_X + 34, y + 17, status == 3 ? 0xFF88CC88 : status == 2 ? 0xFFFFE080 : status == 1 ? 0xFF9ACCFF : 0xFFAA6666, false);
            y += CARD_H + 4;
        }
    }

    private static int dim(int c, float f) {
        return ((int) (((c >> 16) & 255) * f) << 16) | ((int) (((c >> 8) & 255) * f) << 8) | (int) ((c & 255) * f);
    }

    private static void outline(GuiGraphics g, int x0, int y0, int x1, int y1, int c) {
        g.fill(x0, y0, x1, y0 + 1, c); g.fill(x0, y1 - 1, x1, y1, c); g.fill(x0, y0, x0 + 1, y1, c); g.fill(x1 - 1, y0, x1, y1, c);
    }

    /** The banner of a storyline, as wide as the right column allows (2:1); returns its bottom. */
    private int bannerAt(GuiGraphics g, String path, int x, int y, String title, String sub, int col) {
        int w = Math.min(width - x - 12, 340), h = w / 2;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000 | col);
        g.blit(banner(path), x, y, w, h, 0, 0, 512, 256, 512, 256);
        g.fillGradient(x, y + h - 30, x + w, y + h, 0x00000000, 0xC0000000);
        g.drawString(font, title, x + 8, y + h - 22, 0xFFFFFFFF, true);
        if (sub != null) g.drawString(font, sub, x + 8, y + h - 11, 0xFFD8D8E0, false);
        return y + h;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fillGradient(0, 0, width, 28, 0xE0101018, 0x80101018);
        CompoundTag st = StoryClient.state;
        g.drawString(font, "STORY MODE - SEASON 1", 12, 12, 0xFFE8B030, true);
        String kl = "Your Karma Level: " + com.xenowarriors.karma.KarmaLevel.fmt(st.getLong("karma"));
        g.drawString(font, kl, width - font.width(kl) - 12, 12, 0xFFB0C8FF, true);
        cards(g, mx, my);
        if (!st.getBoolean("active")) {
            int y = bannerAt(g, "finale", 180, 34, "Season 1: the Xeno Warriors' story", "Rhalix and Mikiko - and you", 0xE8B030) + 8;
            for (String s : new String[]{"Play the Season 1 story with your own character.",
                    "Every character gets locked except yours. Meet people to call them as bots;",
                    "grow your bond with them (40, or 75 for Rhalix, Mikiko, Senju and Kyle) - or finish",
                    "their storyline - to play them. Rhalix's or Mikiko's story first, then the other,",
                    "then the Sky Rebellion and the Government, then the finale. Parties of up to 4:",
                    "the party leader's story is the one you all play."}) {
                g.drawString(font, s, 180, y, 0xFFD8D8E0, false); y += 11;
            }
            super.render(g, mx, my, pt);
            return;
        }
        // the selected storyline's missions
        CompoundTag p = sel == null ? null : path(sel);
        int x = 184, y = 34;
        if (p != null) {
            int status = p.getInt("status");
            String stt = status == 3 ? "Complete" : status == 2 ? "Playing" : status == 1 ? "Available - begin it below" : "Locked - finish the earlier storylines";
            y = bannerAt(g, sel, x, y, p.getString("title"), stt, pathColor(sel)) + 6;
            int cur = st.getInt("mi");
            var ms = p.getList("missions", Tag.TAG_COMPOUND);
            int done = 0; for (int i = 0; i < ms.size(); i++) if (ms.getCompound(i).getBoolean("done")) done++;
            int bw = Math.min(width - x - 12, 340);                                       // progress
            g.fill(x, y, x + bw, y + 5, 0xFF26262E);
            g.fill(x, y, x + Math.round(bw * (ms.isEmpty() ? 0F : done / (float) ms.size())), y + 5, 0xFF000000 | pathColor(sel));
            g.drawString(font, done + "/" + ms.size() + " missions", x + bw - font.width(done + "/" + ms.size() + " missions"), y + 7, 0xFF9098A8, false);
            y += 18;
            for (int i = 0; i < ms.size(); i++) {
                CompoundTag m = ms.getCompound(i);
                boolean done = m.getBoolean("done"), now = status == 2 && i == cur;
                int c = done ? 0xFF70B070 : now ? 0xFFFFE080 : 0xFF9098A8;
                String line = (done ? "✔ " : now ? "▶ " : "  ") + m.getString("title") + (m.getBoolean("extra") ? " (extra)" : "") + "   KL " + com.xenowarriors.karma.KarmaLevel.fmt(m.getInt("karma"));
                g.drawString(font, line, x, y, c, false);
                y += 11;
                if (now) {
                    for (var w : font.split(Component.literal(m.getString("desc")), width - x - 16)) { g.drawString(font, w, x + 12, y, 0xFFC0C0D0, false); y += 10; }
                    boolean ok = st.getLong("karma") >= m.getInt("karma");
                    g.drawString(font, ok ? "Ready (Karma Level " + com.xenowarriors.karma.KarmaLevel.fmt(m.getInt("karma")) + " needed)" : "Needs Karma Level " + com.xenowarriors.karma.KarmaLevel.fmt(m.getInt("karma")) + " - train up!", x + 12, y, ok ? 0xFF88DD88 : 0xFFE06060, false);
                    y += 12;
                }
            }
        }
        if (st.contains("obj")) g.drawString(font, "Now: " + st.getString("obj"), x, height - 52, 0xFFFFE080, true);
        if (st.contains("sideObj")) g.drawString(font, st.getString("sideObj"), x, height - 64, 0xFF80D0FF, true);
        // who you've met
        int gy = LIST_Y + paths().size() * (CARD_H + 4) + 8, gx = LIST_X, n = 0;
        g.drawString(font, "People you've met", gx, gy, 0xFFE8B030, false);
        gy += 12;
        for (Tag tt : st.getList("chars", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) tt;
            if (!c.getBoolean("met")) continue;
            CharacterType t = CharacterType.byId(c.getString("id"));
            int cx = gx + (n % 2) * 82, cy = gy + (n / 2) * 20;
            if (cy > height - 52) break;
            g.blit(portrait(t.getId()), cx, cy, 16, 16, 0, 0, 128, 128, 128, 128);
            boolean play = c.getBoolean("play");
            g.drawString(font, font.plainSubstrByWidth(t.getDisplayName().getString(), 58), cx + 19, cy, play ? 0xFF88DD88 : 0xFFD0D0D8, false);
            int need = Math.max(1, c.getInt("need"));
            g.fill(cx + 19, cy + 11, cx + 77, cy + 14, 0xFF26262E);                          // bond toward playing them
            g.fill(cx + 19, cy + 11, cx + 19 + Math.round(58 * (play ? 1F : Math.min(1F, c.getInt("bond") / (float) need))), cy + 14, play ? 0xFF60C060 : 0xFF000000 | (t.getColor() & 0xFFFFFF));
            if (mx >= cx && mx < cx + 78 && my >= cy && my < cy + 16)
                g.renderTooltip(font, Component.literal(t.getDisplayName().getString() + (play ? " - playable" : " - bond " + c.getInt("bond") + "/" + need + " to play")), mx, my);
            n++;
        }
        if (n == 0) g.drawString(font, "Nobody yet - start a storyline.", gx, gy, 0xFF9098A8, false);
        super.render(g, mx, my, pt);
    }
}
