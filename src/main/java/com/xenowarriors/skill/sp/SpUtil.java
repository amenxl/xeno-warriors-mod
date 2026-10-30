package com.xenowarriors.skill.sp;

import com.xenowarriors.skill.ServerScheduler;
import com.xenowarriors.skill.SkillTargeting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Small helpers for the Sky Patrol skills. */
public final class SpUtil {
    private SpUtil() {}

    /** What she's aiming at, else the closest valid target in front within range. */
    public static LivingEntity front(ServerPlayer p, double range) {
        LivingEntity t = SkillTargeting.aimed(p, range, 10);
        if (t != null) return t;
        Vec3 look = p.getLookAngle(), eye = p.getEyePosition();
        double best = Double.MAX_VALUE;
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range))) {
            if (e == p || !SkillTargeting.canTarget(p, e)) continue;
            Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0).subtract(eye);
            if (to.length() > range || to.normalize().dot(look) < 0.5) continue;
            if (to.length() < best) { best = to.length(); t = e; }
        }
        return t;
    }

    public static List<LivingEntity> around(ServerPlayer p, Vec3 c, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(c, c).inflate(r)))
            if (e != p && SkillTargeting.canTarget(p, e) && e.position().distanceTo(c) <= r) out.add(e);
        return out;
    }

    /** How far a dash from `start` along `dir` gets before a wall. */
    public static double pathReach(ServerPlayer p, Vec3 start, Vec3 dir, double len) {
        Vec3 from = start.add(0, 1, 0), to = from.add(dir.scale(len));
        var hit = p.level().clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? len : Math.max(0, hit.getLocation().distanceTo(from) - 0.3);
    }

    /**
     * Everyone along the dash so far: from where it started to how far it should have got by now, and around where the
     * player actually is. (The server only learns the player's real position from their client a tick or two later, so a
     * short dash checked only at the server's position would end before it ever reached anyone.)
     */
    public static List<LivingEntity> swept(ServerPlayer p, Vec3 start, Vec3 dir, double dist, double r) {
        List<LivingEntity> out = new ArrayList<>(around(p, p.position().add(0, 1, 0), r));
        Vec3 a = start.add(0, 1, 0), b = a.add(dir.scale(dist));
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(a, b).inflate(r))) {
            if (e == p || out.contains(e) || !SkillTargeting.canTarget(p, e)) continue;
            Vec3 c = e.position().add(0, e.getBbHeight() / 2, 0);
            double t = Math.max(0, Math.min(dist, c.subtract(a).dot(dir)));
            if (c.distanceTo(a.add(dir.scale(t))) <= r + e.getBbWidth() / 2) out.add(e);
        }
        return out;
    }

    /** A dash along the look direction over `ticks`; each entity it passes through is hit once. */
    public static void dash(ServerPlayer p, double speed, int ticks, ParticleOptions trail, Consumer<LivingEntity> onHit) {
        Vec3 dir = p.getLookAngle().multiply(1, 0.35, 1).normalize();
        Set<Integer> hit = new HashSet<>();
        Vec3 start = p.position();
        double reach = pathReach(p, start, dir, speed * ticks);
        for (int i = 0; i < ticks; i++) {
            int ii = i;
            ServerScheduler.later(i, () -> {
                if (!p.isAlive()) return;
                p.setDeltaMovement(dir.scale(speed)); p.hurtMarked = true; p.fallDistance = 0;
                ServerLevel l = p.serverLevel();
                l.sendParticles(trail, p.getX(), p.getY() + 1, p.getZ(), 3, 0.2, 0.3, 0.2, 0.01);
                for (LivingEntity e : swept(p, start, dir, Math.min(reach, (ii + 1) * speed), 1.8)) if (hit.add(e.getId())) onHit.accept(e);
            });
        }
        ServerScheduler.later(ticks, () -> { p.setDeltaMovement(p.getDeltaMovement().scale(0.2)); p.hurtMarked = true; });
    }

    static void line(ServerLevel l, Vec3 a, Vec3 b, ParticleOptions o, int n) {
        for (int i = 0; i <= n; i++) { Vec3 q = a.lerp(b, i / (double) n); l.sendParticles(o, q.x, q.y, q.z, 1, 0, 0, 0, 0); }
    }
}
