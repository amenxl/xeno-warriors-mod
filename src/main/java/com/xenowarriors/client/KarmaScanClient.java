package com.xenowarriors.client;

import com.xenowarriors.gov.KarmaScanner;
import com.xenowarriors.karma.KarmaLevel;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Karma Levels on screen: with the Government visor on, a green lens over one eye (first person); whoever you look at
 * gets a reading that counts up beside the crosshair, scouter style. Yuri and Ultima read Karma without one, in their own
 * colours. When the visor overloads: ERROR, cracks, then it's gone.
 */
public class KarmaScanClient implements IGuiOverlay {
    private static int target = -1, mode;
    private static long value, shownAt, brokeAt = -100000;
    private static String name = "";

    public static void receive(int entity, long v, int m, boolean broke) {
        Minecraft mc = Minecraft.getInstance();
        if (broke) { brokeAt = net.minecraft.Util.getMillis(); target = -1; value = v; return; }
        if (entity != target) shownAt = net.minecraft.Util.getMillis();
        target = entity; value = v; mode = m;
        Entity e = mc.level == null || entity < 0 ? null : mc.level.getEntity(entity);
        name = e == null ? "" : e.getDisplayName().getString();
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float pt, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        Font font = mc.font;
        long now = net.minecraft.Util.getMillis();
        boolean visor = mc.player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof KarmaScanner.Item;
        // the lens over the left eye (first person only)
        if (visor && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
            int x0 = Math.round(width * 0.30F), y0 = Math.round(height * 0.30F), x1 = Math.round(width * 0.46F), y1 = Math.round(height * 0.56F);   // (a small lens over the left eye)
            g.fill(x0, y0, x1, y1, 0x1A30FF60);
            g.fill(x0, y0, x1, y0 + 1, 0x9050FF80); g.fill(x0, y1 - 1, x1, y1, 0x9050FF80);
            g.fill(x0, y0, x0 + 1, y1, 0x9050FF80); g.fill(x1 - 1, y0, x1, y1, 0x9050FF80);
            for (int y = y0 + 3; y < y1; y += 4) g.fill(x0 + 1, y, x1 - 1, y + 1, 0x1030FF60);          // scan lines
        }
        // overload: ERROR and cracks for 2 seconds
        if (now - brokeAt < 2000) {
            boolean flash = (now / 120) % 2 == 0;
            String s = "ERROR - OVERLOAD";
            sc(g, font, s, width / 2F - font.width(s), height / 2F - 40, 2F, flash ? 0xFF3030 : 0xFFFFFF);
            String v2 = KarmaLevel.fmt(value) + "+";
            sc(g, font, v2, width / 2F - font.width(v2) * 0.75F, height / 2F - 18, 1.5F, 0xFF6060);
            int cx = width / 4, cy = height / 3;
            for (int i = 0; i < 6; i++) {
                double a = i * 1.1 + 0.3;
                for (int k = 0; k < 40; k++) { int x = (int) (cx + Math.cos(a) * k * 2 + (k % 3)), y = (int) (cy + Math.sin(a) * k * 2); g.fill(x, y, x + 1, y + 1, 0xC0FFFFFF); }
            }
            return;
        }
        var ch = ClientCharacterData.getLocal();
        boolean tech = ch == com.xenowarriors.character.CharacterType.TECHNICA && TechGoggles.down(mc.player);
        if (tech && mc.options.getCameraType() == CameraType.FIRST_PERSON) {          // looking through her goggles
            int r = Math.round(height * 0.32F), cy = height / 2;
            for (int side = -1; side <= 1; side += 2) {
                int cx = width / 2 + side * Math.round(r * 1.05F);
                for (int dy = -r; dy <= r; dy++) {
                    int w = (int) Math.sqrt(r * r - dy * dy);
                    g.fill(cx - w, cy + dy, cx + w, cy + dy + 1, 0x18EC5AA0);
                }
            }
        }
        if (target < 0 || !(visor || tech || ch == com.xenowarriors.character.CharacterType.YURI || ch == com.xenowarriors.character.CharacterType.ULTIMA)) return;
        Entity e = mc.level.getEntity(target);
        if (e == null) return;
        int col = mode == KarmaScanner.YURI ? 0x3AD04A : mode == KarmaScanner.ULTIMA ? 0x1FE07A : mode == KarmaScanner.TECHNICA ? 0xFF7AC0 : 0x50FF80;
        String who = mode == KarmaScanner.YURI ? "YURI'S HACK" : mode == KarmaScanner.ULTIMA ? "ULTIMA ANALYSIS" : mode == KarmaScanner.TECHNICA ? "TECH'S GOGGLES" : "GOV SCANNER";
        float t = Mth.clamp((now - shownAt) / 700F, 0F, 1F);
        long shown = (long) (value * (t * t * (3 - 2 * t)));
        if (t < 1F) shown = shown / 7 * 7 + (now / 30) % 7;                     // (digits rolling while it reads)
        int x = width / 2 + 14, y = height / 2 - 26;
        g.fill(x - 3, y - 3, x + 118, y + 31, 0x80001008);
        g.fill(x - 3, y - 3, x - 2, y + 31, 0xFF000000 | col);
        sc(g, font, who + "  -  KARMA LEVEL", x, y, 0.55F, col);
        sc(g, font, name, x, y + 6, 0.7F, 0xFFFFFF);
        String num = KarmaLevel.fmt(Math.max(0, shown));
        sc(g, font, num, x, y + 15, 1.4F, value > KarmaScanner.CAPACITY && mode == KarmaScanner.VISOR ? 0xFF5050 : col);
    }

    private static void sc(GuiGraphics g, Font font, String s, float x, float y, float k, int c) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(k, k, 1F);
        g.drawString(font, s, 0, 0, 0xFF000000 | c, true);
        g.pose().popPose();
    }
}
