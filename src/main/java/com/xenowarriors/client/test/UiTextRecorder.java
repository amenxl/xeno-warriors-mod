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

    public static void add(GuiGraphics g, Font font, String s, int width, int x, int y, int color) {
        if ((color >>> 24) == 0 && (color & 0xFFFFFF) != 0) color |= 0xFF000000;      // (no alpha given: drawn opaque)
        if ((color >>> 24) < 0x20) return;                                            // (invisible: faded out)
        var m = g.pose().last().pose();
        Vector4f a = m.transform(new Vector4f(x, y, 0, 1)), b = m.transform(new Vector4f(x + width, y + font.lineHeight - 1, 0, 1));
        TEXTS.add(new Text(s, Math.min(a.x, b.x), Math.min(a.y, b.y), Math.max(a.x, b.x), Math.max(a.y, b.y), color));
    }

    public static void start() { TEXTS.clear(); on = true; }
    public static List<Text> stop() { on = false; return new ArrayList<>(TEXTS); }
}
