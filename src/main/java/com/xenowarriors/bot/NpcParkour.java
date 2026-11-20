package com.xenowarriors.bot;

import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.NpcParkourPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Parkour for people who aren't bots - townsfolk, gangs and raiders, the Sky Rebellion - the same moves as BotParkour
 * (with ParCool installed): vaulting a one-block obstacle they run at, climbing a 2-block wall or wall-jumping a 3-block
 * one to get at someone up there, rolling out of a drop, sliding in at their target and side-stepping a swing. The
 * move goes to the clients as one number (NpcParkourPacket) and is drawn by NpcParkourClient.
 */
public final class NpcParkour {
    private NpcParkour() {}

    private static final Map<PathfinderMob, BotParkour.State> STATES = new WeakHashMap<>();
    /** (tests) moves started by NPCs, by code. */
    public static final int[] STARTED = new int[BotParkour.NAMES.length];

    private static BotParkour.State st(PathfinderMob m) { return STATES.computeIfAbsent(m, k -> new BotParkour.State()); }

    private static void start(PathfinderMob m, BotParkour.State s, int code, int ticks, Vec3 dir) {
        s.code = code; s.ticks = ticks; s.dir = dir;
        s.seq = (s.seq + 1) & 0xFFFF;
        STARTED[code]++;
        ModNetwork.sendToTrackingEntityAndSelf(new NpcParkourPacket(m.getId(), BotParkour.pack(s.seq, code)), m);
    }

    private static void end(PathfinderMob m, BotParkour.State s) {
        if (s.code == BotParkour.NONE) return;
        s.code = BotParkour.NONE; s.ticks = 0;
        ModNetwork.sendToTrackingEntityAndSelf(new NpcParkourPacket(m.getId(), BotParkour.pack(s.seq, BotParkour.NONE)), m);
    }

    /** Each server tick (from the NPC's aiStep); {@code may}: on its feet and free to (not flying, not sitting...). */
    public static void tick(PathfinderMob m, boolean may) {
        if (!BotParkour.on() || m.level().isClientSide) return;
        BotParkour.State s = st(m);
        if (s.cd > 0) s.cd--;
        if (s.slideCd > 0) s.slideCd--;
        if (s.dodgeCd > 0) s.dodgeCd--;
        if (!may || m.isPassenger() || m.isNoGravity() || m.isInWater() || m.isInLava() || m.isNoAi()) { end(m, s); return; }
        if (s.code != BotParkour.NONE) { move(m, s); return; }
        m.fallDistance = Math.min(m.fallDistance, 40F);
        LivingEntity t = m.getTarget();
        if (!m.onGround() || s.cd > 0) return;
        Level l = m.level();
        int y = (int) Math.floor(m.getY() + 0.01);
        Vec3 v = m.getDeltaMovement();
        // climbing / wall-jumping up to whoever it's after
        if (t != null && t.isAlive()) {
            Vec3 to = new Vec3(t.getX() - m.getX(), 0, t.getZ() - m.getZ());
            double rise = t.getY() - m.getY();
            if (to.lengthSqr() > 0.25 && rise > 1.2 && rise < 4.5 && to.lengthSqr() < 9 * 9) {
                Vec3 dir = to.normalize();
                for (double reach : new double[]{0.7, 1.3}) {
                    BlockPos front = BlockPos.containing(m.getX() + dir.x * reach, y, m.getZ() + dir.z * reach);
                    int h = wall(l, front);
                    if (h < 2 || h > 3 || !BotMoves.open(l, front.above(h)) || !BotMoves.open(l, front.above(h + 1))) continue;
                    boolean headroom = true;
                    for (int k = 2; k <= h + 1 && headroom; k++) headroom = BotMoves.open(l, m.blockPosition().above(k));
                    if (!headroom) continue;
                    m.getNavigation().stop();
                    s.top = y + h;
                    face(m, dir);
                    if (h == 2) { m.setDeltaMovement(dir.x * 0.12, 0.5, dir.z * 0.12); start(m, s, BotParkour.CLING, 8, dir); }
                    else { m.setDeltaMovement(dir.x * 0.12, 0.62, dir.z * 0.12); start(m, s, m.getRandom().nextBoolean() ? BotParkour.WALLJUMP_R : BotParkour.WALLJUMP_L, 9, dir); }
                    s.cd = 30;
                    return;
                }
            }
        }
        // a vault over a one-block obstacle it's running at
        if (v.horizontalDistanceSqr() > 0.08 * 0.08) {
            Vec3 run = new Vec3(v.x, 0, v.z).normalize();
            for (double reach : new double[]{0.45, 0.9, 1.35}) {
                double r = m.getBbWidth() * 0.5 + reach;
                BlockPos front = BlockPos.containing(m.getX() + run.x * r, y, m.getZ() + run.z * r);
                if (front.equals(m.blockPosition())) continue;
                if (!BotMoves.solid(l, front)) continue;
                BlockPos past = BlockPos.containing(front.getX() + 0.5 + run.x, y, front.getZ() + 0.5 + run.z);
                if (wall(l, front) == 1 && BotMoves.open(l, front.above()) && BotMoves.open(l, front.above(2)) && BotMoves.open(l, m.blockPosition().above(2))
                        && BotMoves.open(l, past) && BotMoves.open(l, past.above()) && BotMoves.dropAt(l, past.getX() + 0.5, y, past.getZ() + 0.5) < 3) {
                    double sp = Math.max(0.3, Math.min(0.45, 0.2 + reach * 0.15));
                    m.setDeltaMovement(run.x * sp, 0.52, run.z * sp);
                    face(m, run);
                    start(m, s, m.getRandom().nextBoolean() ? BotParkour.VAULT_R : BotParkour.VAULT_L, 11, run);
                    s.cd = 20;
                    return;
                }
                break;
            }
        }
        // in a fight: a slide in now and then, a side-step from a close swing
        if (t != null && t.isAlive()) {
            Vec3 to = new Vec3(t.getX() - m.getX(), 0, t.getZ() - m.getZ());
            if (to.lengthSqr() < 0.25) return;
            Vec3 dir = to.normalize();
            double d = Math.sqrt(m.distanceToSqr(t));
            if (s.slideCd == 0 && d > 3.5 && d < 7 && v.horizontalDistanceSqr() > 0.12 * 0.12 && m.getRandom().nextInt(40) == 0 && safe(l, m, dir, y, 5)) {
                face(m, dir);
                m.setDeltaMovement(dir.x * 0.55, 0, dir.z * 0.55);
                start(m, s, BotParkour.SLIDE, 12, dir);
                s.slideCd = 140;
                return;
            }
            if (s.dodgeCd == 0 && d < 3.2 && t.swinging && t.swingTime <= 1 && m.getRandom().nextInt(4) == 0) {
                boolean right = m.getRandom().nextBoolean();
                Vec3 side = new Vec3(-dir.z, 0, dir.x).scale(right ? -1 : 1);
                if (safe(l, m, side, y, 4) && BotMoves.open(l, BlockPos.containing(m.getX() + side.x * 1.2, y, m.getZ() + side.z * 1.2))) {
                    m.setDeltaMovement(side.x * 0.45, 0.12, side.z * 0.45);
                    start(m, s, right ? BotParkour.DODGE_R : BotParkour.DODGE_L, 10, side);
                    s.dodgeCd = 70;
                }
            }
        }
    }

