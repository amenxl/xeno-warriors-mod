package com.xenowarriors.corrupted;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Corrupted souls (and the C-Soul) stay low: no more than {@link #MAX} blocks over the ground under them - unless the
 * player they're after is flying, then they follow them up.
 */
public final class SoulAltitude {
    public static final double MAX = 10;
    private static final double PROBE = 24;

    private SoulAltitude() {}

    /** Flying for real: creative/skill flight, an elytra, or hanging in the air well off the ground and not falling (other mods' flight). */
    public static boolean flying(LivingEntity t) {
        if (t == null) return false;
        if (t instanceof Player p && p.getAbilities().flying) return true;
        if (t.isFallFlying()) return true;
        if (!(t instanceof Player)) return t.isNoGravity() && !t.onGround();
        return t instanceof Player && !t.onGround() && !t.isInWater() && !t.isPassenger() && t.fallDistance < 1F && above(t) > 3.5;
    }

    /** Blocks between it and the ground under it ({@link #PROBE} if nothing's that close). */
    public static double above(net.minecraft.world.entity.Entity e) {
        Vec3 a = e.position();
        var hit = e.level().clip(new ClipContext(a, a.subtract(0, PROBE, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, e));
        return hit.getType() == HitResult.Type.MISS ? PROBE : a.y - hit.getLocation().y;
    }

    /** Call every server tick: over the limit (and its target isn't flying), it's pulled back down. Returns how high it is. */
    public static double tick(Mob m) {
        double h = above(m);
        if (flying(m.getTarget())) return h;
        if (h > MAX) {
            Vec3 v = m.getDeltaMovement();
            double down = -Math.min(0.35, 0.06 + (h - MAX) * 0.04);
            m.setDeltaMovement(v.x, Math.min(v.y, down), v.z);
        } else if (h > MAX - 1.5 && m.getDeltaMovement().y > 0) {
            Vec3 v = m.getDeltaMovement();
            m.setDeltaMovement(v.x, v.y * 0.3, v.z);                 // eases up to the ceiling instead of bouncing off it
        }
        return h;
    }
}
