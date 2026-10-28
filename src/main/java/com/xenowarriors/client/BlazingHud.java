package com.xenowarriors.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The Blazing Wind HUD (the default): bottom right, the character's portrait inside a ring emblem in their colour, with
 * round labelled bubbles and bars running left - HP, KP (Karma; MP for Zane, DP for Ultima), HG (hunger) or KI and ST
 * with DragonMineZ, the LV plate and the EXP bar.
 */
public final class BlazingHud {
    private BlazingHud() {}

    private static final ResourceLocation EMBLEM = XenoWarriors.id("textures/gui/hud/blazing_emblem.png");
    private static final int EW = 480, EH = 456;
    private static final float CX = 260F / EW, CY = 208F / EH, CR = 124F / EW;

    private static ResourceLocation bubble(String name) { return XenoWarriors.id("textures/gui/hud/blazing_" + name + ".png"); }

    /** The top of your own gauge (the party cards sit above it). */
    public static int top(int height) {
        int E = 70, H = Math.round(E * EH / (float) EW);
        return height - H - 24 - HudLayout.skillBarLift();
    }

    /** Portrait placement for the rest of the HUD (KA orb, chat bubble, effects): {x, y, size, barsLeft, barsTop}. */
    public static int[] render(GuiGraphics g, Minecraft mc, Font font, CharacterType type, int width, int height, int lift,
                               float hp, float max, float chip, boolean low, boolean flash, boolean dbz, String face) {
        var p = mc.player;
        int col = type.getColor() & 0xFFFFFF;
        int E = 70, H = Math.round(E * EH / (float) EW);
        int ex = width - E - 1, ey = height - H - 24 - lift;
        float pcx = ex + CX * E, pcy = ey + CY * H, pr = CR * E;

        // the portrait, then the emblem over it in the character's colour
        if (HudFaces.hasArt(type)) {
            int s = Math.round(pr * 2 / 0.828F);
            KhHud.portrait(g, HudFaces.texture(type, face), Math.round(pcx - s / 2F), Math.round(pcy - s / 2F), s, flash);
        } else {
            int s = Math.round(pr * 2 * 0.98F);
            disc(g, pcx, pcy, pr * 1.05F, 0xFF000000 | mix(col, 0.35F));
            HudHead.draw(g, p, type, Math.round(pcx - s / 2F), Math.round(pcy - s / 2F), s, flash, 0.62F, 0.95F, false);   // (head and hair in the middle of the ring)
        }
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(((col >> 16) & 255) / 255F, ((col >> 8) & 255) / 255F, (col & 255) / 255F, 1F);
        g.blit(EMBLEM, ex, ey, E, H, 0, 0, EW, EH, EW, EH);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);

