package com.xenowarriors.client.kaeru;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.network.ScentPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayDeque;

/**
 * Kaeru's scent trail: once she picks a scent (ScentScreen), the path it has walked shows as a trail of faint orange
 * wisps on the ground, and a dotted line points from her to it - until the scent fades (45 s).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ScentClient {
    private ScentClient() {}

    private static final DustParticleOptions TRAIL = new DustParticleOptions(new Vector3f(1F, 0.55F, 0.15F), 0.9F);
    private static final DustParticleOptions LEAD = new DustParticleOptions(new Vector3f(1F, 0.8F, 0.4F), 0.6F);
    private static int target = -1;
    private static long until;
    private static final ArrayDeque<Vec3> PATH = new ArrayDeque<>();
    /** (Tests) trail points so far. */
    public static int points() { return PATH.size(); }
    public static int target() { return target; }

    public static void received(ScentPacket m) {
        Minecraft mc = Minecraft.getInstance();
        if (m.entries().size() == 1 && m.entries().get(0).kind() < 0) {
            target = m.entries().get(0).id();
            until = mc.level == null ? 0 : mc.level.getGameTime() + m.entries().get(0).distance();
            PATH.clear();
            if (mc.screen instanceof ScentScreen) mc.setScreen(null);
            return;
        }
        mc.setScreen(new ScentScreen(m.entries()));
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (target < 0 || mc.level == null || mc.player == null || mc.isPaused()) return;
        if (mc.level.getGameTime() > until) { target = -1; PATH.clear(); return; }
        Entity t = mc.level.getEntity(target);
        if (t != null && mc.level.getGameTime() % 4 == 0) {                     // where it has been
            Vec3 at = t.position();
            if (PATH.isEmpty() || PATH.peekLast().distanceToSqr(at) > 0.6 * 0.6) PATH.addLast(at);
            while (PATH.size() > 80) PATH.removeFirst();
        }
        if (mc.level.getGameTime() % 3 != 0) return;
        for (Vec3 p : PATH) {                                                   // the trail: wisps over its footprints
            if (p.distanceToSqr(mc.player.position()) > 48 * 48 || mc.level.random.nextInt(3) != 0) continue;
            mc.level.addParticle(TRAIL, p.x + (mc.level.random.nextDouble() - 0.5) * 0.3, p.y + 0.15, p.z + (mc.level.random.nextDouble() - 0.5) * 0.3, 0, 0.01, 0);
        }
        if (t != null) {                                                        // and a dotted line from her toward it
            Vec3 from = mc.player.position().add(0, 0.6, 0), to = t.position().add(0, t.getBbHeight() * 0.5, 0);
            double d = from.distanceTo(to);
            Vec3 step = to.subtract(from).normalize();
            for (double k = 1.5; k < Math.min(d, 12); k += 1.5) {
                Vec3 q = from.add(step.scale(k));
                mc.level.addParticle(LEAD, q.x, q.y, q.z, 0, 0, 0);
            }
        }
    }
}
