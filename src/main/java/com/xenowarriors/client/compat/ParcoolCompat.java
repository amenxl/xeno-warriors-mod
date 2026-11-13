package com.xenowarriors.client.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * ParCool: Mikiko and Rhalix have runs of their own (his fox run on all fours, Rhalix's sprint). ParCool's fast-run
 * animation and lean was laid over them, so the two fought every frame - their own run plays instead (ParCool's
 * speed, stamina and every other parkour move are untouched). Reflection only: nothing happens without ParCool, and
 * a ParCool update that renames things just turns this off (logged once).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ParcoolCompat {
    private static Boolean loaded;
    private static boolean broken;
    private static java.lang.reflect.Method get, remove;
    private static java.lang.reflect.Field animator;
    /** (tests) ParCool fast-run animations taken off our runners. */
    public static int cleared;

    private ParcoolCompat() {}

    public static boolean loaded() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("parcool");
        return loaded;
    }

    /** Characters whose own run replaces ParCool's. */
    public static boolean ownRun(Player p) {
        CharacterType t = ClientCharacterData.get(p);
        return t == CharacterType.MIKIKO || t == CharacterType.RHALIX;
    }

    /** ParCool's current animator on this player, by class name (or null). */
    public static String animatorOf(Player p) {
        Object a = anim(p);
        if (a == null) return null;
        try { Object x = animator.get(a); return x == null ? null : x.getClass().getSimpleName(); } catch (Throwable t) { return null; }
    }

    private static Object anim(Player p) {
        if (!loaded() || broken) return null;
        try {
            if (get == null) {
                Class<?> c = Class.forName("com.alrex.parcool.common.capability.Animation");
                get = c.getMethod("get", Player.class);
                remove = c.getMethod("removeAnimator");
                animator = c.getDeclaredField("animator");
                animator.setAccessible(true);
            }
            return get.invoke(null, p);
        } catch (Throwable t) {
            broken = true;
            XenoWarriors.LOGGER.warn("ParCool compat off (its animation API changed): {}", t.toString());
            return null;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !loaded() || broken) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (Player p : mc.level.players()) {
            if (!ownRun(p)) continue;
            Object a = anim(p);
            if (a == null) continue;
            try {
                Object x = animator.get(a);
                if (x != null && x.getClass().getSimpleName().equals("FastRunningAnimator")) { remove.invoke(a); cleared++; }
            } catch (Throwable t) {
                broken = true;
                XenoWarriors.LOGGER.warn("ParCool compat off: {}", t.toString());
            }
        }
    }
}
