package com.xenowarriors.client.test;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** (Tests) how the local player gets drawn: vanilla render events for them, and how many got cancelled (by whom we can't tell). */
@Mod.EventBusSubscriber(modid = com.xenowarriors.XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RenderProbe {
    private RenderProbe() {}
    public static int pre, cancelled, post;

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onPre(RenderPlayerEvent.Pre e) {
        if (e.getEntity() != Minecraft.getInstance().player) return;
        pre++;
        if (e.isCanceled()) cancelled++;
    }

    @SubscribeEvent
    public static void onPost(RenderPlayerEvent.Post e) { if (e.getEntity() == Minecraft.getInstance().player) post++; }

    public static String read() { return "vanilla render events for you: pre " + pre + ", cancelled " + cancelled + ", post " + post; }
    public static void reset() { pre = cancelled = post = 0; }
}
