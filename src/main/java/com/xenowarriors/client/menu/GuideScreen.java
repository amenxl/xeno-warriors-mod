package com.xenowarriors.client.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import com.xenowarriors.client.ModKeyMappings;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Xeno Warriors Guide: how to play, chapter by chapter - from the guide book item and the main menu's Guide entry.
 * All the text lives in assets/xenowarriors/guide/guide.json (so it's easy to keep up to date: add a page there when
 * something new is added). Chapters down the left, the page on the right; scroll for more, arrows / Q E for pages.
 * Laid out on a 400x225 grid scaled to the window, in your character's colour.
 */
public class GuideScreen extends Screen {
    private static final int VW = 400, VH = 225, LIST_W = 112, TEXT_X = LIST_W + 16, TEXT_W = VW - TEXT_X - 10, TOP = 30;
    private static final Pattern KEY = Pattern.compile("\\{key:([a-z0-9_]+)}");
    private final Screen parent;
    private Boolean hudWas;
    private long turned = net.minecraft.Util.getMillis();

    public record Page(String title, String text) {}
    public record Chapter(String title, List<Page> pages) {}

    private final List<Chapter> chapters = load();
    private int chapter, page, scroll;
    /**
     * The chapter list scrolls when the open chapter has more pages than fit in its column (Characters, Progression
     * and DragonMineZ ran off the bottom of the screen, out of reach of the mouse).
     */
    private int listScroll;
    private static final int LIST_BOTTOM = VH - 6;

    private int listHeight() {
        int h = chapters.size() * 12;
        if (!chapters.isEmpty()) h += chapters.get(chapter).pages().size() * 9;
        return h;
    }

    private int maxListScroll() { return Math.max(0, listHeight() - (LIST_BOTTOM - TOP)); }

    /** Keeps the open page's row in view. */
    private void followPage() {
        int y = (chapter + 1) * 12 + page * 9;            // the page row's top, from the top of the list
        int view = LIST_BOTTOM - TOP;
        if (y - listScroll < 0) listScroll = y - 12;
        else if (y + 9 - listScroll > view) listScroll = y + 9 - view;
        listScroll = Mth.clamp(listScroll, 0, maxListScroll());
    }

    public GuideScreen(Screen parent) { super(Component.literal("Guide")); this.parent = parent; }

    public static void open(Screen parent) { Minecraft.getInstance().setScreen(new GuideScreen(parent)); }

    // ------------------------------------------------------------------ the data

