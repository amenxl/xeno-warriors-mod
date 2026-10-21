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
    private static final Map<String, List<SoundInstance>> PLAYING = new HashMap<>();

    private DiscTracker() {}

    @SubscribeEvent
    public static void onSound(PlaySoundEvent event) {
        SoundInstance s = event.getSound();
        if (s != null && s.getLocation().getPath().startsWith("music_disc.")) {
            PLAYING.computeIfAbsent(s.getLocation().getPath(), k -> new ArrayList<>()).add(s);
        }
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
