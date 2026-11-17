package com.xenowarriors.bot;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

/**
 * Bots doing ParCool-style parkour (ParCool itself only works for real players). With ParCool installed, a bot on foot:
 * <ul>
 *   <li>vaults over a one-block obstacle it runs at;</li>
 *   <li>grabs the edge of a 2-block wall and climbs up when what it's going for is up there;</li>
 *   <li>a 3-block wall: jumps, kicks off the wall (wall jump), grabs the edge and climbs up;</li>
 *   <li>rolls when it lands from a drop (3+ blocks): no fall damage up to 8 blocks, less after;</li>
 *   <li>in a fight, now and then slides in at its target, or side-steps (dodges) a swing;</li>
 *   <li>a gap it leaps across (BotMoves.tryLeap) is a cat leap; running to catch you up is ParCool's fast run.</li>
 * </ul>
 * The move playing is sent to the clients (one number) and drawn with ParCool's own poses on the bot's body
 * (client ParcoolBots). Without ParCool none of this happens: bots move as before.
 */
public final class BotParkour {
    private BotParkour() {}

    public static final int NONE = 0, VAULT_R = 1, VAULT_L = 2, CLING = 3, CLIMB = 4, WALLJUMP_R = 5, WALLJUMP_L = 6, ROLL = 7,
            SLIDE = 8, FAST_RUN = 9, DODGE_L = 10, DODGE_R = 11, LEAP = 12;
    public static final String[] NAMES = {"none", "vault", "vault", "cling", "climb up", "wall jump", "wall jump", "roll", "slide", "fast run", "dodge", "dodge", "cat leap"};

    /** (tests) how many times each move started, by code. */
    public static final int[] STARTED = new int[NAMES.length];

    private static Boolean parcool;
    /** (tests) on even without ParCool. */
    public static boolean force;

    public static boolean on() {
        if (force) return true;
        if (parcool == null) parcool = ModList.get() != null && ModList.get().isLoaded("parcool");
        return parcool;
    }

    /** A bot's parkour state (server side). */
    public static final class State {
        int code, ticks, stuck, cd, slideCd, dodgeCd, seq;
        double top;               // (climbing: the top of the wall)
        Vec3 dir = Vec3.ZERO;     // (which way the move goes)
        public int code() { return code; }
    }

    /** The number sent to clients: the move in the low 5 bits, a counter above (so the same move twice restarts its pose). */
    public static int pack(int seq, int code) { return (seq << 5) | code; }
    public static int codeOf(int packed) { return packed & 31; }

    private static void start(PartyBotEntity b, int code, int ticks, Vec3 dir) {
        State s = b.pk;
        s.code = code; s.ticks = ticks; s.dir = dir;
        s.seq = (s.seq + 1) & 0xFFFF;
        b.setParkour(pack(s.seq, code));
        STARTED[code]++;
    }

    private static void end(PartyBotEntity b) {
        State s = b.pk;
        if (s.code == NONE) return;
        s.code = NONE; s.ticks = 0;
        b.setParkour(pack(s.seq, NONE));
    }

    private static boolean able(PartyBotEntity b) {
        return on() && !b.downed() && !b.held() && !b.isPassenger() && !b.isNoGravity() && !b.selfFlying && !b.isInWater() && !b.isInLava() && !b.isNoAi();
    }

