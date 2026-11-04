package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;

/**
 * With DragonMineZ: its ki blasts, beams, discs and waves don't carve up the Tower, Journey or VR arenas (or the
 * Hekublade realm) - anywhere modes.ModeRules says nothing may be broken. DragonMineZ breaks blocks itself
 * (Level.destroyBlock / setBlock from its ki entities), which no block event catches, so each ki attack that appears
 * there has its own block destruction switched off (AbstractKiProjectile.setBlockDestructionEnabled(false)) - the
 * same switch DragonMineZ uses for its harmless moves. Ki explosions that go through vanilla explosions are already
 * stopped by ModeRules' explosion rule. They still hurt as normal.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DmzTerrain {
    private static Boolean loaded;
    private static boolean broken;
    private static Class<?> kiClass;
    private static Method setDestruction, getDestruction;

    private DmzTerrain() {}

    private static boolean ready() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("dragonminez");
        if (!loaded || broken) return false;
        if (kiClass == null) {
            try {
                kiClass = Class.forName("com.dragonminez.common.init.entities.ki.AbstractKiProjectile");
                setDestruction = kiClass.getMethod("setBlockDestructionEnabled", boolean.class);
                getDestruction = kiClass.getMethod("isBlockDestructionEnabled");
            } catch (Throwable t) {
                broken = true;
                XenoWarriors.LOGGER.warn("DragonMineZ ki block destruction can't be switched off in the arenas", t);
                return false;
            }
        }
        return true;
    }

    /** Whether this is a DragonMineZ ki attack that would break blocks (null = not a ki attack). */
    public static Boolean destroys(Entity e) {
        if (!ready() || !kiClass.isInstance(e)) return null;
        try { return (Boolean) getDestruction.invoke(e); } catch (Throwable t) { return null; }
    }

    /** Switches a ki attack's block breaking off. */
    public static boolean disarm(Entity e) {
        if (!ready() || !kiClass.isInstance(e)) return false;
        try { setDestruction.invoke(e, false); return true; } catch (Throwable t) { return false; }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || com.xenowarriors.modes.ModeRules.canGrief(event.getLevel())) return;
        disarm(event.getEntity());
    }
}
