package com.xenowarriors.client.compat;

import com.xenowarriors.XenoWarriors;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * (Re)Forged Play Mod support. Everything Xeno Warriors shows on the characters (forms, faces, wings, auras, skills)
 * comes from packets and player data, so it records and plays back on its own; while watching a replay our HUD
 * (skill bar, levels, party, lock-on) is hidden, since the camera isn't one of the warriors.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ReplayCompat {
    private static Boolean loaded;
    private static Field instance;
    private static Method getHandler;

    private ReplayCompat() {}

    /** Watching a replay right now. */
    public static boolean replaying() {
        if (loaded == null) {
            loaded = ModList.get() != null && ModList.get().isLoaded("reforgedplaymod");
            if (loaded) {
                try {
                    Class<?> c = Class.forName("com.replaymod.replay.ReplayModReplay");
                    instance = c.getField("instance");
                    getHandler = c.getMethod("getReplayHandler");
                } catch (Throwable t) {
                    loaded = false;
                }
            }
        }
        if (!loaded) return false;
        try {
            Object mod = instance.get(null);
            return mod != null && getHandler.invoke(mod) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** replaying(), checked once a tick (cheap to read from render code). */
    public static boolean active;

    @SubscribeEvent
    public static void onTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.START) active = replaying();
    }

    /**
     * In a replay the camera is a player entity of its own with your name - it isn't a warrior, and it must never be
     * drawn: (a first-person body view would show it as a second Mikiko doing his normal moves right next to the
     * recorded one while Epic Fight animates the real him).
     */
    public static boolean isReplayCamera(net.minecraft.world.entity.player.Player p) {
        return active && p == net.minecraft.client.Minecraft.getInstance().player;
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onRenderPlayer(net.minecraftforge.client.event.RenderPlayerEvent.Pre event) {
        if (isReplayCamera(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (XenoWarriors.MOD_ID.equals(event.getOverlay().id().getNamespace()) && replaying()) event.setCanceled(true);
    }
}
