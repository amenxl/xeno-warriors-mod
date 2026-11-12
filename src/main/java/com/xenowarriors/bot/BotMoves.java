package com.xenowarriors.bot;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * How bot teammates get about: flying under their own power (characters with wings or wind), leaping short gaps,
 * stopping at cliff edges they'd get hurt dropping off, and finding the way out of water and lava.
 */
public final class BotMoves {
    private BotMoves() {}

    /** A drop of this many blocks or more is a cliff (unless there's water at the bottom). */
    public static final int CLIFF = 4;

    // ---------------------------------------------------------------- flying

    /** Fly straight toward a point (gravity off while it does). speed: the most it covers a tick. */
    static void flyTo(PartyBotEntity b, Vec3 goal, double speed) {
        b.getNavigation().stop();
        b.selfFlying = true;
        b.flewAt = b.tickCount;
        b.setNoGravity(true);
        Vec3 d = goal.subtract(b.position());
        double dist = d.length();
        Vec3 v = dist < 0.3 ? Vec3.ZERO : d.normalize().scale(Math.min(speed, Math.max(0.12, dist * 0.35)));
        if (b.horizontalCollision) v = v.add(0, 0.25, 0);                      // (a wall in the way: up and over)
        b.setDeltaMovement(b.getDeltaMovement().scale(0.5).add(v.scale(0.5)));
        b.fallDistance = 0;
        if (d.horizontalDistanceSqr() > 0.5) {
            float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            b.setYRot(yaw); b.yBodyRot = yaw; b.yHeadRot = yaw;
        }
    }

    /** Stops flying under its own power: gravity back on (it doesn't get hurt landing). */
    static void land(PartyBotEntity b) {
        if (!b.selfFlying) return;
        b.selfFlying = false;
        if (!b.flyingWithOwner && !b.isVehicle()) b.setNoGravity(false);
    }

