package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Where our HUD pieces may go without covering anything: the top-right corner is shared (stacked below the vanilla
 * status-effect icons: request cards first, then level-up toasts), the top-center bar sits under any boss bars, and the
 * bottom-right skill bar moves up out of Epic Fight's way.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class HudLayout {
    private static int topRight = 6, bossBottom, bossBottomNext;
    private static Boolean epicFight;

    private HudLayout() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFrame(RenderGuiEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        int y = 6;
        if (mc.player != null) {
            boolean good = false, bad = false;
            for (var e : mc.player.getActiveEffects()) {
                if (!e.showIcon()) continue;
                if (e.getEffect().isBeneficial()) good = true; else bad = true;
            }
            if (good) y = 27;
            if (bad) y = good ? 53 : 27;
        }
        topRight = y;
        bossBottom = bossBottomNext;
        bossBottomNext = 0;
    }

    @SubscribeEvent
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        bossBottomNext = Math.max(bossBottomNext, event.getY() + event.getIncrement());
    }

    /** Claims {@code h} pixels of the top-right column; returns the y to draw at. */
    public static int takeTopRight(int h) {
        int y = topRight;
        topRight += h + 4;
        return y;
    }

    public static int topRight() { return topRight; }

    /** The lowest point of the boss bars this frame (0 if none). */
    public static int bossBottom() { return bossBottom; }

    /** How far the bottom-right skill bar should rise to stay clear of other mods' HUD there. */
    public static int skillBarLift() {
        if (epicFight == null) epicFight = net.minecraftforge.fml.ModList.get().isLoaded("epicfight");
        return epicFight ? 34 : 0;
    }
}
