package com.xenowarriors.client.mikiko;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.entity.HekubladeShardEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Riding a Hekublade (Hekublade Scatter): a thin bar above the hotbar with the ride time left. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SurfHud {
    private SurfHud() {}

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !(mc.player.getVehicle() instanceof HekubladeShardEntity b) || !b.surfing()) return;
        int max = Math.max(1, b.rideMax()), left = b.rideLeft();
        GuiGraphics g = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        int bw = 90, x = w / 2 - bw / 2, y = h - 59;
        float f = Math.min(1F, left / (float) max);
        boolean low = left <= HekubladeShardEntity.SURF_FADE;
        g.fill(x - 1, y - 1, x + bw + 1, y + 4, 0xC0101014);
        g.fill(x, y, x + Math.round(bw * f), y + 3, low && (mc.player.tickCount / 4) % 2 == 0 ? 0xFFFF5A3A : 0xFFFFE6C8);
        String s = String.format("Blade ride %.1fs", left / 20F);
        g.pose().pushPose();
        g.pose().translate(w / 2F - mc.font.width(s) * 0.35F, y - 7, 0);
        g.pose().scale(0.7F, 0.7F, 1F);
        g.drawString(mc.font, s, 0, 0, 0xFFE8D8C0, true);
        g.pose().popPose();
    }
}