    /** Each server tick, before it moves. */
    static void tick(PartyBotEntity b) {
        State s = b.pk;
        if (s.cd > 0) s.cd--;
        if (s.slideCd > 0) s.slideCd--;
        if (s.dodgeCd > 0) s.dodgeCd--;
        if (!able(b)) { end(b); s.stuck = 0; return; }
        if (s.code != NONE && s.code != FAST_RUN) { move(b, s); return; }
        b.fallDistance = Math.min(b.fallDistance, 40F);
        boolean ground = b.onGround();
        Goal g = goal(b);
        // running to catch up with you: ParCool's fast run (the follow speed already goes up when it's far behind)
        boolean fast = ground && g != null && g.owner && g.at.distanceToSqr(b.position()) > 10 * 10 && b.getDeltaMovement().horizontalDistanceSqr() > 0.15 * 0.15;
        if (fast && s.code != FAST_RUN) start(b, FAST_RUN, 0, Vec3.ZERO);
        else if (!fast && s.code == FAST_RUN) end(b);
        if (!ground || g == null || s.cd > 0) { s.stuck = 0; return; }
        Vec3 to = new Vec3(g.at.x - b.getX(), 0, g.at.z - b.getZ());
        if (to.lengthSqr() < 0.25) return;
        Vec3 dir = to.normalize();
        Level l = b.level();
        int y = (int) Math.floor(b.getY() + 0.01);
        double rise = g.at.y - b.getY();
        // a wall between it and what it's after, which is up there: climb it (2 high) or wall-jump up it (3 high)
        if (rise > 1.2 && rise < 4.5 && to.lengthSqr() < 9 * 9) {
            for (double reach : new double[]{0.7, 1.3}) {
                BlockPos front = BlockPos.containing(b.getX() + dir.x * reach, y, b.getZ() + dir.z * reach);
                int h = wall(l, front);
                if (h < 2 || h > 3) continue;
                if (!BotMoves.open(l, front.above(h)) || !BotMoves.open(l, front.above(h + 1))) continue;   // (room to stand on top)
                boolean headroom = true;
                for (int k = 2; k <= h + 1 && headroom; k++) headroom = BotMoves.open(l, b.blockPosition().above(k));
                if (!headroom) continue;
                b.getNavigation().stop();
                s.top = y + h;
                face(b, dir);
                if (h == 2) { b.setDeltaMovement(dir.x * 0.12, 0.5, dir.z * 0.12); start(b, CLING, 8, dir); }
                else { b.setDeltaMovement(dir.x * 0.12, 0.62, dir.z * 0.12); start(b, wallSide(b, dir) ? WALLJUMP_R : WALLJUMP_L, 9, dir); }
                s.cd = 30;
                return;
            }
        }
        // running at a one-block obstacle (with a landing past it): vault over (looked for a little way ahead, before
        //  the path's own jump takes it up)
        Vec3 v = b.getDeltaMovement();
        if (v.horizontalDistanceSqr() > 0.08 * 0.08) {
            Vec3 run = new Vec3(v.x, 0, v.z).normalize();
            for (double reach : new double[]{0.45, 0.9, 1.35}) {
                double r = b.getBbWidth() * 0.5 + reach;
                BlockPos front = BlockPos.containing(b.getX() + run.x * r, y, b.getZ() + run.z * r);
                if (front.equals(b.blockPosition())) continue;
                if (!BotMoves.solid(l, front)) continue;
                BlockPos past = BlockPos.containing(front.getX() + 0.5 + run.x, y, front.getZ() + 0.5 + run.z);
                if (wall(l, front) == 1 && BotMoves.open(l, front.above()) && BotMoves.open(l, front.above(2)) && BotMoves.open(l, b.blockPosition().above(2))
                        && BotMoves.open(l, past) && BotMoves.open(l, past.above()) && BotMoves.dropAt(l, past.getX() + 0.5, y, past.getZ() + 0.5) < 3) {
                    double sp = Math.max(0.3, Math.min(0.45, 0.2 + reach * 0.15));
                    b.setDeltaMovement(run.x * sp, 0.52, run.z * sp);
                    face(b, run);
                    start(b, b.getRandom().nextBoolean() ? VAULT_R : VAULT_L, 11, run);
                    s.cd = 20;
                    return;
                }
                break;                                                          // (something else in the way: leave it to the path)
            }
        }
        // in a fight: a slide in at the target now and then; a side-step when it swings at close range
        LivingEntity t = b.getTarget();
        if (t != null && t.isAlive()) {
            double d = Math.sqrt(b.distanceToSqr(t));
            if (s.slideCd == 0 && d > 3.5 && d < 7 && v.horizontalDistanceSqr() > 0.12 * 0.12 && b.getRandom().nextInt(30) == 0
                    && safe(l, b, dir, y, 5)) {
                face(b, dir);
                b.setDeltaMovement(dir.x * 0.55, 0, dir.z * 0.55);
                start(b, SLIDE, 12, dir);
                s.slideCd = 120;
                return;
            }
            if (s.dodgeCd == 0 && d < 3.2 && t.swinging && t.swingTime <= 1 && b.getRandom().nextInt(3) == 0) {
                boolean right = b.getRandom().nextBoolean();
                Vec3 side = new Vec3(-dir.z, 0, dir.x).scale(right ? -1 : 1);
                if (safe(l, b, side, y, 4) && BotMoves.open(l, BlockPos.containing(b.getX() + side.x * 1.2, y, b.getZ() + side.z * 1.2))) {
                    b.setDeltaMovement(side.x * 0.45, 0.12, side.z * 0.45);
                    start(b, right ? DODGE_R : DODGE_L, 10, side);
                    s.dodgeCd = 60;
                }
            }
        }
    }

