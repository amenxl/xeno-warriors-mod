package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.network.CutsceneGuardPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * "GUARD 1/3" at the top under the cutscene letterbox for a moment after an enemy's hit during your cutscene (skill.CutsceneBreak):
 * pips for the hits that counted, "RESISTED" when your Defense shrugged one off, "GUARD BROKEN" when it ends the cutscene.
 * Drawn after the HUD (the cutscenes hide every HUD overlay but their own).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CutsceneGuardHud {
    private CutsceneGuardHud() {}

    public static final long SHOW_MS = 1600;

    /** Whether the indicator is up right now (tests). */
    public static boolean showing() {
        CutsceneGuardPacket m = CutsceneGuardPacket.last;
        return m != null && System.currentTimeMillis() - CutsceneGuardPacket.lastAtMs < SHOW_MS;
    }

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post event) {
        CutsceneGuardPacket m = CutsceneGuardPacket.last;
        if (m == null) return;
        long age = System.currentTimeMillis() - CutsceneGuardPacket.lastAtMs;
        if (age >= SHOW_MS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        GuiGraphics g = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        float fade = age < SHOW_MS - 400 ? 1F : (SHOW_MS - age) / 400F;
        int a = Math.max(8, Math.round(255 * fade)) << 24;
        int cy = h / 6 + 6;                                              // (top middle, under the letterbox bar: clear of the ultimate's title)
        String title = m.state() == 2 ? "GUARD BROKEN" : "GUARD " + m.hits() + "/" + m.need();
        int col = m.state() == 2 ? 0xFF5544 : m.state() == 1 ? 0xFFBB44 : 0x88DDFF;
        g.pose().pushPose();
        g.pose().translate(w / 2F, cy, 0);
        g.pose().scale(1.5F, 1.5F, 1F);
        g.drawCenteredString(mc.font, title, 0, 0, a | col);
        g.pose().popPose();
        // pips: filled = hits that counted toward breaking it
        int pip = 8, gap = 4, n = Math.max(1, m.need()), total = n * pip + (n - 1) * gap, x0 = w / 2 - total / 2, y = cy + 16;
        for (int i = 0; i < n; i++) {
            int x = x0 + i * (pip + gap);
            g.fill(x - 1, y - 1, x + pip + 1, y + pip + 1, a | 0x000000);
            g.fill(x, y, x + pip, y + pip, a | (i < m.hits() || m.state() == 2 ? col : 0x333333));
        }
        if (m.state() == 0) g.drawCenteredString(mc.font, "RESISTED (Defense: " + m.chance() + "% of hits count)", w / 2, y + 12, a | 0xBBEEFF);
        else if (m.state() == 1) g.drawCenteredString(mc.font, "Hit counts!", w / 2, y + 12, a | 0xFFDD99);
    }
}
