package com.xenowarriors.client.you;

import com.xenowarriors.client.journey.JourneyScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.IntConsumer;

/**
 * Picking a You character's colour: the one its menus, HUD and name are drawn in. A swatch, or any hue and shade from
 * the two bars; "element's colour" goes back to its element's. A preview shows the menus in it.
 */
public class YouColourScreen extends JourneyScreen {
    /** The swatches (a spread of hues, then a few darker and softer ones, black-ish and white-ish). */
    static final int[] SWATCHES = {
            0xE83A3A, 0xFF7A2A, 0xFFB020, 0xFFE040, 0xA8E040, 0x40D060, 0x30D0A0, 0x30D0E0,
            0x40A0FF, 0x4060FF, 0x7A50FF, 0xB050FF, 0xF050D0, 0xFF5A90, 0xC8A0FF, 0xFFC8A0,
            0x8A1A1A, 0x8A4A10, 0x6A6A20, 0x1A6A3A, 0x1A4A8A, 0x4A2A8A, 0x8A2A6A, 0x5A5A68,
            0xFFFFFF, 0xC8D0D8, 0x8A9AAA, 0x2A2A34};

    private final String who;
    private final int element;
    private final Screen back;
    private final IntConsumer done;
    private int colour;          // -1 = the element's
    private float hue, shade = 0.5F;

    /** {@code current}: -1 for the element's colour; {@code element}: that colour. */
    public YouColourScreen(String who, int current, int element, Screen back, IntConsumer done) {
        super("Pick a colour");
        this.who = who;
        this.colour = current < 0 ? -1 : current & 0xFFFFFF;
        this.element = element & 0xFFFFFF;
        this.back = back;
        this.done = done;
        hue = hueOf(shown());
    }

    private int shown() { return colour < 0 ? element : colour; }

    /** A hue at a shade: 0 = dark, 0.5 = full, 1 = soft and pale. */
    static int hsv(float h, float shade) {
        float s = shade <= 0.5F ? 1F : 1F - (shade - 0.5F) * 1.5F;
        float v = shade <= 0.5F ? 0.25F + shade * 1.5F : 1F;
        return net.minecraft.util.Mth.hsvToRgb(h % 1F, s, v) & 0xFFFFFF;
    }

    /** The hue (0-1) of an RGB colour. */
    static float hueOf(int c) {
        float r = ((c >> 16) & 255) / 255F, g = ((c >> 8) & 255) / 255F, b = (c & 255) / 255F;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        if (d <= 0F) return 0F;
        float h = max == r ? ((g - b) / d) % 6F : max == g ? (b - r) / d + 2F : (r - g) / d + 4F;
        h /= 6F;
        return h < 0F ? h + 1F : h;
    }

    @Override
    protected void draw(GuiGraphics g) {
        int col = shown();
        sc(g, "COLOUR FOR " + who.toUpperCase(), 14, 8, 1.2F, 0xFFFFFF);
        sc(g, colour < 0 ? "Now: its element's colour" : String.format("Now: #%06X", colour), 14, 21, 0.6F, 0x9AA8B8);
        // the preview: a menu in this colour (the main menu's bar, a button, the name)
        g.fill(14, 30, 124, 150, 0xE0101820); box(g, 14, 30, 124, 150, 0xFF000000 | col);
        g.fill(15, 31, 123, 46, 0xFF000000 | YouSlotsScreen.mix(col, 0.45F));
        g.fill(15, 46, 123, 48, 0xFF000000 | col);
        sc(g, who, 20, 35, 0.8F, 0xFFFFFF);
        YouSlotsScreen.face(g, 20, 54, 40, col, null);
        sc(g, "LV 1", 66, 56, 0.6F, 0xFF000000 | col);
        g.fill(66, 66, 118, 70, 0xFF20262E); g.fill(66, 66, 98, 70, 0xFF000000 | col);
        g.fill(20, 100, 118, 114, 0xF0161C24); box(g, 20, 100, 118, 114, 0xFF000000 | col);
        sc(g, "MENU BUTTON", 69 - font.width("MENU BUTTON") * 0.6F / 2F, 104, 0.6F, 0xE0E8F0);
        g.fill(20, 120, 118, 134, 0xFF000000 | col);
        sc(g, "SELECTED", 69 - font.width("SELECTED") * 0.6F / 2F, 124, 0.6F, 0xFFFFFF);
        sc(g, "Menus, HUD and name", 20, 139, 0.5F, 0x7A8898);

        int x = 134;
        // swatches
        sc(g, "SWATCHES", x, 30, 0.6F, 0x9AA8B8);
        for (int i = 0; i < SWATCHES.length; i++) {
            int c = SWATCHES[i], sx = x + (i % 8) * 30, sy = 39 + (i / 8) * 16;
            boolean on = colour == c;
            g.fill(sx, sy, sx + 27, sy + 13, 0xFF000000 | c);
            box(g, sx, sy, sx + 27, sy + 13, on ? 0xFFFFFFFF : hover(sx, sy, sx + 27, sy + 13) ? 0xFFB0B8C0 : 0xFF000000);
            if (on) box(g, sx + 1, sy + 1, sx + 26, sy + 12, 0xFF000000);
            area(sx, sy, sx + 27, sy + 13, () -> { colour = c; hue = hueOf(c); });
        }
        // any hue
        int hy = 107;
        sc(g, "ANY COLOUR: hue, then shade", x, hy - 9, 0.6F, 0x9AA8B8);
        int bw = 236, seg = 59;
        for (int i = 0; i < seg; i++) {
            float h = i / (float) seg;
            int sx = x + i * bw / seg, ex = x + (i + 1) * bw / seg;
            g.fill(sx, hy, ex, hy + 12, 0xFF000000 | hsv(h, shade));
            area(sx, hy, ex, hy + 12, () -> { hue = h; colour = hsv(hue, shade); });
        }
        box(g, x - 1, hy - 1, x + bw + 1, hy + 13, 0xFF000000);
        int mk = x + Math.round(hue * bw);
        g.fill(mk - 1, hy - 2, mk + 1, hy + 14, 0xFFFFFFFF);
        int sy = hy + 17, sseg = 30;
        for (int i = 0; i < sseg; i++) {
            float s = i / (float) (sseg - 1);
            int sx = x + i * bw / sseg, ex = x + (i + 1) * bw / sseg;
            g.fill(sx, sy, ex, sy + 12, 0xFF000000 | hsv(hue, s));
            area(sx, sy, ex, sy + 12, () -> { shade = s; colour = hsv(hue, shade); });
        }
        box(g, x - 1, sy - 1, x + bw + 1, sy + 13, 0xFF000000);
        int sm = x + Math.round(shade * (bw - bw / sseg)) + bw / sseg / 2;
        g.fill(sm - 1, sy - 2, sm + 1, sy + 14, 0xFFFFFFFF);
        // its element's
        button(g, x, sy + 20, x + 116, sy + 34, "ELEMENT'S COLOUR", element, colour >= 0, () -> colour = -1);
        g.fill(x + 120, sy + 21, x + 133, sy + 33, 0xFF000000 | element);

        button(g, VW - 150, VH - 22, VW - 80, VH - 6, "DONE", 0x60D080, true, () -> done.accept(colour));
        button(g, VW - 70, VH - 22, VW - 10, VH - 6, "BACK", 0x9AA8B8, true, () -> minecraft.setScreen(back));
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) { minecraft.setScreen(back); return true; }
        if (key == 257 || key == 335) { done.accept(colour); return true; }
        return super.keyPressed(key, scan, mods);
    }

    /** UI test: pick a colour. */
    public void testPick(int c) { colour = c; }
}
