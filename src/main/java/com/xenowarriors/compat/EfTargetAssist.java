package com.xenowarriors.compat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Epic Fight's dashing attacks (Kaeru's Sparks opener, and others) stop at the player's target - but a player only has
 * one while locked on, so without it the dash flies straight past whoever is in front. With no lock-on target, the
 * nearest enemy just in front of you counts (see the mixins on the player patches' getTarget).
 */
public final class EfTargetAssist {
    private EfTargetAssist() {}

    private static final double RANGE = 6.5, CONE_COS = Math.cos(Math.toRadians(50));
    /** (client) our own lock-on target, and who counts as a foe (LockOn.attackable). */
    public static java.util.function.Function<Player, LivingEntity> clientLock = p -> null;
    public static java.util.function.Predicate<LivingEntity> clientFoe = e -> true;

    private static final java.util.Map<Player, Object[]> LAST = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static final java.util.Map<Player, Object[]> CACHE = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    public static int assists;

    public static LivingEntity target(Player p) {
        if (p == null) return null;
        Object[] c = CACHE.get(p);
        if (c != null && (int) c[0] == p.tickCount) return (LivingEntity) c[1];
        LivingEntity t = null;
        try {
            if (p.level().isClientSide) t = clientLock.apply(p);
            if (t == null) t = pick(p);
            // (sticky for a moment: a lunge that carries you level with it doesn't lose it)
            Object[] last = LAST.get(p);
            if (t == null && last != null && p.tickCount - (int) last[0] < 15 && last[1] instanceof LivingEntity lt && lt.isAlive()
                    && lt.distanceToSqr(p) < RANGE * RANGE) t = lt;
            if (t != null) LAST.put(p, new Object[]{p.tickCount, t});
        } catch (Throwable ignored) {}
        CACHE.put(p, new Object[]{p.tickCount, t});
        if (t != null) assists++;
        return t;
    }

    /** (Tests) times a lunge was stopped at the enemy instead of running through it. */
    public static int stops;

    /**
     * An attack's lunge stops at the enemy (Kyrie's Iron Fist carried her straight through and past the dummy - the
     * hits after the first then hit nothing): the step Epic Fight is about to move the player this tick, cut short
     * at the target.
     */
    public static Vec3 stopAtTarget(Player p, Vec3 step) {
        LivingEntity t = target(p);
        if (t == null || step == null || t.level() != p.level()) return step;
        double contact = (t.getBbWidth() + p.getBbWidth()) / 2 + 0.1;
        double nx = p.getX() + step.x, nz = p.getZ() + step.z;
        double dx = t.getX() - nx, dz = t.getZ() - nz;
        double after = Math.sqrt(dx * dx + dz * dz);
        double bx = t.getX() - p.getX(), bz = t.getZ() - p.getZ();
        double before = Math.sqrt(bx * bx + bz * bz);
        if (before > 3.5) return step;
        Vec3 face = Vec3.directionFromRotation(0, p.getYRot());
        boolean wasAhead = bx * face.x + bz * face.z > 0;
        boolean passes = wasAhead && dx * face.x + dz * face.z < 0.2;           // (it would end up beside or behind you)
        if (after >= contact && !passes) return step;
        stops++;
        // as far along as stays in front of it, at arm's length
        double lo = 0, hi = 1;
        for (int i = 0; i < 8; i++) {
            double m = (lo + hi) / 2, mx = t.getX() - (p.getX() + step.x * m), mz = t.getZ() - (p.getZ() + step.z * m);
            boolean ok = Math.sqrt(mx * mx + mz * mz) >= contact && (!wasAhead || mx * face.x + mz * face.z >= 0.2);
            if (ok) lo = m; else hi = m;
        }
        return new Vec3(step.x * lo, step.y, step.z * lo);
    }

    private static LivingEntity pick(Player p) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = Vec3.directionFromRotation(0, p.getYRot());
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(RANGE))) {
            if (e == p || !e.isAlive() || e.isInvulnerable() || p.isAlliedTo(e) || e.isPassengerOfSameVehicle(p)) continue;
            if (!foe(p, e)) continue;
            Vec3 to = e.position().subtract(p.position());
            Vec3 flat = new Vec3(to.x, 0, to.z);
            double d = flat.length();
            if (d > RANGE || Math.abs(to.y) > 3) continue;
            if (d > 0.8 && flat.scale(1 / d).dot(look) < CONE_COS) continue;
            if (d < bestD) { bestD = d; best = e; }
        }
        return best;
    }

    private static boolean foe(Player p, LivingEntity e) {
        if (e instanceof Player o && !p.canHarmPlayer(o)) return false;
        if (p.level().isClientSide) return (e instanceof Enemy || e == p.getLastHurtMob()) && clientFoe.test(e);
        if (e instanceof Player o && o != p) return com.xenowarriors.party.Duels.inDuel(p) || p.getLastHurtMob() == o;
        if (e instanceof com.xenowarriors.bot.PartyBotEntity b && p.getUUID().equals(b.ownerId())) return false;
        return e instanceof Enemy || e == p.getLastHurtMob() || e.getLastHurtByMob() == p || (e instanceof Mob m && m.getTarget() == p);
    }
}
