package com.xenowarriors.skill;

import com.xenowarriors.XenoWarriors;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/** Runs bits of skills a few ticks later on the server (multi-hit combos, dashes). */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerScheduler {
    private record Task(long at, Runnable run) {}
    private static final List<Task> TASKS = new ArrayList<>();
    private static long tick;

    private ServerScheduler() {}

    public static void later(int ticks, Runnable run) {
        TASKS.add(new Task(tick + Math.max(1, ticks), run));
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent e) { TASKS.clear(); tick = 0; }   // (nothing from the old world runs in the next one)

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tick++;
        List<Task> due = new ArrayList<>();
        TASKS.removeIf(t -> {
            if (t.at <= tick) { due.add(t); return true; }
            return false;
        });
        for (Task t : due) {
            try {
                t.run.run();
            } catch (Exception e) {
                XenoWarriors.LOGGER.error("Skill task failed", e);
            }
        }
    }
}
