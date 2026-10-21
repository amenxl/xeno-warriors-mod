package com.xenowarriors.client;

import com.xenowarriors.character.CharacterType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** A small badge left of the hotbar: your character's level (1-150) and a bar toward the next one. */
public class LevelHud implements IGuiOverlay {
    private static int level;
    private static float progress;

    private static int speed;

    public static void set(int lv, float p, int spd) {
        level = lv;
        progress = p;
        speed = spd;
    }

    /** Your own Character Level. */
    public static int level() {
        return Math.max(1, level);
    }

    /** Your own Speed stat level (0-50). */
    public static int speed() {
        return speed;
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        CharacterType type = ClientCharacterData.getLocal();
        if (mc.options.hideGui || mc.player == null || type == CharacterType.NONE || mc.player.isSpectator()) return;
        Font font = mc.font;
        int color = type.getColor() | 0xFF000000;
        String lv = String.valueOf(Math.max(1, level));
        int w = Math.max(30, font.width("Lv " + lv) + 8);
        int x = width / 2 - 91 - w - 6, y = height - 22;
        g.fill(x, y, x + w, y + 20, 0xC0101018);
        g.fill(x, y, x + w, y + 1, color);
        g.drawCenteredString(font, "Lv " + lv, x + w / 2, y + 4, 0xFFFFFF);
        float p = level >= com.xenowarriors.stats.Stats.MAX_CHARACTER_LEVEL ? 1F : Mth.clamp(progress, 0F, 1F);
        g.fill(x + 3, y + 15, x + w - 3, y + 17, 0xFF2A2A34);
        g.fill(x + 3, y + 15, x + 3 + Math.round((w - 6) * p), y + 17, color);
    }
}
