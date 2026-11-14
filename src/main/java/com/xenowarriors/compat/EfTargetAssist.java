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
        } catch (Throwable ignored) {}
        CACHE.put(p, new Object[]{p.tickCount, t});
        if (t != null) assists++;
        return t;
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
