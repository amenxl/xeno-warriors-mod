package com.xenowarriors.client;

import com.xenowarriors.stats.Stat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayList;
import java.util.List;

/** "Speed ▲ Lv 5" cards that slide in at the top right when a stat levels up. */
public class StatToasts implements IGuiOverlay {
    private static final long SHOW_MS = 3500, SLIDE_MS = 250;
    private static final int[] COLORS = {0xFF5CE1E6, 0xFFFF5A4A, 0xFF7CD65A};
    private static final String[] ICONS = {"»", "⚔", "⛨"};
    private static final List<long[]> TOASTS = new ArrayList<>();   // {stat, level, start ms}

    public static void add(int stat, int level) {
        TOASTS.removeIf(t -> t[0] == stat);
        TOASTS.add(new long[]{stat, level, Util.getMillis()});
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        if (TOASTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || UltimateCutscene.active()) return;
        long now = Util.getMillis();
        TOASTS.removeIf(t -> now - t[2] > SHOW_MS);
        Font font = mc.font;
        int y = 6;
        for (long[] t : TOASTS) {
            Stat stat = Stat.values()[(int) t[0]];
            int level = (int) t[1];
            long age = now - t[2];
            float in = Mth.clamp(age / (float) SLIDE_MS, 0F, 1F), out = Mth.clamp((SHOW_MS - age) / (float) SLIDE_MS, 0F, 1F);
            float slide = Math.min(in, out);
            slide = slide * slide * (3 - 2 * slide);

            Component title = Component.translatable("stat.xenowarriors.levelup",
                    Component.translatable("stat.xenowarriors." + stat.id()), level);
            String bonus = switch (stat) {
                case SPEED -> "+" + Math.round(level * stat.bonusPerLevel * 100) + "% speed";
                case ATTACK -> "+" + Math.round(level * stat.bonusPerLevel * 100) + "% damage";
                case DEFENSE -> "-" + Math.round(level * stat.bonusPerLevel * 100) + "% damage taken";
            };
            int w = Math.max(font.width(title), font.width(bonus)) + 30;
            int x = width - 6 - Math.round(w * slide);
            int color = COLORS[stat.ordinal()];
            g.fill(x, y, x + w, y + 26, 0xD0101018);
            g.fill(x, y, x + 2, y + 26, color);
            g.drawString(font, ICONS[stat.ordinal()], x + 8, y + 9, color);
            g.drawString(font, title, x + 20, y + 4, 0xFFFFFF);
            g.drawString(font, bonus, x + 20, y + 15, color & 0xFFFFFF | 0xC0000000);
            y += 30;
        }
    }
}
