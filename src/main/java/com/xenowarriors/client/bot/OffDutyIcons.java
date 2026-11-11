package com.xenowarriors.client.bot;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Small animated badges for what an off-duty character is up to (bot.OffDuty.Act ordinals), drawn over its portrait:
 * a swinging pickaxe / axe, clashing swords, rising Zzz, falling confetti, flying ki blasts, bricks stacking up.
 * Just item icons and fills moved by the clock - nothing loaded.
 */
public final class OffDutyIcons {
    private OffDutyIcons() {}

    public static final int TRAINING = 1, RELAX_DOWNED = 2, RELAX_LONG = 3, RELAX = 4, PARTY = 5, DMZ = 6, MINING = 7, WOOD = 8, BUILDING = 9;
    public static final int SIZE = 20;

    /** The label's colour for an activity. */
    public static int color(int act) {
        return switch (act) {
            case TRAINING -> 0xFFFF9A5A; case RELAX_DOWNED, RELAX_LONG, RELAX -> 0xFF9FC8FF; case PARTY -> 0xFFFF7FD8;
            case DMZ -> 0xFFFFE04A; case MINING -> 0xFFD8C090; case WOOD -> 0xFFB98A50; case BUILDING -> 0xFF8BE07A; default -> 0xFFAAAAAA;
        };
    }

    /** The badge, top-left corner at (x, y), SIZE square. */
    public static void draw(GuiGraphics g, int act, int x, int y, long ms) {
        if (act <= 0) return;
        g.fill(x, y, x + SIZE, y + SIZE, 0xD0101018);
        g.fill(x, y, x + SIZE, y + 1, color(act)); g.fill(x, y + SIZE - 1, x + SIZE, y + SIZE, color(act));
        g.fill(x, y, x + 1, y + SIZE, color(act)); g.fill(x + SIZE - 1, y, x + SIZE, y + SIZE, color(act));
        float t = ms / 1000F;
        g.enableScissor(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1);
        switch (act) {
            case MINING -> { swing(g, new ItemStack(Items.IRON_PICKAXE), x, y, t, 1F); chips(g, x, y, t, 0xFF8A8A8A); }
            case WOOD -> { swing(g, new ItemStack(Items.IRON_AXE), x, y, t, 1F); chips(g, x, y, t, 0xFF9A6A30); }
            case TRAINING -> { clash(g, x, y, t); }
            case RELAX_DOWNED, RELAX_LONG, RELAX -> zzz(g, x, y, t);
            case PARTY -> confetti(g, x, y, t);
            case DMZ -> ki(g, x, y, t);
            case BUILDING -> bricks(g, x, y, t);
            default -> {}
        }
        g.disableScissor();
    }

    private static void item(GuiGraphics g, ItemStack s, float cx, float cy, float deg, float scale) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 120);
        g.pose().mulPose(Axis.ZP.rotationDegrees(deg));
        g.pose().scale(scale, scale, 1F);
        g.renderItem(s, -8, -8);
        g.pose().popPose();
    }

    /** A tool chopping down at the bottom-left corner. */
    private static void swing(GuiGraphics g, ItemStack s, int x, int y, float t, float k) {
        float ph = (t * 2.2F) % 1F;
        float deg = ph < 0.35F ? -50F + ph / 0.35F * 85F : 35F - (ph - 0.35F) / 0.65F * 85F;
        item(g, s, x + 11, y + 9, deg, 0.85F * k);
    }

    private static void chips(GuiGraphics g, int x, int y, float t, int col) {
        float ph = (t * 2.2F) % 1F;
        if (ph < 0.35F || ph > 0.75F) return;
        float f = (ph - 0.35F) / 0.4F;
        for (int i = 0; i < 3; i++) {
            int px = x + 6 + Math.round((i - 1) * 4 * f), py = y + 16 - Math.round(5 * f * (1 - f) * 4) + i;
            g.fill(px, py, px + 2, py + 2, col);
        }
    }

    /** Two swords meeting (sparring). */
    private static void clash(GuiGraphics g, int x, int y, float t) {
        float ph = (float) Math.abs(Math.sin(t * 4F));
        item(g, new ItemStack(Items.IRON_SWORD), x + 7 + ph * 1.5F, y + 10, -20F + ph * 30F, 0.7F);
        item(g, new ItemStack(Items.WOODEN_SWORD), x + 13 - ph * 1.5F, y + 10, 110F - ph * 30F, 0.7F);
        if (ph > 0.9F) { g.fill(x + 9, y + 4, x + 11, y + 6, 0xFFFFFFA0); g.fill(x + 8, y + 5, x + 12, y + 5 + 0, 0xFFFFFFA0); }
    }

    private static void zzz(GuiGraphics g, int x, int y, float t) {
        var font = Minecraft.getInstance().font;
        for (int i = 0; i < 3; i++) {
            float ph = (t * 0.5F + i / 3F) % 1F;
            float s = 0.45F + ph * 0.45F;
            int a = Math.round(255 * Mth.clamp(1.2F - ph, 0, 1));
            g.pose().pushPose();
            g.pose().translate(x + 4 + ph * 9, y + 16 - ph * 14, 150);
            g.pose().scale(s, s, 1F);
            g.drawString(font, "z", 0, 0, (a << 24) | 0x9FC8FF, false);
            g.pose().popPose();
        }
    }

    private static final int[] CONFETTI = {0xFFFF5A5A, 0xFFFFD84A, 0xFF5AD8FF, 0xFF7AFF6A, 0xFFFF7FD8, 0xFFB08AFF};

    private static void confetti(GuiGraphics g, int x, int y, float t) {
        for (int i = 0; i < 12; i++) {
            float speed = 0.5F + (i * 37 % 10) / 20F;
            float ph = (t * speed + i * 0.137F) % 1F;
            int px = x + 2 + (i * 53 % 15) + Math.round((float) Math.sin(t * 3 + i) * 1.5F);
            int py = y + 1 + Math.round(ph * 17);
            int w = (i & 1) == 0 ? 2 : 1;
            g.fill(px, py, px + w, py + 3 - w, CONFETTI[i % CONFETTI.length]);
        }
    }

    /** Ki blasts flying across, with short trails. */
    private static void ki(GuiGraphics g, int x, int y, float t) {
        for (int i = 0; i < 3; i++) {
            float ph = (t * 1.3F + i / 3F) % 1F;
            int px = x + 1 + Math.round(ph * 18), py = y + 4 + i * 5 + Math.round((float) Math.sin(t * 5 + i) * 1F);
            int col = i == 1 ? 0xFF7FE8FF : 0xFFFFE04A;
            for (int k = 1; k <= 3; k++) g.fill(px - k * 2, py + 1, px - k * 2 + 2, py + 2, (0x50 * (4 - k) / 3 << 24) | (col & 0xFFFFFF));
            g.fill(px - 1, py, px + 2, py + 3, col);
            g.fill(px, py + 1, px + 1, py + 2, 0xFFFFFFFF);
        }
    }

    /** Bricks stacking up one by one. */
    private static void bricks(GuiGraphics g, int x, int y, float t) {
        int n = (int) (t * 1.2F) % 4;
        for (int i = 0; i < n; i++) item(g, new ItemStack(Items.BRICKS), x + 10, y + 15 - i * 5, 0F, 0.5F);
        float drop = (t * 1.2F) % 1F;
        if (n < 3) item(g, new ItemStack(Items.BRICKS), x + 10, y + 1 + drop * (14 - n * 5), 0F, 0.5F);
    }
}