    /** How high it is above whatever is under it (blocks or liquid), up to 24. */
    public static double heightAboveGround(LivingEntity e) {
        Vec3 from = e.position(), to = from.subtract(0, 24, 0);
        var hit = e.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, e));
        return hit.getType() == HitResult.Type.MISS ? 24 : from.y - hit.getLocation().y;
    }

    /** In the air and not about to land: something a bot on foot can't hit (a flying mob, someone flying, a phantom). */
    public static boolean airborne(LivingEntity t) {
        if (t.onGround() || t.isInWater() || t.isInLava() || t.isPassenger()) return false;
        return heightAboveGround(t) > 2.2;
    }

    // ---------------------------------------------------------------- cliffs and gaps

    private static boolean solid(Level l, BlockPos p) {
        BlockState s = l.getBlockState(p);
        return !s.getCollisionShape(l, p).isEmpty();
    }

    private static boolean open(Level l, BlockPos p) {
        BlockState s = l.getBlockState(p);
        return s.getCollisionShape(l, p).isEmpty() && !s.getFluidState().is(FluidTags.LAVA);
    }

    /**
     * How far it would fall stepping onto this column (feet at y): 0 if there's ground (or water) right there, else the
     * number of empty blocks under it (up to CLIFF+2). Lava at the bottom counts as a long way down.
     */
    public static int dropAt(Level l, double x, int y, double z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, y - 1, z);
        for (int i = 0; i <= CLIFF + 1; i++) {
            BlockState s = l.getBlockState(p);
            if (s.getFluidState().is(FluidTags.LAVA)) return CLIFF + 2;
            if (s.getFluidState().is(FluidTags.WATER)) return i >= 2 ? 0 : i;    // (deep enough water breaks the fall)
            if (!s.getCollisionShape(l, p).isEmpty()) return i;
            p.move(0, -1, 0);
        }
        return CLIFF + 2;
    }

    /** Walking this way (this tick's movement) would take it over a cliff. */
    static boolean cliffAhead(PartyBotEntity b, Vec3 move) {
        Vec3 h = new Vec3(move.x, 0, move.z);
        if (h.lengthSqr() < 1e-6) return false;
        Vec3 ahead = b.position().add(h).add(h.normalize().scale(b.getBbWidth() * 0.5 + 0.05));
        int y = (int) Math.floor(b.getY() + 0.01);
        if (solid(b.level(), BlockPos.containing(ahead.x, y - 1, ahead.z))) return false;
        return dropAt(b.level(), ahead.x, y, ahead.z) >= CLIFF;
    }

    /**
     * A gap between it and where it's going (a missing block or three, a ditch): a running jump across if there's
     * somewhere to land 2-4 blocks on (level, one up, or a bit down), with room for its head. True if it leapt.
     */
    static boolean tryLeap(PartyBotEntity b, Vec3 goal) {
        if (!b.onGround() || b.isInWater() || b.isInLava() || b.leapTicks > 0) return false;
        Vec3 d = new Vec3(goal.x - b.getX(), 0, goal.z - b.getZ());
        if (d.lengthSqr() < 2.0 * 2.0) return false;
        Vec3 dir = d.normalize();
        Level l = b.level();
        int y = (int) Math.floor(b.getY() + 0.01);
        Vec3 first = b.position().add(dir.scale(0.9));
        if (dropAt(l, first.x, y, first.z) < 2) return false;                 // (no gap right in front)
        for (int k = 2; k <= 4; k++) {
            Vec3 at = b.position().add(dir.scale(k));
            for (int dy : new int[]{0, 1, -1}) {
                BlockPos floor = BlockPos.containing(at.x, y - 1 + dy, at.z);
                if (!solid(l, floor) || !open(l, floor.above()) || !open(l, floor.above(2))) continue;
                if (dy == 1 && !open(l, BlockPos.containing(b.getX(), y + 2, b.getZ()))) continue;   // (room to jump up)
                boolean clear = true;                                           // nothing in the way along the arc
                for (int j = 1; j < k && clear; j++) {
                    Vec3 m = b.position().add(dir.scale(j));
                    if (!open(l, BlockPos.containing(m.x, y + 1, m.z)) || !open(l, BlockPos.containing(m.x, y + 2, m.z))) clear = false;
                }
                if (!clear) continue;
                double sp = 0.2 * k + 0.05;                                    // (the first tick on the ground loses about half to friction)
                b.getNavigation().stop();
                b.setDeltaMovement(dir.x * sp, dy > 0 ? 0.52 : 0.42, dir.z * sp);
                b.leapTicks = 20;
                float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
                b.setYRot(yaw); b.yBodyRot = yaw;
                b.lastLeap = "gap " + k + " dy " + dy + " from " + b.blockPosition().toShortString();
                return true;
            }
        }
        return false;
    }

    /** Bumping into a one-block step it didn't jump (no path told it to): hop up. */
    static void stepUp(PartyBotEntity b) {
        if (!b.onGround() || !b.horizontalCollision || b.isNoGravity()) return;
        Vec3 look = Vec3.directionFromRotation(0, b.getYRot());
        BlockPos front = BlockPos.containing(b.getX() + look.x * 0.8, b.getY() + 0.01, b.getZ() + look.z * 0.8);
        Level l = b.level();
        if (solid(l, front) && open(l, front.above()) && open(l, front.above(2)) && open(l, b.blockPosition().above(2))) b.getJumpControl().jump();
    }

    // ---------------------------------------------------------------- water and lava

    /** Somewhere dry to stand near it (nearest first; near the owner if there's a choice). */
    static BlockPos dryLand(PartyBotEntity b, int r) {
        Level l = b.level();
        BlockPos c = b.blockPosition(), best = null;
        var o = b.owner();
        double bd = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -3, -r), c.offset(r, 4, r))) {
            if (!solid(l, p.below()) || !open(l, p) || !open(l, p.above())) continue;
            if (!l.getFluidState(p).isEmpty() || !l.getFluidState(p.above()).isEmpty() || l.getBlockState(p.below()).getFluidState().is(FluidTags.LAVA)) continue;
            if (l.getBlockState(p.below()).is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK) || nearLava(l, p)) continue;
            double dd = p.distSqr(c) + (p.getY() < c.getY() ? 4 : 0) + (o != null && o.level() == l ? p.distSqr(o.blockPosition()) * 0.05 : 0);
            if (dd < bd) { bd = dd; best = p.immutable(); }
        }
        return best;
    }

    private static boolean nearLava(Level l, BlockPos p) {
        for (var d : net.minecraft.core.Direction.Plane.HORIZONTAL) if (l.getFluidState(p.relative(d)).is(FluidTags.LAVA)) return true;
        return false;
    }

    /** Swimming toward a spot on the shore: up to the surface, across, and a boost over the edge. */
    static void swimToward(PartyBotEntity b, BlockPos spot, boolean lava) {
        Vec3 to = Vec3.atBottomCenterOf(spot).subtract(b.position());
        Vec3 h = new Vec3(to.x, 0, to.z);
        double push = lava ? 0.11 : 0.07;
        Vec3 v = b.getDeltaMovement();
        if (h.lengthSqr() > 0.01) v = v.add(h.normalize().scale(push));
        boolean under = b.isEyeInFluid(FluidTags.WATER) || b.isEyeInFluid(FluidTags.LAVA);
        if (under || to.y > 0.2) v = new Vec3(v.x, Math.max(v.y, 0.12), v.z);    // up to the surface
        if (b.horizontalCollision) v = new Vec3(v.x, Math.max(v.y, 0.38), v.z); // the bank: climb out over it
        double hs = Math.sqrt(v.x * v.x + v.z * v.z), cap = lava ? 0.32 : 0.28;
        if (hs > cap) v = new Vec3(v.x / hs * cap, v.y, v.z / hs * cap);
        b.setDeltaMovement(v.x, Math.min(0.45, v.y), v.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
        b.setYRot(yaw); b.yBodyRot = yaw;
    }

    /** (Tests) strokes swum after the owner. */
    public static int swimStrokes;

    /** Swimming after someone (you in the water, or in a boat): straight at them, at the surface, faster the further behind. */
    static void swimAfter(PartyBotEntity b, Vec3 target) {
        b.getNavigation().stop();
        Vec3 to = target.subtract(b.position());
        Vec3 h = new Vec3(to.x, 0, to.z);
        double d = h.length();
        Vec3 v = b.getDeltaMovement();
        if (d > 0.1) v = v.add(h.scale(1 / d).scale(d > 10 ? 0.12 : 0.08));
        boolean under = b.isEyeInFluid(FluidTags.WATER);
        if (under || to.y > 0.6) v = new Vec3(v.x, Math.max(v.y, 0.1), v.z);      // up to the surface (air), and up to them
        if (b.horizontalCollision) v = new Vec3(v.x, Math.max(v.y, 0.38), v.z); // a bank or a boat's side: up over it
        double hs = Math.sqrt(v.x * v.x + v.z * v.z), cap = d > 10 ? 0.42 : 0.3;
        if (hs > cap) v = new Vec3(v.x / hs * cap, v.y, v.z / hs * cap);
        b.setDeltaMovement(v.x, Math.min(0.45, v.y), v.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
        b.setYRot(yaw); b.yBodyRot = yaw; b.setYHeadRot(yaw);
        swimStrokes++;
    }
}
