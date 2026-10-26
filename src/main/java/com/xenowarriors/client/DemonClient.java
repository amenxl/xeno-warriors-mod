package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;

/** Demon's Remnants on the client: the glowing red eye face, and a faint red trail from his eyes. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class DemonClient {
    private static final Set<Integer> ON = new HashSet<>();
    private static final DustParticleOptions GLOW = new DustParticleOptions(new Vector3f(1F, 0.08F, 0.1F), 0.45F);

    private DemonClient() {}

    public static void set(int id, boolean on) { if (on) ON.add(id); else ON.remove(id); }

    public static boolean active(int id) { return ON.contains(id); }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || ON.isEmpty() || mc.isPaused()) return;
        for (Player p : mc.level.players()) {
            if (!ON.contains(p.getId()) || p.tickCount % 2 != 0) continue;
            if (p == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            float yaw = p.getYHeadRot() * Mth.DEG_TO_RAD;
            Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)), side = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
            Vec3 eye = p.getEyePosition().add(fwd.scale(0.27)).add(0, 0.02, 0);
            for (int s = -1; s <= 1; s += 2) {
                Vec3 e = eye.add(side.scale(0.09 * s));
                mc.level.addParticle(GLOW, e.x, e.y, e.z, 0, 0, 0);
            }
        }
    }
}
