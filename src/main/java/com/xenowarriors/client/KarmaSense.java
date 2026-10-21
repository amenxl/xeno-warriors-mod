package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Client side of Karma Sensing: sensed entities get a glowing outline you can see through walls,
 * colored by what they are (hostile red, players violet, animals green, others gold), plus a purple
 * pulse across the screen and a ring of light rolling out from you.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class KarmaSense {
    private static final Map<Integer, Long> SENSED = new HashMap<>();   // entity id -> game time it stops glowing
    private static final Map<Integer, Long> TRACKED = new HashMap<>();  // Fire Blitz targets (orange outline)
    private static long pulseStart = -1000;

    public static void track(int id, int duration) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) TRACKED.put(id, mc.level.getGameTime() + duration);
    }

    private KarmaSense() {}

    public static void sensed(int[] ids, int duration) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long until = mc.level.getGameTime() + duration;
        for (int id : ids) SENSED.put(id, until);
        pulseStart = mc.level.getGameTime();
    }

    public static boolean isSensed(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        Long until = SENSED.get(e.getId());
        Long tracked = TRACKED.get(e.getId());
        long now = mc.level.getGameTime();
        return (until != null && now < until) || (tracked != null && now < tracked);
    }

    public static int color(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        Long tracked = TRACKED.get(e.getId());
        if (tracked != null && mc.level != null && mc.level.getGameTime() < tracked) return 0xFF7A1A;
        if (e instanceof Enemy) return 0xFF3A3A;
        if (e instanceof Player) return 0xB45CFF;
        if (e instanceof Animal) return 0x5CFF7A;
        return 0xFFC94A;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            SENSED.clear();
            TRACKED.clear();
            return;
        }
        long now = mc.level.getGameTime();
        SENSED.values().removeIf(until -> until < now);
        TRACKED.values().removeIf(until -> until < now);
        // a ring of light rolling outward for the first second
        long age = now - pulseStart;
        if (mc.player != null && age >= 0 && age < 20) {
            double r = 1 + age * 2.2;
            for (int i = 0; i < 40; i++) {
                double a = i / 40.0 * Math.PI * 2;
                mc.level.addParticle(ParticleTypes.END_ROD, mc.player.getX() + Math.cos(a) * r, mc.player.getY() + 0.2,
                        mc.player.getZ() + Math.sin(a) * r, 0, 0.01, 0);
            }
        }
    }

    /** Purple pulse around the edges of the screen when you sense. */
    public static class Overlay implements IGuiOverlay {
        @Override
        public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            float age = mc.level.getGameTime() - pulseStart + partialTick;
            if (age < 0 || age > 24) return;
            float a = Mth.sin(Mth.clamp(age / 24F, 0F, 1F) * (float) Math.PI) * 0.45F;
            int alpha = Math.round(a * 255);
            int edge = Math.max(8, width / 10);
            for (int i = 0; i < edge; i += 2) {
                int col = (Math.round(alpha * (1F - i / (float) edge)) << 24) | 0x8A3CFF;
                g.fill(i, i, width - i, i + 2, col);
                g.fill(i, height - i - 2, width - i, height - i, col);
                g.fill(i, i, i + 2, height - i, col);
                g.fill(width - i - 2, i, width - i, height - i, col);
            }
        }
    }
}