    /** Reads the guide (chapters / pages shown for this game: installed mods, unlocked seasons). */
    public static List<Chapter> load() {
        List<Chapter> out = new ArrayList<>();
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(XenoWarriors.id("guide/guide.json"));
            if (res.isEmpty()) return out;
            try (Reader r = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                for (JsonElement ce : root.getAsJsonArray("chapters")) {
                    JsonObject c = ce.getAsJsonObject();
                    if (!shown(c)) continue;
                    List<Page> pages = new ArrayList<>();
                    JsonArray ps = c.getAsJsonArray("pages");
                    for (JsonElement pe : ps) {
                        JsonObject p = pe.getAsJsonObject();
                        if (!shown(p)) continue;
                        pages.add(new Page(p.get("title").getAsString(), p.get("text").getAsString()));
                    }
                    if (!pages.isEmpty()) out.add(new Chapter(c.get("title").getAsString(), pages));
                }
            }
        } catch (Exception e) {
            XenoWarriors.LOGGER.warn("Couldn't read the guide: {}", e.toString());
        }
        return out;
    }

    private static boolean shown(JsonObject o) {
        if (o.has("requires") && !net.minecraftforge.fml.ModList.get().isLoaded(o.get("requires").getAsString())) return false;
        return !o.has("season") || com.xenowarriors.client.ClientSkillState.season() >= o.get("season").getAsInt();
    }

    /** {key:NAME} -> the player's key for it. */
    static String keys(String s) {
        Matcher m = KEY.matcher(s);
        StringBuilder b = new StringBuilder();
        while (m.find()) {
            String name = "key.xenowarriors." + m.group(1), shown = "?";
            for (KeyMapping k : Minecraft.getInstance().options.keyMappings)
                if (k.getName().equals(name)) { shown = k.getTranslatedKeyMessage().getString(); break; }
            m.appendReplacement(b, Matcher.quoteReplacement("[" + shown + "]"));
        }
        m.appendTail(b);
        return b.toString();
    }

    public int chapters() { return chapters.size(); }

    /** UI test: show this page of this chapter. */
    public void show(int c, int p) {
        if (chapters.isEmpty()) return;
        chapter = Mth.clamp(c, 0, chapters.size() - 1);
        page = Mth.clamp(p, 0, chapters.get(chapter).pages().size() - 1);
        scroll = 0;
        turned = net.minecraft.Util.getMillis();
        followPage();
    }

    /** UI test: the title of the page showing. */
    public String pageTitle() { return chapters.isEmpty() ? "" : chapters.get(chapter).pages().get(page).title(); }

    private void turn(int d) {
        if (chapters.isEmpty()) return;
        int c = chapter, p = page + d;
        if (p < 0) { c = (c + chapters.size() - 1) % chapters.size(); p = chapters.get(c).pages().size() - 1; }
        else if (p >= chapters.get(c).pages().size()) { c = (c + 1) % chapters.size(); p = 0; }
        show(c, p);
        minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1F, 0.8F));
    }

    // ------------------------------------------------------------------ screen

    @Override protected void init() { if (hudWas == null && minecraft.level != null) { hudWas = minecraft.options.hideGui; minecraft.options.hideGui = true; } }
    @Override public void removed() { if (hudWas != null) { minecraft.options.hideGui = hudWas; hudWas = null; } }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(parent); }

    private float scale() { return Math.min(width / (float) VW, height / (float) VH); }
    private float[] origin() { float s = scale(); return new float[]{(width - VW * s) / 2F, (height - VH * s) / 2F}; }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ModKeyMappings.STATS_MENU.matches(key, scan)) { minecraft.setScreen(null); return true; }
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_E || key == GLFW.GLFW_KEY_D) { turn(1); return true; }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_Q || key == GLFW.GLFW_KEY_A) { turn(-1); return true; }
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_S) { scroll += 16; return true; }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_W) { scroll = Math.max(0, scroll - 16); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double d) {
        float s = scale(); float[] o = origin();
        double vx = (mx - o[0]) / s;
        if (vx > TEXT_X - 4) scroll = Math.max(0, scroll - (int) Math.signum(d) * 12);
        else listScroll = Mth.clamp(listScroll - (int) Math.signum(d) * 18, 0, maxListScroll());   // (the chapter list)
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        float s = scale(); float[] o = origin();
        double vx = (mx - o[0]) / s, vy = (my - o[1]) / s;
        if (vx < 30 && vy < 22) { onClose(); return true; }
        // chapter list (with the open chapter's pages under it)
        int y = TOP - listScroll;
        boolean inList = vy >= TOP - 2 && vy < LIST_BOTTOM;
        for (int c = 0; c < chapters.size() && inList; c++) {
            if (vx >= 6 && vx < LIST_W && vy >= y && vy < y + 12) { show(c, 0); click(); return true; }
            y += 12;
            if (c == chapter) for (int p = 0; p < chapters.get(c).pages().size(); p++) {
                if (vx >= 10 && vx < LIST_W && vy >= y && vy < y + 9) { show(c, p); click(); return true; }
                y += 9;
            }
        }
        if (vy >= VH - 18 && vy < VH - 6) {
            if (vx >= TEXT_X && vx < TEXT_X + 40) { turn(-1); return true; }
            if (vx >= VW - 50 && vx < VW - 10) { turn(1); return true; }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void click() {
        minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.4F, 0.3F));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        CharacterType type = minecraft.player == null ? CharacterType.NONE : ClientCharacterData.getLocal();
        int col = type == CharacterType.NONE ? 0x9A7AE0 : type.getColor() & 0xFFFFFF;
        g.fillGradient(0, 0, width, height, 0xF0101016, 0xF0060608);
        float s = scale(); float[] o = origin();
        double vx = (mx - o[0]) / s, vy = (my - o[1]) / s;
        g.pose().pushPose();
        g.pose().translate(o[0], o[1], 0);
        g.pose().scale(s, s, 1F);
        // header
        g.fill(4, 4, 26, 20, 0xFF16161C);
        box(g, 4, 4, 26, 20, 0xFF000000 | col);
        g.drawString(font, "↶", 11, 8, vx < 30 && vy < 22 ? 0xFFFFFF : col, false);
        g.fill(30, 4, 30 + font.width("GUIDE") * 2 + 12, 20, 0xFF16161C);
        sc(g, "GUIDE", 36, 5.5F, 2F, 0xFFFFFF);
        String sub = "How to play Xeno Warriors Recarnet";
        sc(g, sub, VW - 6 - font.width(sub) * 0.6F, 10, 0.6F, 0x9A9AAA);
        if (chapters.isEmpty()) { g.drawCenteredString(font, "The guide couldn't be loaded.", VW / 2, VH / 2, 0xFF8080); g.pose().popPose(); return; }

        // chapters
        g.fill(4, TOP - 4, LIST_W + 2, VH - 4, 0xE0141418);
        listScroll = Mth.clamp(listScroll, 0, maxListScroll());
        g.enableScissor((int) (o[0] + 4 * s), (int) (o[1] + (TOP - 3) * s), (int) (o[0] + (LIST_W + 2) * s), (int) (o[1] + LIST_BOTTOM * s));
        int y = TOP - listScroll;
        for (int c = 0; c < chapters.size(); c++) {
            boolean sel = c == chapter, hov = vx >= 6 && vx < LIST_W && vy >= y && vy < y + 12;
            if (sel) g.fill(6, y - 1, LIST_W, y + 11, 0xFF000000 | dark(col));
            if (sel) g.fill(6, y - 1, 8, y + 11, 0xFF000000 | col);
            sc(g, chapters.get(c).title(), 11, y + 1.5F, 0.85F, sel ? 0xFFFFFF : hov ? 0xE0E0FF : 0xB0B0C0);
            y += 12;
            if (sel) for (int p = 0; p < chapters.get(c).pages().size(); p++) {
                boolean ps = p == page, ph = vx >= 10 && vx < LIST_W && vy >= y && vy < y + 9;
                String t = chapters.get(c).pages().get(p).title();
                sc(g, (ps ? "▸ " : "  ") + font.plainSubstrByWidth(t, (int) ((LIST_W - 22) / 0.65F)), 13, y + 1.5F, 0.65F, ps ? light(col) : ph ? 0xE0E0FF : 0x8A8A9A);
                y += 9;
            }
        }
        g.disableScissor();
        if (maxListScroll() > 0) {                     // more chapters above / below: a little arrow on the list's edge
            if (listScroll > 0) sc(g, "▲", LIST_W - 8, TOP - 3, 0.6F, 0x8899AA);
            if (listScroll < maxListScroll()) sc(g, "▼", LIST_W - 8, LIST_BOTTOM - 5, 0.6F, 0x8899AA);
        }

        // the page
        Page pg = chapters.get(chapter).pages().get(page);
        float in = Math.min(1F, (net.minecraft.Util.getMillis() - turned) / 200F);
        g.fill(TEXT_X - 6, TOP - 4, VW - 4, VH - 4, 0xE0181820);
        g.fill(TEXT_X - 6, TOP - 4, TEXT_X - 4, VH - 4, 0xFF000000 | col);
        g.pose().pushPose();
        g.pose().translate((1F - in) * 14F, 0, 0);
        sc(g, chapters.get(chapter).title().toUpperCase(java.util.Locale.ROOT), TEXT_X, TOP, 0.6F, 0x8A8A9A);
        sc(g, pg.title(), TEXT_X, TOP + 7, 1.3F, 0xFFFFFF);
        int top = TOP + 22, bottom = VH - 22;
        List<Object[]> lines = layout(pg.text());               // {FormattedCharSequence, colour, indent, scale}
        int total = 0;
        for (Object[] l : lines) total += (Integer) l[4];
        scroll = Mth.clamp(scroll, 0, Math.max(0, total - (bottom - top)));
        g.enableScissor((int) (o[0] + (TEXT_X - 4) * s), (int) (o[1] + top * s), (int) (o[0] + (VW - 4) * s), (int) (o[1] + bottom * s));
        int ly = top - scroll;
        for (Object[] l : lines) {
            int h = (Integer) l[4];
            if (ly + h >= top && ly < bottom) {
                g.pose().pushPose();
                g.pose().translate(TEXT_X + (Integer) l[2], ly, 0);
                float k = (Float) l[3];
                g.pose().scale(k, k, 1F);
                g.drawString(font, (FormattedCharSequence) l[0], 0, 0, (Integer) l[1], false);
                g.pose().popPose();
            }
            ly += h;
        }
        g.disableScissor();
        if (total > bottom - top) sc(g, scroll < total - (bottom - top) ? "▼ scroll" : "▲", VW - 32, bottom - 1, 0.6F, 0x8899AA);
        g.pose().popPose();
        // page turning
        int n = 0, at = 0;
        for (int c = 0; c < chapters.size(); c++) { if (c == chapter) at = n + page; n += chapters.get(c).pages().size(); }
        boolean hp = vy >= VH - 18 && vy < VH - 6 && vx >= TEXT_X && vx < TEXT_X + 40, hn = vy >= VH - 18 && vy < VH - 6 && vx >= VW - 50 && vx < VW - 10;
        sc(g, "◀ Prev", TEXT_X, VH - 15, 0.75F, hp ? 0xFFFFFF : light(col));
        sc(g, "Next ▶", VW - 12 - font.width("Next ▶") * 0.75F, VH - 15, 0.75F, hn ? 0xFFFFFF : light(col));
        String pn = (at + 1) + " / " + n + "    Q / E: pages";
        sc(g, pn, (TEXT_X + VW) / 2F - font.width(pn) * 0.3F, VH - 14, 0.6F, 0x8A8A9A);
        g.pose().popPose();
    }

    /** The page's text as wrapped lines: '## ' a heading, '- ' a bullet; {key:...} filled in. */
    private List<Object[]> layout(String text) {
        List<Object[]> out = new ArrayList<>();
        float k = 0.75F;
        for (String raw : keys(text).split("\n")) {
            if (raw.startsWith("## ")) {
                out.add(new Object[]{FormattedCharSequence.EMPTY, 0, 0, k, 3});
                for (FormattedCharSequence l : font.split(Component.literal(raw.substring(3)), (int) (TEXT_W / 0.85F)))
                    out.add(new Object[]{l, 0xFFD878, 0, 0.85F, 10});
            } else if (raw.startsWith("- ")) {
                List<FormattedCharSequence> ls = font.split(Component.literal(raw.substring(2)), (int) ((TEXT_W - 8) / k));
                for (int i = 0; i < ls.size(); i++) {
                    if (i == 0) out.add(new Object[]{Component.literal("•").getVisualOrderText(), 0xFFFFFF, 2, k, 0});
                    out.add(new Object[]{ls.get(i), 0xE0E0E8, 8, k, 8});
                }
            } else {
                for (FormattedCharSequence l : font.split(Component.literal(raw), (int) (TEXT_W / k))) out.add(new Object[]{l, 0xE8E8F0, 0, k, 8});
            }
        }
        return out;
    }

    private static int dark(int c) { int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255; return ((r * 30 / 100) << 16) | ((g * 30 / 100) << 8) | (b * 30 / 100); }
    private static int light(int c) { int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255; return ((r + (255 - r) * 45 / 100) << 16) | ((g + (255 - g) * 45 / 100) << 8) | (b + (255 - b) * 45 / 100); }

    private static void box(GuiGraphics g, int x0, int y0, int x1, int y1, int c) {
        g.fill(x0, y0, x1, y0 + 1, c); g.fill(x0, y1 - 1, x1, y1, c);
        g.fill(x0, y0, x0 + 1, y1, c); g.fill(x1 - 1, y0, x1, y1, c);
    }

    private void sc(GuiGraphics g, String s, float x, float y, float k, int c) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(k, k, 1F);
        g.drawString(font, s, 0, 0, c, false);
        g.pose().popPose();
    }
}
