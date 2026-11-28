package com.xenowarriors.client.test;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/** (UI tests) every piece of text a screen drew while recording, as on-screen boxes (scaled / moved text included). */
public final class UiTextRecorder {
    private UiTextRecorder() {}

    public static volatile boolean on;
    public record Text(String s, float x0, float y0, float x1, float y1, int color) {
        public float w() { return x1 - x0; }
        public float h() { return y1 - y0; }
    }
    public static final List<Text> TEXTS = new ArrayList<>();
    /** The clip areas (scissor) open while recording: text outside them isn't seen - only the part inside counts. */
    public static final java.util.ArrayDeque<int[]> CLIP = new java.util.ArrayDeque<>();

    public static void add(GuiGraphics g, Font font, String s, int width, int x, int y, int color) {
        if ((color >>> 24) == 0 && (color & 0xFFFFFF) != 0) color |= 0xFF000000;      // (no alpha given: drawn opaque)
        if ((color >>> 24) < 0x20) return;                                            // (invisible: faded out)
        var m = g.pose().last().pose();
        Vector4f a = m.transform(new Vector4f(x, y, 0, 1)), b = m.transform(new Vector4f(x + width, y + font.lineHeight - 1, 0, 1));
        float x0 = Math.min(a.x, b.x), y0 = Math.min(a.y, b.y), x1 = Math.max(a.x, b.x), y1 = Math.max(a.y, b.y);
        for (int[] c : CLIP) { x0 = Math.max(x0, c[0]); y0 = Math.max(y0, c[1]); x1 = Math.min(x1, c[2]); y1 = Math.min(y1, c[3]); }   // (nested: each clips further)
        if (x1 - x0 < 1 || y1 - y0 < 1) return;                                       // (hidden: outside the clip)
        TEXTS.add(new Text(s, x0, y0, x1, y1, color));
    }

    public static void start() { TEXTS.clear(); CLIP.clear(); on = true; }
    public static List<Text> stop() { on = false; return new ArrayList<>(TEXTS); }
}
