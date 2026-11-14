package com.xenowarriors.client.compat.epicfight;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
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

    /** One step of the sweep (f: 0 -> 1 across it). */
    private static void arc(Player p, int side, float f) {
        var level = p.level();
        var r = level.random;
        float yaw = p.getYRot();
        double from = 70 * side, to = -70 * side;
        double a = Math.toRadians(yaw + from + (to - from) * f);
        Vec3 c = p.position().add(0, p.getBbHeight() * 0.6, 0);
        for (double rad = 1.0; rad <= 2.4; rad += 0.35) {
            double x = c.x - Math.sin(a) * rad, z = c.z + Math.cos(a) * rad, y = c.y + (r.nextDouble() - 0.5) * 0.3;
            level.addParticle(ParticleTypes.FLAME, x, y, z, -Math.sin(a) * 0.04, 0.02, Math.cos(a) * 0.04);
            if (r.nextInt(3) == 0) level.addParticle(ParticleTypes.SMALL_FLAME, x, y + 0.1, z, 0, 0.03, 0);
            if (r.nextInt(6) == 0) level.addParticle(ParticleTypes.LAVA, x, y, z, 0, 0, 0);
        }
        if (f >= 0.99F) {
            double ax = c.x - Math.sin(Math.toRadians(yaw)) * 1.8, az = c.z + Math.cos(Math.toRadians(yaw)) * 1.8;
            for (int i = 0; i < 8; i++)
                level.addParticle(ParticleTypes.FLAME, ax, c.y - 0.3, az, (r.nextDouble() - 0.5) * 0.15, r.nextDouble() * 0.12, (r.nextDouble() - 0.5) * 0.15);
        }
    }
}
