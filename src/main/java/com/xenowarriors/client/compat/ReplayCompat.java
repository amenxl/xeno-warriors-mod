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

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (XenoWarriors.MOD_ID.equals(event.getOverlay().id().getNamespace()) && replaying()) event.setCanceled(true);
    }
}
