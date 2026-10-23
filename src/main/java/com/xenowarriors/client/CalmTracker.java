package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/** How long since each character last fought (swung at something, got hurt, locked on, charged up). */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CalmTracker {
    /** Ten minutes without a fight. */
    public static final int CALM_TICKS = 20 * 60 * 10;
    private static final Map<Integer, Long> LAST_FIGHT = new HashMap<>();

    private CalmTracker() {}

    /** No fighting for ten minutes. */
    public static boolean longCalm(AbstractClientPlayer p) {
        var level = Minecraft.getInstance().level;
        if (level == null) return false;
        Long last = LAST_FIGHT.get(p.getId());
        return last != null && level.getGameTime() - last >= CALM_TICKS;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        var level = Minecraft.getInstance().level;
        if (event.phase != TickEvent.Phase.END) return;
        if (level == null) {
            LAST_FIGHT.clear();
            return;
        }
        long now = level.getGameTime();
        for (AbstractClientPlayer p : level.players()) {
            boolean fighting = p.swinging || p.hurtTime > 0 || Busy.busy(p)
                    || (p == Minecraft.getInstance().player && LockOn.target() != null);
            if (fighting || !LAST_FIGHT.containsKey(p.getId())) LAST_FIGHT.put(p.getId(), now);
        }
    }
}
