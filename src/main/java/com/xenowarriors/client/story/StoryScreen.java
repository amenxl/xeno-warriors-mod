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
        int lx = 12, y = 34;
        for (CompoundTag p : paths()) {
            String id = p.getString("id");
            int status = p.getInt("status");
            String mark = status == 3 ? " ✔" : status == 2 ? " ▶" : status == 0 ? " 🔒" : "";
            Button b = Button.builder(Component.literal(p.getString("title") + mark), x -> { sel = id; init(); }).bounds(lx, y, 150, 20).build();
            b.active = !id.equals(sel);
            addRenderableWidget(b);
            y += 23;
        }
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

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        CompoundTag st = StoryClient.state;
        g.drawString(font, "STORY MODE - SEASON 1", 12, 12, 0xFFE8B030, true);
        String kl = "Your Karma Level: " + com.xenowarriors.karma.KarmaLevel.fmt(st.getLong("karma"));
        g.drawString(font, kl, width - font.width(kl) - 12, 12, 0xFFB0C8FF, true);
        if (!st.getBoolean("active")) {
            int y = 40;
            for (String s : new String[]{"Play the Season 1 story with your own character.",
                    "Every character gets locked except yours. Meet people to call them as bots;",
                    "grow your bond with them (40, or 75 for Rhalix, Mikiko, Senju and Kyle) - or finish",
                    "their storyline - to play them. Rhalix's or Mikiko's story first, then the other,",
                    "then the Sky Rebellion and the Government, then the finale. Parties of up to 4:",
                    "the party leader's story is the one you all play."}) {
                g.drawString(font, s, 180, y, 0xFFD8D8E0, false); y += 12;
            }
            super.render(g, mx, my, pt);
            return;
        }
        // the selected storyline's missions
        CompoundTag p = sel == null ? null : path(sel);
        int x = 180, y = 34;
        if (p != null) {
            int status = p.getInt("status");
            g.drawString(font, p.getString("title"), x, y, 0xFFFFFFFF, true);
            String stt = status == 3 ? "Complete" : status == 2 ? "Playing" : status == 1 ? "Available" : "Locked - finish the earlier storylines";
            g.drawString(font, stt, x + font.width(p.getString("title")) + 8, y, status == 0 ? 0xFFAA5555 : 0xFF88CC88, false);
            y += 14;
            int cur = st.getInt("mi");
            var ms = p.getList("missions", Tag.TAG_COMPOUND);
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
        int gy = Math.max(y + 8, 34 + paths().size() * 23 + 8), gx = 12, n = 0;
        g.drawString(font, "People you've met (bond / needed to play):", gx, gy, 0xFFE8B030, false);
        gy += 12;
        for (Tag tt : st.getList("chars", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) tt;
            if (!c.getBoolean("met")) continue;
            CharacterType t = CharacterType.byId(c.getString("id"));
            int cx = gx + (n % 4) * 110, cy = gy + (n / 4) * 12;
            if (cy > height - 40) break;
            String s = t.getDisplayName().getString() + " " + (c.getBoolean("play") ? "✔" : c.getInt("bond") + "/" + c.getInt("need"));
            g.drawString(font, s, cx, cy, c.getBoolean("play") ? 0xFF88DD88 : 0xFFD0D0D8, false);
            n++;
        }
        if (n == 0) g.drawString(font, "Nobody yet - start a storyline.", gx, gy, 0xFF9098A8, false);
        super.render(g, mx, my, pt);
    }
}
