package com.xenowarriors.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The vanilla hunger row for big stomachs: still 10 drumsticks, but each full bar (20 points) is its own color, so
 * Mikiko's 10 bars go through 10 colors and Rhalix's 3 through 3. Ultima has no hunger row at all.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BigHungerHud {
    private static final ResourceLocation ICONS = new ResourceLocation("textures/gui/icons.png");
    /** Bar colors, bar 1 (vanilla look) to bar 10. */
    public static final int[] TIER = {0xFFFFFF, 0xFFD24A, 0x7CE05A, 0x4AD0E8, 0x5A7CFF, 0xB25AFF, 0xFF5AC8, 0xFF5A5A, 0xFFA040, 0xFFFFA0};
    private static int total = -1, max = 20;

    private BigHungerHud() {}

    public static void set(int t, int m) { total = t; max = m; }

    public static int total() { return total; }
    public static int max() { return max; }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.FOOD_LEVEL.id())) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        CharacterType t = ClientCharacterData.getLocal();
        if (t == CharacterType.ULTIMA) { event.setCanceled(true); return; }
        if (com.xenowarriors.client.you.YouClient.sun >= 0) {           // a Seedling: the Sun meter in the food bar's place
            event.setCanceled(true);
            drawSun(event.getGuiGraphics(), mc, com.xenowarriors.client.you.YouClient.sun / (float) com.xenowarriors.you.YouRaces.SUN_MAX);
            return;
        }
        if (max <= 20 || total < 0 || (t != CharacterType.MIKIKO && t != CharacterType.RHALIX)) return;
        if (event.isCanceled()) return;
        event.setCanceled(true);
        draw(event.getGuiGraphics(), mc);
    }

    /** Ten little suns, filling from the right. */
    private static void drawSun(GuiGraphics g, Minecraft mc, float frac) {
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        int right = w / 2 + 91, top = h - ((net.minecraftforge.client.gui.overlay.ForgeGui) mc.gui).rightHeight;
        ((net.minecraftforge.client.gui.overlay.ForgeGui) mc.gui).rightHeight += 10;
        int lit = Math.round(frac * 20);
        for (int i = 0; i < 10; i++) {
            int x = right - i * 8 - 9, y = top;
            g.fill(x + 2, y + 2, x + 7, y + 7, 0xFF3A3020);
            int pts = i * 2;
            int c = pts + 2 <= lit ? 0xFFFFD040 : pts + 1 == lit ? 0xFFB08A30 : 0;
            if (c != 0) { g.fill(x + 3, y + 3, x + 6, y + 6, c); g.fill(x + 4, y + 2, x + 5, y + 7, c); g.fill(x + 2, y + 4, x + 7, y + 5, c); }
        }
    }

    private static void draw(GuiGraphics g, Minecraft mc) {
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        int right = w / 2 + 91, top = h - ((net.minecraftforge.client.gui.overlay.ForgeGui) mc.gui).rightHeight;
        ((net.minecraftforge.client.gui.overlay.ForgeGui) mc.gui).rightHeight += 10;
        int tier = total <= 0 ? 0 : (total - 1) / 20;                // the bar being eaten from
        int inTier = total <= 0 ? 0 : total - tier * 20;              // 1..20 points in it
        RenderSystem.enableBlend();
        for (int i = 0; i < 10; i++) {
            int x = right - i * 8 - 9, y = top;
            g.blit(ICONS, x, y, 16, 27, 9, 9);                         // empty backing
            int pts = i * 2;
            int col;
            int u;
            if (pts + 2 <= inTier) { col = TIER[tier]; u = 52; }                       // full in this bar's color
            else if (pts + 1 == inTier) { col = TIER[tier]; u = 61; }                   // half
            else if (tier > 0) { col = TIER[tier - 1]; u = 52; }                       // under it: the bar below, full
            else continue;
            float r = ((col >> 16) & 255) / 255F, gg = ((col >> 8) & 255) / 255F, b = (col & 255) / 255F;
            RenderSystem.setShaderColor(r, gg, b, 1F);
            g.blit(ICONS, x, y, u, 27, 9, 9);
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        }
        String label = "x" + (tier + 1);
        g.drawString(mc.font, label, right - 80 - mc.font.width(label) - 3, top + 1, 0xFF000000 | TIER[tier], true);
    }
}
