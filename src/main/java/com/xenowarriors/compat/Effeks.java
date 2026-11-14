package com.xenowarriors.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/**
 * AAA Particles (Effekseer for Minecraft): its 3D effects for some of ours, when it's installed. The effects themselves
 * are EpicFight Nightfall's (efn:flame, efn:burst_red...), called by name like its animations - none of its files
 * are in this mod. Without AAA Particles (or Nightfall) nothing plays and our own particles carry on as before.
 * Called on the server, the effect is sent to everyone near; on the client it plays there only.
 */
public final class Effeks {
    private Effeks() {}

    public static final String FLAME = "efn:flame", FLAME2 = "efn:flame2", BURST = "efn:burst", BURST_RED = "efn:burst_red", BURST_BLUE = "efn:burst_blue",
            SPARK = "efn:spark", SPARK2 = "efn:spark2", CHARGING = "efn:charging", MIST = "efn:mist", CLAW = "efn:claw", ROCK = "efn:rock1",
            DISORDER = "efn:disorder", DRAGON_FLASH = "efn:dragon_flash", BREAKOUT = "efn:breakout";

    /** (Tests) effects started; switched off (to compare before / after). */
    public static int played;
    public static boolean off;

    private static Boolean ready;
    private static java.lang.reflect.Constructor<?> info;
    private static Method add, position, scale, rotation, bind;

    private static boolean ready() {
        if (ready != null) return ready;
        ready = false;
        try {
            if (ModList.get() == null || !ModList.get().isLoaded("aaa_particles") || !ModList.get().isLoaded("efn")) return false;
            Class<?> pei = Class.forName("mod.chloeprime.aaaparticles.api.common.ParticleEmitterInfo");
            info = pei.getConstructor(ResourceLocation.class);
            add = Class.forName("mod.chloeprime.aaaparticles.api.common.AAALevel").getMethod("addParticle", Level.class, boolean.class, pei);
            position = pei.getMethod("position", double.class, double.class, double.class);
            scale = pei.getMethod("scale", float.class);
            rotation = pei.getMethod("rotation", float.class, float.class, float.class);
            bind = pei.getMethod("bindOnEntity", Entity.class);
            ready = true;
        } catch (Throwable t) {
            com.xenowarriors.XenoWarriors.LOGGER.info("[Xeno Warriors] AAA Particles effects off: {}", t.toString());
        }
        return ready;
    }

    public static boolean available() { return !off && ready(); }

    /** Plays an effect at a spot (yaw in degrees: which way it faces). True if it went off. */
    public static boolean play(Level level, String effect, Vec3 at, float size, float yaw) {
        return play(level, effect, at, size, yaw, null);
    }

    /** ...following an entity (bound to it). */
    public static boolean play(Level level, String effect, Vec3 at, float size, float yaw, Entity follow) {
        if (level == null || !available()) return false;
        try {
            Object e = info.newInstance(new ResourceLocation(effect));
            position.invoke(e, at.x, at.y, at.z);
            scale.invoke(e, size);
            rotation.invoke(e, 0F, (float) Math.toRadians(-yaw), 0F);
            if (follow != null) bind.invoke(e, follow);
            add.invoke(null, level, true, e);
            played++;
            return true;
        } catch (Throwable t) {
            ready = false;
            com.xenowarriors.XenoWarriors.LOGGER.warn("[Xeno Warriors] AAA Particles effect {} failed, effects off: {}", effect, t.toString());
            return false;
        }
    }
}