    /** The move under way. */
    private static void move(PartyBotEntity b, State s) {
        b.fallDistance = 0;
        s.ticks--;
        switch (s.code) {
            case CLING -> {
                // hanging on the edge: up against the wall, holding still
                b.getNavigation().stop();
                face(b, s.dir);
                if (b.getY() + 1.6 < s.top) b.setDeltaMovement(s.dir.x * 0.1, Math.max(b.getDeltaMovement().y, 0.3), s.dir.z * 0.1);
                else b.setDeltaMovement(s.dir.x * 0.08, 0.0, s.dir.z * 0.08);
                if (s.ticks <= 0) start(b, CLIMB, 10, s.dir);
            }
            case WALLJUMP_R, WALLJUMP_L -> {
                face(b, s.dir);
                if (s.ticks == 5) b.setDeltaMovement(s.dir.x * 0.08, 0.55, s.dir.z * 0.08);    // (the kick off the wall)
                if (s.ticks <= 0) start(b, CLING, 6, s.dir);
            }
            case CLIMB -> {
                b.getNavigation().stop();
                face(b, s.dir);
                if (b.getY() < s.top + 0.05) b.setDeltaMovement(s.dir.x * 0.04, Math.min(0.5, s.top + 0.12 - b.getY() + 0.08), s.dir.z * 0.04);
                else b.setDeltaMovement(s.dir.x * 0.3, Math.max(0, b.getDeltaMovement().y), s.dir.z * 0.3);   // (over the edge, onto the top)
                if (s.ticks <= 0 || b.getY() >= s.top && b.onGround()) end(b);
            }
            case ROLL -> {
                double k = 0.25 * Math.max(0, s.ticks) / 9.0 + 0.05;
                b.setDeltaMovement(s.dir.x * k, b.getDeltaMovement().y, s.dir.z * k);
                if (s.ticks <= 0) end(b);
            }
            case SLIDE -> {
                double k = 0.55 * Math.max(0, s.ticks) / 12.0;
                b.setDeltaMovement(s.dir.x * k, b.getDeltaMovement().y, s.dir.z * k);
                if (s.ticks <= 0 || b.horizontalCollision) end(b);
            }
            default -> { if (s.ticks <= 0 || (s.code == LEAP && b.onGround() && s.ticks < 16)) end(b); }
        }
    }

    /** Landing from a drop: a roll (true = the fall did no harm; else the damage is already cut by causeFallDamage). */
    static float land(PartyBotEntity b, float dist) {
        if (!on() || b.downed() || b.isPassenger() || dist < 2.5F) return dist;
        Vec3 v = b.getDeltaMovement();
        Vec3 dir = v.horizontalDistanceSqr() > 1e-4 ? new Vec3(v.x, 0, v.z).normalize() : net.minecraft.world.phys.Vec3.directionFromRotation(0, b.getYRot());
        start(b, ROLL, 9, dir);
        return dist <= 8F ? 0F : dist - 8F;
    }

    /** A gap leapt across (BotMoves.tryLeap). */
    static void leapt(PartyBotEntity b) {
        if (!on()) return;
        Vec3 v = b.getDeltaMovement();
        start(b, LEAP, 20, new Vec3(v.x, 0, v.z));
    }

    /** Ground all the way along (every half block, this many blocks out): never dodges or slides off an edge. */
    private static boolean safe(Level l, PartyBotEntity b, Vec3 dir, int y, int blocks) {
        for (double k = 0.5; k <= blocks; k += 0.5)
            if (BotMoves.dropAt(l, b.getX() + dir.x * k, y, b.getZ() + dir.z * k) >= 2) return false;
        return true;
    }

    /** How many solid blocks stand at this spot from its feet up (0-4). */
    private static int wall(Level l, BlockPos at) {
        int h = 0;
        while (h < 4 && BotMoves.solid(l, at.above(h))) h++;
        return h;
    }

    /** Which side the wall-jump pose leans (the wall on its right if the turn from its facing is clockwise). */
    private static boolean wallSide(PartyBotEntity b, Vec3 dir) {
        return b.getRandom().nextBoolean();
    }

    private static void face(PartyBotEntity b, Vec3 dir) {
        if (dir.horizontalDistanceSqr() > 1e-4) BotMoves.face(b, (float) Math.toDegrees(Math.atan2(-dir.x, dir.z)), 45F);
    }

    private record Goal(Vec3 at, boolean owner) {}

    /** Where it's trying to get to: its target, someone it's reviving, else you (when it's following). */
    private static Goal goal(PartyBotEntity b) {
        LivingEntity t = b.getTarget();
        if (t != null && t.isAlive() && t.level() == b.level()) return new Goal(t.position(), false);
        if (b.reviving != null && b.reviving.isAlive()) return new Goal(b.reviving.position(), false);
        var o = b.owner();
        if (o != null && o.level() == b.level() && !o.isSpectator() && b.distanceToSqr(o) > 4 * 4 && (o.onGround() || o.isSprinting())) return new Goal(o.position(), true);
        return null;
    }
}
