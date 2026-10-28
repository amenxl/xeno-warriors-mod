package com.xenowarriors.client.journey;

import com.mojang.blaze3d.systems.RenderSystem;
import com.xenowarriors.character.CharacterType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Shared look for the Journey screens: a 400x225 grid scaled to the window, simple drawn buttons, character faces. */
public abstract class JourneyScreen extends Screen {
    protected static final int VW = 400, VH = 225;
    protected static final int ACCENT = 0x40D0FF;
    private record Btn(float x0, float y0, float x1, float y1, Runnable run) {}
    private final List<Btn> btns = new ArrayList<>();
    protected double vx, vy;
    private Boolean hudWas;

    protected JourneyScreen(String title) { super(Component.literal(title)); }

    @Override protected void init() { if (hudWas == null) { hudWas = minecraft.options.hideGui; minecraft.options.hideGui = true; } }
    @Override public void removed() { if (hudWas != null) { minecraft.options.hideGui = hudWas; hudWas = null; } }
    @Override public boolean isPauseScreen() { return false; }

    private float scale() { return Math.min(width / (float) VW, height / (float) VH); }
    private float[] origin() { float s = scale(); return new float[]{(width - VW * s) / 2F, (height - VH * s) / 2F}; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fillGradient(0, 0, width, height, 0xE0081018, 0xE0040608);
        float s = scale(); float[] o = origin();
        vx = (mx - o[0]) / s; vy = (my - o[1]) / s;
        btns.clear();
        g.pose().pushPose();
        g.pose().translate(o[0], o[1], 0);
        g.pose().scale(s, s, 1F);
        draw(g);
        g.pose().popPose();
    }

    protected abstract void draw(GuiGraphics g);

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        float s = scale(); float[] o = origin();
        double x = (mx - o[0]) / s, y = (my - o[1]) / s;
        for (int i = btns.size() - 1; i >= 0; i--) {
            Btn b = btns.get(i);
            if (x >= b.x0 && x < b.x1 && y >= b.y0 && y < b.y1) {
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.4F, 0.3F));
                b.run.run();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    protected boolean hover(float x0, float y0, float x1, float y1) { return vx >= x0 && vx < x1 && vy >= y0 && vy < y1; }

    /** A clickable area (no drawing). */
    protected void area(float x0, float y0, float x1, float y1, Runnable r) { btns.add(new Btn(x0, y0, x1, y1, r)); }

    protected void button(GuiGraphics g, int x0, int y0, int x1, int y1, String label, int color, boolean enabled, Runnable r) {
        boolean h = enabled && hover(x0, y0, x1, y1);
        g.fill(x0, y0, x1, y1, enabled ? (h ? 0xF0283440 : 0xF0161C24) : 0xC0101014);
        box(g, x0, y0, x1, y1, enabled ? 0xFF000000 | color : 0xFF3A3A44);
        float k = Math.min(1F, (x1 - x0 - 6) / (float) Math.max(1, font.width(label)));
        sc(g, label, (x0 + x1) / 2F - font.width(label) * k / 2F, (y0 + y1) / 2F - 4 * k, k, enabled ? (h ? 0xFFFFFF : 0xE0E8F0) : 0x6A6A74);
        if (enabled) area(x0, y0, x1, y1, r);
    }

    protected static void box(GuiGraphics g, int x0, int y0, int x1, int y1, int c) {
        g.fill(x0, y0, x1, y0 + 1, c); g.fill(x0, y1 - 1, x1, y1, c);
        g.fill(x0, y0, x0 + 1, y1, c); g.fill(x1 - 1, y0, x1, y1, c);
    }

    protected void sc(GuiGraphics g, String s, float x, float y, float k, int c) { sc(g, font, s, x, y, k, c); }

    public static void sc(GuiGraphics g, Font font, String s, float x, float y, float k, int c) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(k, k, 1F);
        g.drawString(font, s, 0, 0, 0xFF000000 | c, true);
        g.pose().popPose();
    }

    /** The character's face from their skin (with the hat layer). */
    public static void face(GuiGraphics g, CharacterType t, int x, int y, int size) {
        if (t == CharacterType.NONE) { g.fill(x, y, x + size, y + size, 0xFF303038); return; }
        RenderSystem.enableBlend();
        g.blit(t.getSkin(), x, y, size, size, 8F, 8F, 8, 8, 64, 64);
        g.blit(t.getSkin(), x, y, size, size, 40F, 8F, 8, 8, 64, 64);
        RenderSystem.disableBlend();
    }

    /** A health bar: red when low, a grey "DOWN" when 0. */
    public static void hpBar(GuiGraphics g, int x, int y, int w, int h, float hp) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        g.fill(x, y, x + w, y + h, 0xFF2A1414);
        if (hp > 0) g.fill(x, y, x + Math.max(1, Math.round(w * Math.min(1F, hp))), y + h, hp > 0.5F ? 0xFF50D060 : hp > 0.25F ? 0xFFE0A030 : 0xFFE03030);
    }

    protected static String name(CharacterType t) { return t.getDisplayName().getString(); }
}
