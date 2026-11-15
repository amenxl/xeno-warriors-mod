package com.xenowarriors.client.menu;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The menus' shared look (the Pets and Character Customization menus' style): a dark backdrop, flat buttons in your
 * character's colour (lighter under the mouse, dark when they can't be pressed), labels shrunk to fit their button.
 * {@link #builder} is a drop-in for {@link Button#builder}.
 */
public final class Flat {
    private Flat() {}

    /** Your character's colour (grey without one). */
    public static int col() {
        CharacterType t = ClientCharacterData.getLocal();
        if (t == CharacterType.YOU && Minecraft.getInstance().player != null) return ClientCharacterData.color(Minecraft.getInstance().player) & 0xFFFFFF;
        return t == CharacterType.NONE ? 0x6A7A8C : t.getColor() & 0xFFFFFF;
    }

    public static int light(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return ((r + (255 - r) * 45 / 100) << 16) | ((g + (255 - g) * 45 / 100) << 8) | (b + (255 - b) * 45 / 100);
    }

    public static int dark(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return ((r * 30 / 100) << 16) | ((g * 30 / 100) << 8) | (b * 30 / 100);
    }

    /** A colour too dark to read white text on gets lifted (Ultima's black, Kill's...). */
    private static int readable(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return r * 3 + g * 6 + b < 260 ? light(light(c)) : c;
    }

    /** The backdrop behind a menu (instead of Minecraft's dirt / grey). */
    public static void background(Screen s, GuiGraphics g) {
        g.fillGradient(0, 0, s.width, s.height, 0xF0101016, 0xF0060608);
    }

    public static void box(GuiGraphics g, int x0, int y0, int x1, int y1, int c) {
        g.fill(x0, y0, x1, y0 + 1, c); g.fill(x0, y1 - 1, x1, y1, c);
        g.fill(x0, y0, x0 + 1, y1, c); g.fill(x1 - 1, y0, x1, y1, c);
    }

    /** Like Button.builder, but the button is drawn flat in your colour. */
    public static Button.Builder builder(Component message, Button.OnPress press) {
        return new Builder(message, press);
    }

    private static final class Builder extends Button.Builder {
        Builder(Component message, Button.OnPress press) { super(message, press); }
        @Override public Button build() { return build(FlatButton::new); }
    }

    public static final class FlatButton extends Button {
        FlatButton(Button.Builder b) { super(b); }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            int c = readable(col());
            boolean over = active && isHoveredOrFocused();
            int bg = !active ? 0xFF000000 | dark(c) : over ? 0xFF000000 | light(c) : 0xFF000000 | c;
            g.fill(getX(), getY(), getX() + width, getY() + height, (int) (alpha * 255) << 24 | (bg & 0xFFFFFF));
            if (!active) box(g, getX(), getY(), getX() + width, getY() + height, 0xFF000000 | c);   // (the tab you're on / an option that can't change now)
            var font = Minecraft.getInstance().font;
            String label = getMessage().getString();
            float k = Math.min(1F, (width - 6) / Math.max(1F, font.width(label)));
            g.pose().pushPose();
            g.pose().translate(getX() + width / 2F, getY() + height / 2F - 4 * k, 0);
            g.pose().scale(k, k, 1F);
            g.drawCenteredString(font, getMessage(), 0, 0, !active ? light(c) : over ? 0x101010 : 0xFFFFFF);
            g.pose().popPose();
        }
    }
}
