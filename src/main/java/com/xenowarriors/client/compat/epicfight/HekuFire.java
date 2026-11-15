package com.xenowarriors.client.compat.epicfight;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The Hekublade's broadblade swings (Epic Fight battle mode) burn: as the blade comes round, a sweep of fire, embers and
 * a few lava sparks follows its arc in front of Mikiko, and a little flame flares where it lands.
 */
public final class HekuFire {
    private HekuFire() {}

    private record Swing(Player p, int at, int side, int left) {}
    private static final java.util.List<Swing> LIVE = new java.util.ArrayList<>();
    public static int swings;

    /** A new swing: the arc goes out when the blade lands (hitTicks after now). */
    public static void swing(Player p, float hitTicks) {
        int at = p.tickCount + Math.max(1, Math.round(hitTicks > 0 ? hitTicks : 5));
        LIVE.removeIf(s -> s.p == p);
        LIVE.add(new Swing(p, at - 2, (swings & 1) == 0 ? 1 : -1, 5));
        swings++;
    }

    public static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) { if (mc.level == null) LIVE.clear(); return; }
        for (var it = LIVE.listIterator(); it.hasNext(); ) {
            Swing s = it.next();
            if (s.p.isRemoved() || s.p.level() != mc.level) { it.remove(); continue; }
            int age = s.p.tickCount - s.at;
            if (age < 0) continue;
            if (age >= s.left) { it.remove(); continue; }
            arc(s.p, s.side, age / (float) (s.left - 1));
        }
    }

    /** One step of the sweep (f: 0 -> 1 across it): a few embers along the arc, and one small flame where it lands. No Minecraft flame sprites (the user: too much fire, and not the standard fire). */
    private static void arc(Player p, int side, float f) {
        var level = p.level();
        var r = level.random;
        float yaw = p.getYRot();
        double from = 70 * side, to = -70 * side;
        double a = Math.toRadians(yaw + from + (to - from) * f);
        Vec3 c = p.position().add(0, p.getBbHeight() * 0.6, 0);
        for (double rad = 1.2; rad <= 2.4; rad += 0.6) {
            if (r.nextInt(2) != 0) continue;
            double x = c.x - Math.sin(a) * rad, z = c.z + Math.cos(a) * rad, y = c.y + (r.nextDouble() - 0.5) * 0.3;
            level.addParticle(EMBER, x, y, z, -Math.sin(a) * 0.03, 0.02, Math.cos(a) * 0.03);
        }
        if (f >= 0.99F) {
            double ax = c.x - Math.sin(Math.toRadians(yaw)) * 1.8, az = c.z + Math.cos(Math.toRadians(yaw)) * 1.8;
            com.xenowarriors.compat.Effeks.play(level, com.xenowarriors.compat.Effeks.FLAME2, new Vec3(ax, c.y - 0.3, az), 0.35F, yaw);   // (AAA Particles)
        }
    }

    private static final net.minecraft.core.particles.DustParticleOptions EMBER =
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1F, 0.42F, 0.08F), 0.9F);
}
