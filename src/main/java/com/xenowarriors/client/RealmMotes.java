package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/** Inside the Hekublade: a few faint blue motes drifting slowly up around you - very sparse. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RealmMotes {
    private static final Vector3f BLUE = new Vector3f(0.35F, 0.6F, 1F), PALE = new Vector3f(0.6F, 0.8F, 1F);

    private RealmMotes() {}

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.player == null || mc.isPaused()) return;
        if (!mc.level.dimension().equals(com.xenowarriors.mikiko.HekubladeRealm.KEY)) return;
        var r = mc.player.getRandom();
        if (r.nextInt(3) != 0) return;                          // about one every few ticks
        double x = mc.player.getX() + (r.nextDouble() - 0.5) * 24, z = mc.player.getZ() + (r.nextDouble() - 0.5) * 24;
        double y = mc.player.getY() + r.nextDouble() * 8 - 1;
        mc.level.addParticle(new DustParticleOptions(r.nextBoolean() ? BLUE : PALE, 0.5F + r.nextFloat() * 0.4F),
                x, y, z, (r.nextDouble() - 0.5) * 0.01, 0.015 + r.nextDouble() * 0.015, (r.nextDouble() - 0.5) * 0.01);
    }
}
