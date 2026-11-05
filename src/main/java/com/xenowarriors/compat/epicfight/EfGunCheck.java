package com.xenowarriors.compat.epicfight;

import net.minecraft.client.Minecraft;

/**
 * (Client UI tests) what Epic Fight makes of the local player holding a summoned gun: battle mode, whether its
 * item renderer hands first person back to vanilla (our item_skins json), and whether it's playing an attack.
 * Every method is safe to call without Epic Fight (returns a neutral answer).
 */
public final class EfGunCheck {
    private EfGunCheck() {}

    public static boolean loaded() { return net.minecraftforge.fml.ModList.get().isLoaded("epicfight"); }

    /** Into Epic Fight's battle mode (the mode in which it draws its own first-person arms and turns clicks into swings). */
    public static void battleMode() { if (loaded()) try { Impl.battleMode(); } catch (Throwable t) { log(t); } }

    /** "battle=.., animatedFirstPerson=.., gunRenderer=<class> forceVanillaFirstPerson=.." for the main hand item. */
    public static String info() {
        if (!loaded()) return "epic fight not loaded";
        try { return Impl.info(); } catch (Throwable t) { log(t); return "error " + t; }
    }

    /** Epic Fight's ATTACKING / INACTION state on the local player (an attack animation running). */
    public static boolean attacking() {
        if (!loaded()) return false;
        try { return Impl.attacking(); } catch (Throwable t) { log(t); return false; }
    }

    private static void log(Throwable t) { com.xenowarriors.XenoWarriors.LOGGER.info("XW-TEST gunfeel ef check failed {}", t.toString()); }

    private static final class Impl {
        static yesman.epicfight.client.world.capabilites.entitypatch.player.LocalPlayerPatch patch() {
            return yesman.epicfight.client.ClientEngine.getInstance().getPlayerPatch();
        }

        static void battleMode() {
            var p = patch();
            if (p != null && !p.isEpicFightMode()) p.toEpicFightMode(true);
        }

        static String info() {
            var p = patch();
            Minecraft mc = Minecraft.getInstance();
            var r = yesman.epicfight.client.ClientEngine.getInstance().renderEngine.getItemRenderer(mc.player.getMainHandItem());
            return "battle=" + (p != null && p.isEpicFightMode()) + " animatedFirstPerson=" + yesman.epicfight.config.ClientConfig.enableAnimatedFirstPersonModel
                    + " gunRenderer=" + (r == null ? "none" : r.getClass().getSimpleName() + " forceVanillaFirstPerson=" + r.forceVanillaFirstPerson());
        }

        static boolean attacking() {
            var p = patch();
            if (p == null || p.getEntityState() == null) return false;
            var s = p.getEntityState();
            return Boolean.TRUE.equals(s.getState(yesman.epicfight.api.animation.types.EntityState.ATTACKING))
                    || Boolean.TRUE.equals(s.getState(yesman.epicfight.api.animation.types.EntityState.INACTION));
        }
    }
}
