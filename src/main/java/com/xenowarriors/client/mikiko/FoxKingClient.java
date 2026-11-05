package com.xenowarriors.client.mikiko;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** Which Mikikos are in Fox King mode right now (drawn with the Fox King hair and mask), and your own Fox King mastery. */
public final class FoxKingClient {
    private static final IntSet ACTIVE = new IntOpenHashSet();
    /** Your Fox King mastery (0-100). */
    public static float mastery;
    /** While you're in the form: the game time it ends at and its full length in ticks. */
    private static long endsAt = -1;
    private static int total = 1;

    private FoxKingClient() {}

    public static void set(int id, boolean on) {
        if (on) ACTIVE.add(id);
        else ACTIVE.remove(id);
    }

    public static boolean active(Entity e) {
        return ACTIVE.contains(e.getId());
    }

    public static void mastery(float m, int left, int full) {
        mastery = m;
        var mc = Minecraft.getInstance();
        endsAt = left > 0 && mc.level != null ? mc.level.getGameTime() + left : -1;
        total = Math.max(1, full);
    }

    /** Ticks of Fox King you have left (0 = not in it). */
    public static int left() {
        var mc = Minecraft.getInstance();
        return endsAt < 0 || mc.level == null ? 0 : (int) Math.max(0, endsAt - mc.level.getGameTime());
    }

    public static int total() { return total; }

    /** The Fox King timer while you're in the form: "FOX KING 2:41" over a draining red bar, with your mastery. */
    public static class Overlay implements IGuiOverlay {
        @Override
        public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
            Minecraft mc = Minecraft.getInstance();
            int left = left();
            if (left <= 0 || mc.player == null || mc.options.hideGui || !active(mc.player)) return;
            int w = 90, x = width / 2 - w / 2, y = height - 76;
            float frac = Mth.clamp(left / (float) total, 0F, 1F);
            int s = (left + 19) / 20;
            String time = "FOX KING  " + s / 60 + ":" + String.format("%02d", s % 60);
            g.drawCenteredString(mc.font, time, width / 2, y - 10, left < 200 && (mc.level.getGameTime() / 5) % 2 == 0 ? 0xFFFFFF : 0xFF6A3A);
            g.fill(x - 1, y - 1, x + w + 1, y + 4, 0xC0101010);
            g.fill(x, y, x + Math.round(w * frac), y + 3, 0xFFE8361C);
            g.fill(x, y, x + Math.round(w * frac), y + 1, 0xFFFFA070);
            String m = "Mastery " + (int) mastery + (mastery >= 100 ? " (MAX)" : "");
            g.pose().pushPose();
            g.pose().translate(width / 2F - mc.font.width(m) * 0.35F, y + 6, 0);
            g.pose().scale(0.7F, 0.7F, 1F);
            g.drawString(mc.font, m, 0, 0, 0xFFD27A);
            g.pose().popPose();
        }
    }
}