        // the bars, running left from their bubbles
        int hotbarRight = width / 2 + 91;
        int bw = Mth.clamp(ex - hotbarRight - 22, 50, 110);
        float karma = ClientSkillState.karma(), kmax = Math.max(1F, ClientSkillState.maxKarma());
        int sealed = ClientKarmaLock.secondsLeft();
        String kp = type == CharacterType.ZANE ? "mp" : type == CharacterType.ULTIMA ? "dp" : "kp";
        int kc = type == CharacterType.ZANE ? 0xFFB050F0 : type == CharacterType.ULTIMA ? 0xFF20D890 : 0xFF3C8CFF;
        int y0 = ey + Math.round(H * 0.16F);
        row(g, font, "hp", ex + 3, y0, bw, 6, hp / max, chip / max, low ? (flash ? 0xFFFF5040 : 0xFFD03A30) : 0xFFE04868, 0xFFC81E3C,
                Math.round(hp) + " / " + Math.round(max));
        row(g, font, kp, ex - 4, y0 + 15, bw, 5, karma / kmax, 0, sealed > 0 ? 0xFF6A5A70 : kc, 0xFF000000 | darker(kc),
                sealed > 0 ? "DRAINED " + sealed + "s" : String.valueOf(Math.round(karma)));
        int y = y0 + 30;
        if (dbz) {
            row(g, font, "ki", ex + 2, y, bw, 5, DmzHud.ki / Math.max(1F, DmzHud.maxKi), 0, 0xFFFFCD3C, 0xFFB07A10, Math.round(DmzHud.ki) + "/" + Math.round(DmzHud.maxKi));
            y += 13;
            row(g, font, "st", ex + 8, y, bw - 10, 3, DmzHud.sta / Math.max(1F, DmzHud.maxSta), 0, 0xFF8CE678, 0xFF3C8A3C, "");
            String fl = DmzHud.form + "  " + DmzHud.powerRelease + "%";
            KhHud.small(g, font, fl, pcx - font.width(fl) * 0.275F, ey - 6, 0xFFD25A, 0.55F);
        } else if (type != CharacterType.ULTIMA) {                     // (Ultima doesn't get hungry)
            float food = BigHungerHud.max() > 20 && BigHungerHud.total() >= 0 ? BigHungerHud.total() / (float) BigHungerHud.max() : p.getFoodData().getFoodLevel() / 20F;
            row(g, font, "hg", ex + 2, y, bw, 4, food, 0, 0xFFE8D020, 0xFFA08A10, "");
        }
        // LV plate under the emblem, and the EXP bar
        int lvY = ey + H - 5;
        int plateL = ex + Math.round(E * 0.18F), plateR = ex + Math.round(E * 0.86F);
        g.fill(plateL, lvY, plateR, lvY + 8, 0xFF7A2A08);
        g.fill(plateL + 1, lvY + 1, plateR - 1, lvY + 7, 0xFFFF6A1A);
        g.fill(plateL + 1, lvY + 1, plateR - 1, lvY + 3, 0xFFFFA060);
        String lv = String.valueOf(LevelHud.level());
        KhHud.small(g, font, lv, plateR - 13 - font.width(lv) * 0.65F, lvY + 1.2F, 0xFFFFFF, 0.65F);
        bub(g, "lv", plateR - 6, lvY - 3, 14);
        // EXP sits level with the LV plate, just left of it, its bar touching the bubble
        int expB = plateL - 15, expY = lvY;
        bar(g, expB + 3 - bw, expY + 3, bw, 3, LevelHud.progress(), 0, 0xFFFF9A30, 0xFFA0581A);
        bub(g, "exp", expB, expY - 2, 13);
        return new int[]{Math.round(pcx - pr), Math.round(pcy - pr), Math.round(pr * 2), Math.min(expB + 3 - bw, ex + 3 - bw), y0 - 4};
    }

    /** A bubble with its bar running left, the value inside the bar. */
    private static void row(GuiGraphics g, Font font, String name, int bx, int by, int w, int h, float frac, float chip, int fill, int border, String text) {
        bar(g, bx + 3 - w, by, w, h, frac, chip, fill, border);       // (runs under the bubble's rim so they touch)
        if (!text.isEmpty()) KhHud.small(g, Minecraft.getInstance().font, text, bx - 3 - font.width(text) * 0.5F, by + (h - 4.5F) / 2F, 0xFFFFFF, 0.5F);
        bub(g, name, bx, by + h / 2 - 7, 14);
    }

    /** A rounded bar with a coloured border; fills from the right (the bubble side). */
    private static void bar(GuiGraphics g, int x, int y, int w, int h, float frac, float chip, int fill, int border) {
        g.fill(x + 1, y - 1, x + w - 1, y + h + 1, border);
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y, x + w - 1, y + h, 0xFF4A4A50);
        g.fill(x + 1, y, x + w - 1, y + Math.max(1, h / 3), 0xFF6A6A72);
        int inner = w - 2;
        if (chip > frac) g.fill(x + 1 + Math.round(inner * (1 - Mth.clamp(chip, 0, 1))), y, x + w - 1, y + h, 0xFFFFE27A);
        int fx = x + 1 + Math.round(inner * (1 - Mth.clamp(frac, 0, 1)));
        g.fill(fx, y, x + w - 1, y + h, fill);
        if (h > 2) g.fill(fx, y, x + w - 1, y + 1, KhHud.brighten(fill));
    }

    private static void bub(GuiGraphics g, String name, int x, int y, int s) {
        RenderSystem.enableBlend();
        g.blit(bubble(name), x, y, s, s, 0, 0, 64, 64, 64, 64);
    }

    private static void disc(GuiGraphics g, float cx, float cy, float r, int c) {
        for (int dy = (int) -r; dy <= r; dy++) {
            int w = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            g.fill(Math.round(cx - w), Math.round(cy + dy), Math.round(cx + w), Math.round(cy + dy + 1), c);
        }
    }

    private static int mix(int c, float f) {
        return ((int) (((c >> 16) & 255) * f) << 16) | ((int) (((c >> 8) & 255) * f) << 8) | (int) ((c & 255) * f);
    }

    private static int darker(int c) { return mix(c & 0xFFFFFF, 0.6F); }
}