    private static void move(PathfinderMob m, BotParkour.State s) {
        m.fallDistance = 0;
        s.ticks--;
        switch (s.code) {
            case BotParkour.CLING -> {
                m.getNavigation().stop();
                face(m, s.dir);
                if (m.getY() + 1.6 < s.top) m.setDeltaMovement(s.dir.x * 0.1, Math.max(m.getDeltaMovement().y, 0.3), s.dir.z * 0.1);
                else m.setDeltaMovement(s.dir.x * 0.08, 0.0, s.dir.z * 0.08);
                if (s.ticks <= 0) start(m, s, BotParkour.CLIMB, 10, s.dir);
            }
            case BotParkour.WALLJUMP_R, BotParkour.WALLJUMP_L -> {
                face(m, s.dir);
                if (s.ticks == 5) m.setDeltaMovement(s.dir.x * 0.08, 0.55, s.dir.z * 0.08);
                if (s.ticks <= 0) start(m, s, BotParkour.CLING, 6, s.dir);
            }
            case BotParkour.CLIMB -> {
                m.getNavigation().stop();
                face(m, s.dir);
                if (m.getY() < s.top + 0.05) m.setDeltaMovement(s.dir.x * 0.04, Math.min(0.5, s.top + 0.12 - m.getY() + 0.08), s.dir.z * 0.04);
                else m.setDeltaMovement(s.dir.x * 0.3, Math.max(0, m.getDeltaMovement().y), s.dir.z * 0.3);
                if (s.ticks <= 0 || m.getY() >= s.top && m.onGround()) end(m, s);
            }
            case BotParkour.ROLL -> {
                double k = 0.25 * Math.max(0, s.ticks) / 9.0 + 0.05;
                m.setDeltaMovement(s.dir.x * k, m.getDeltaMovement().y, s.dir.z * k);
                if (s.ticks <= 0) end(m, s);
            }
            case BotParkour.SLIDE -> {
                double k = 0.55 * Math.max(0, s.ticks) / 12.0;
                m.setDeltaMovement(s.dir.x * k, m.getDeltaMovement().y, s.dir.z * k);
                if (s.ticks <= 0 || m.horizontalCollision) end(m, s);
            }
            default -> { if (s.ticks <= 0) end(m, s); }
        }
    }

    /** Landing from a drop: a roll - no harm up to 8 blocks, less after. Returns the fall distance left to hurt. */
    public static float land(PathfinderMob m, float dist) {
        if (!BotParkour.on() || m.level().isClientSide || m.isPassenger() || dist < 2.5F) return dist;
        Vec3 v = m.getDeltaMovement();
        Vec3 dir = v.horizontalDistanceSqr() > 1e-4 ? new Vec3(v.x, 0, v.z).normalize() : Vec3.directionFromRotation(0, m.getYRot());
        start(m, st(m), BotParkour.ROLL, 9, dir);
        return dist <= 8F ? 0F : dist - 8F;
    }

    private static boolean safe(Level l, PathfinderMob m, Vec3 dir, int y, int blocks) {
        for (double k = 0.5; k <= blocks; k += 0.5) if (BotMoves.dropAt(l, m.getX() + dir.x * k, y, m.getZ() + dir.z * k) >= 2) return false;
        return true;
    }

    private static int wall(Level l, BlockPos at) {
        int h = 0;
        while (h < 4 && BotMoves.solid(l, at.above(h))) h++;
        return h;
    }

    private static void face(PathfinderMob m, Vec3 dir) {
        if (dir.horizontalDistanceSqr() > 1e-4) BotMoves.face(m, (float) Math.toDegrees(Math.atan2(-dir.x, dir.z)), 45F);
    }
}
