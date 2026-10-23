package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Thunder: a jump at the crack (wide-eyed), then a reaction - Mikiko scared of it, Rhalix unimpressed. A lightning
 * strike close by gets a bigger reaction.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ThunderFaces {
    private static final int STARTLE = 14, REACT = 50;
    private static long thunderAt = -1000;
    private static double x, y, z;

    private ThunderFaces() {}

    @SubscribeEvent
    public static void onSound(PlaySoundEvent event) {
        var s = event.getSound();
        if (s == null || Minecraft.getInstance().level == null) return;
        String path = s.getLocation().getPath();
        if (path.equals("entity.lightning_bolt.thunder") || path.equals("entity.lightning_bolt.impact")) {
            thunderAt = Minecraft.getInstance().level.getGameTime();
            x = s.getX(); y = s.getY(); z = s.getZ();
        }
    }

    /** The face for a thunderclap just now, or null. */
    public static String face(Player p, CharacterType type) {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        long t = level.getGameTime() - thunderAt;
        boolean close = p.distanceToSqr(x, y, z) < 40 * 40;
        int react = close ? REACT + 30 : REACT;
        if (t < 0 || t >= STARTLE + react) return null;
        if (t < STARTLE) return "surprised";
        if (type == CharacterType.RHALIX) return close ? "annoyed" : t < STARTLE + 20 ? "look_up" : "bored";
        return close ? "panic" : "scared";
    }
}
