package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Keeps track of which music discs are playing nearby (so the characters can dance to them). */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class DiscTracker {
    public static final String GROOVY = "music_disc.its_time_to_get_groovy";
    public static final String GRAVITY = "music_disc.gravity_glitch_duel";
    public static final String LUNAR = "music_disc.lunar_ascendence";
    public static final String SMOKE = "music_disc.smoke_break";
    public static final String MIMICRY = "music_disc.mimicry_eye_catcher";
    public static final String SKYBOUND = "music_disc.skybound_pursuit";
    public static final String VIRTUAL_GRIND = "music_disc.virtual_grind";
    public static final String CORRUPTED_PULSE = "music_disc.corrupted_pulse";
    public static final String CHILL = "music_disc.maxs_chill_day";

    /** Jump height for the hyped "SkyBound Pursuit" dance: a hop on every beat (10 ticks). */
    public static float hypeLift(float t) {
        return Math.abs((float) Math.sin(t * Math.PI / 10.0)) * 0.42F;
    }
    private static final Map<String, List<SoundInstance>> PLAYING = new HashMap<>();

    private DiscTracker() {}

    @SubscribeEvent
    public static void onSound(PlaySoundEvent event) {
        SoundInstance s = event.getSound();
        if (s != null && s.getLocation().getPath().startsWith("music_disc.")) {
            PLAYING.computeIfAbsent(s.getLocation().getPath(), k -> new ArrayList<>()).add(s);
        }
    }

    /** Any music disc playing within 20 blocks (something to dance to)? */
    public static boolean anyNear(Player p) {
        for (String disc : PLAYING.keySet()) if (near(p, disc)) return true;
        return false;
    }

    /** Standing about with music on: the dances take over (even from Epic Fight's own idle stance). */
    public static boolean dancing(Player p) {
        return Math.abs(p.getX() - p.xo) + Math.abs(p.getZ() - p.zo) < 0.02 && !p.swinging && p.onGround() && anyNear(p);
    }

    /** Is this disc playing within 20 blocks of the player? */
    public static boolean near(Player p, String disc) {
        List<SoundInstance> list = PLAYING.get(disc);
        if (list == null || list.isEmpty()) return false;
        var sounds = Minecraft.getInstance().getSoundManager();
        list.removeIf(s -> !sounds.isActive(s));
        for (SoundInstance s : list) {
            if (p.distanceToSqr(s.getX(), s.getY(), s.getZ()) < 20 * 20) return true;
        }
        return false;
    }
}
