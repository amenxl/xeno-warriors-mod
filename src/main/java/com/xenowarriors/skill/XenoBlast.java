package com.xenowarriors.skill;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.karma.Karma;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;

/**
 * Xeno Blast - Mikiko's and Rhalix's last resort (their KA slot, until their real KAs are revealed). A short charge,
 * then a huge blast that pours out ALL the Karma they have left: the more Karma, the harder and wider it hits. After
 * it, their Karma is empty and won't come back for 30 seconds.
 */
public final class XenoBlast {
    public static final int LOCK_TICKS = 30 * 20, MIN_KARMA = 10, RANGE = 40;
    /** The cutscene: 20 seconds; it charges for the first half and lets go at RELEASE. */
    public static final int CUTSCENE = 20 * 20, RELEASE = 200, FREEZE_RANGE = 24;
    private static final String CASTING = "xw_xeno_casting_until";

    // ---- one cast in progress: who's caught in it, how much each fought back, and Ultima pushing it back
    public static final int MASH_NEEDED = 45, COUNTER_UNTIL = RELEASE + 60;
    private static final class Cast {
        final ServerPlayer caster; final long start; final java.util.List<ServerPlayer> watchers = new java.util.ArrayList<>();
        final java.util.Map<Integer, Float> resist = new java.util.HashMap<>();
        final java.util.Set<Integer> frozen = new java.util.HashSet<>();
        int mash; long lastMash; boolean cancelled;
        float dmg; double width; boolean rhalix;
        // a beam clash: one of the frozen fired their own Xeno Blast back
        ServerPlayer rival; float rivalDmg; double rivalWidth; boolean rivalRhalix;
        int casterPresses, rivalPresses; float point = 0.5F;
        final java.util.Map<Integer, long[]> rate = new java.util.HashMap<>();     // per player: {tick, presses that tick}
        /** At most two presses per player per tick (a macro or hacked client can't win instantly). */
        boolean rateOk(ServerPlayer p) {
            long t = p.level().getGameTime();
            long[] r = rate.computeIfAbsent(p.getId(), k -> new long[]{-1, 0});
            if (r[0] != t) { r[0] = t; r[1] = 0; }
            return ++r[1] <= 2;
        }       // where the two blasts meet: 0 = on the caster, 1 = on the rival
        Cast(ServerPlayer c) { caster = c; start = c.level().getGameTime(); }
        int age() { return (int) (caster.level().getGameTime() - start); }
    }
    private static final java.util.Map<Integer, Cast> CASTS = new java.util.HashMap<>();

    private static Cast castOf(ServerPlayer caster) { return CASTS.get(caster.getId()); }
    private static boolean live(ServerPlayer caster) { Cast c = castOf(caster); return c == null || !c.cancelled; }
    /** The blast was met by another Xeno Blast - the normal release doesn't happen, the clash does. */
    private static boolean clashed(ServerPlayer caster) { Cast c = castOf(caster); return c != null && c.rival != null; }
    public static final int CLASH = 120;
    private static float dmgFor(ServerPlayer caster, LivingEntity e, float dmg) {
        Cast c = castOf(caster);
        return c == null ? dmg : dmg * (1F - c.resist.getOrDefault(e.getId(), 0F));
    }

    /**
     * A frozen target fights back with a projectile skill: a shot flies at the caster. It can't stop the blast, but
     * each one (up to three) makes it hit them for less when it lands.
     */
    public static void counter(ServerPlayer target, int casterId, int slot) {
        Cast c = CASTS.get(casterId);
        if (c == null || c.cancelled || !c.frozen.contains(target.getId()) || c.age() > COUNTER_UNTIL) return;
        if (slot == SkillManager.SUPER_SLOT) { clashBack(c, target); return; }
        Skill sk = SkillManager.get(target, slot);
        if (sk == null || sk.clashColor() < 0) {
            target.displayClientMessage(Component.literal("That move can't reach - fight back with a projectile skill!").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        int cost = sk.getKarmaCost(target);
        if (!target.isCreative() && !Karma.has(target, cost)) { Karma.notEnough(target, cost); return; }
        float now = c.resist.getOrDefault(target.getId(), 0F);
        if (now >= 0.6F) { target.displayClientMessage(Component.literal("You've done all you can - brace yourself!").withStyle(ChatFormatting.GRAY), true); return; }
        if (!target.isCreative()) Karma.add(target, -cost);
        c.resist.put(target.getId(), Math.min(0.6F, now + 0.2F));
        ServerLevel l = target.serverLevel();
        int col = sk.clashColor();
        Vector3f v = new Vector3f((col >> 16 & 255) / 255F, (col >> 8 & 255) / 255F, (col & 255) / 255F);
        Vec3 from = target.getEyePosition().subtract(0, 0.3, 0), to = c.caster.getEyePosition().subtract(0, 0.3, 0);
        l.playSound(null, target.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1F, 1.3F);
        for (int k = 0; k <= 10; k++) {
            int kk = k;
            ServerScheduler.later(k, () -> {
                Vec3 q = from.lerp(to, kk / 12.0);         // it never quite gets there: the blast's power eats it
                l.sendParticles(new DustParticleOptions(v, 2.2F), q.x, q.y, q.z, 4, 0.12, 0.12, 0.12, 0);
                if (kk == 10) {
                    l.sendParticles(ParticleTypes.EXPLOSION, q.x, q.y, q.z, 1, 0, 0, 0, 0);
                    l.playSound(null, BlockPos.containing(q), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6F, 1.4F);
                }
            });
        }
        target.displayClientMessage(Component.literal("You fought back! The blast will hit you " + Math.round(c.resist.get(target.getId()) * 100) + "% softer.").withStyle(ChatFormatting.AQUA), true);
    }

    /** Ultima pushing back the wave: mash enough and the Xeno Blast is thrown off - cancelled. */
    public static void mash(ServerPlayer ultima, int casterId) {
        Cast c = CASTS.get(casterId);
        if (c != null && c.rival != null && !c.cancelled) {            // a beam clash: both sides mash to push
            if (c.age() < RELEASE || c.age() >= RELEASE + CLASH) return;
            if ((ultima == c.caster || ultima == c.rival) && !c.rateOk(ultima)) return;
            if (ultima == c.caster) c.casterPresses++;
            else if (ultima == c.rival) c.rivalPresses++;
            return;
        }
        if (c == null || c.cancelled || !c.frozen.contains(ultima.getId()) || ModCapabilities.getType(ultima) != CharacterType.ULTIMA) return;
        if (c.age() > RELEASE + 70 || !c.rateOk(ultima)) return;    // too late (it's already landed), or mashing faster than a person can
        long t = ultima.level().getGameTime();
        c.mash = Math.max(0, c.mash - (int) ((t - c.lastMash) / 10)) + 1;   // it slips back if you slow down
        c.lastMash = t;
        ServerLevel l = ultima.serverLevel();
        Vec3 mid = ultima.getEyePosition().lerp(c.caster.getEyePosition(), 0.25);
        l.sendParticles(new DustParticleOptions(new Vector3f(0.25F, 1F, 0.55F), 1.8F), mid.x, mid.y, mid.z, 6, 0.6, 0.6, 0.6, 0);
        for (ServerPlayer w : c.watchers) com.xenowarriors.network.ModNetwork.sendToPlayer(new com.xenowarriors.network.XenoMashPacket(casterId, c.mash), w);
        if (c.mash >= MASH_NEEDED) cancel(c, ultima);
    }

    private static void cancel(Cast c, ServerPlayer ultima) {
        c.cancelled = true;
        com.xenowarriors.XenoWarriors.LOGGER.info("Xeno Blast pushed back and cancelled by {}", ultima.getGameProfile().getName());
        fx(c.caster, FX_STOP, 0, false, Vec3.ZERO, Vec3.ZERO, 0, 0);
        ServerLevel l = ultima.serverLevel();
        // a green wave rolls back over the caster
        Vec3 a = ultima.position().add(0, 1, 0), b = c.caster.position().add(0, 1, 0);
        for (int k = 0; k <= 12; k++) {
            int kk = k;
            ServerScheduler.later(k, () -> {
                Vec3 q = a.lerp(b, kk / 12.0);
                l.sendParticles(new DustParticleOptions(new Vector3f(0.25F, 1F, 0.55F), 3F), q.x, q.y, q.z, 14, 1.2, 1.2, 1.2, 0);
            });
        }
        l.playSound(null, ultima.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.4F, 1.4F);
        Component msg = Component.literal(ultima.getName().getString() + " pushed back the Xeno Blast!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
        var stop = new com.xenowarriors.network.XenoCutscenePacket(c.caster.getId(), false, 0, 0, 0, 0, 0, 0);
        for (ServerPlayer w : c.watchers) { w.displayClientMessage(msg, true); com.xenowarriors.network.ModNetwork.sendToPlayer(stop, w); }
        for (int id : c.frozen) if (l.getEntity(id) instanceof LivingEntity e) com.xenowarriors.combat.Frozen.release(e);
        com.xenowarriors.combat.Frozen.release(c.caster);
        c.caster.getPersistentData().remove(CASTING);
        c.caster.getPersistentData().remove(SkillEvents.ULT_UNTIL);
        c.caster.push(-c.caster.getLookAngle().x * 1.5, 0.5, -c.caster.getLookAngle().z * 1.5);
        c.caster.hurtMarked = true;
        CASTS.remove(c.caster.getId());
    }

    /** Tests: put another player in a cast as if they'd been frozen by it. */
    public static void testJoin(ServerPlayer caster, ServerPlayer target) {
        Cast c = CASTS.get(caster.getId());
        if (c == null) return;
        c.frozen.add(target.getId());
        if (!c.watchers.contains(target)) c.watchers.add(target);
    }

    /** Tests: a short summary of a cast. */
    public static String testState(ServerPlayer caster) {
        Cast c = CASTS.get(caster.getId());
        if (c == null) return "no cast";
        return "age=" + c.age() + " cancelled=" + c.cancelled + " mash=" + c.mash + " rival=" + (c.rival == null ? "-" : c.rival.getGameProfile().getName())
                + " point=" + c.point + " presses=" + c.casterPresses + "/" + c.rivalPresses + " resist=" + c.resist;
    }

    /** Whether this player is in their Xeno Blast cutscene right now. */
    public static boolean casting(ServerPlayer p) { return p.getPersistentData().getLong(CASTING) > p.level().getGameTime(); }

    private XenoBlast() {}

    /** Whether this character's KA slot is the Xeno Blast. */
    public static boolean has(CharacterType t) { return t == CharacterType.MIKIKO || t == CharacterType.RHALIX; }

    /** The solid DragonMine Z style visuals (client.KiFx), for everyone who can see it. */
    private static void fx(ServerPlayer src, int kind, int other, boolean rh, Vec3 at, Vec3 dir, float size, int life) {
        com.xenowarriors.network.ModNetwork.sendToTrackingEntityAndSelf(new com.xenowarriors.network.XenoFxPacket(src.getId(), kind, other, rh,
                (float) at.x, (float) at.y, (float) at.z, (float) dir.x, (float) dir.y, (float) dir.z, size, life), src);
    }
    private static final int FX_CHARGE = 0, FX_BEAM = 1, FX_ORB = 2, FX_BURST = 3, FX_CLASH = 4, FX_SHOT = 5, FX_STOP = 6;

    private static Vec3 right(ServerPlayer p) {
        return Vec3.directionFromRotation(0, p.getYRot() + 90F);
    }

    /**
     * Mikiko (from the art): energy cupped at his hip, then both hands thrust out in a lunge and a huge beam pours out -
     * white-yellow core, orange body, red edge - spreading wider the further it goes.
     */
    private static void fireBeam(ServerPlayer p, ServerLevel l, float dmg, double width) {
        Vector3f white = new Vector3f(1F, 0.97F, 0.8F), yellow = new Vector3f(1F, 0.85F, 0.25F), orange = new Vector3f(1F, 0.5F, 0.1F), red = new Vector3f(0.95F, 0.12F, 0.05F);
        com.xenowarriors.anim.Poses.play(p, com.xenowarriors.anim.Poses.XENO_MIKIKO, CUTSCENE);
        l.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.4F, 0.6F);
        fx(p, FX_CHARGE, 0, false, Vec3.ZERO, Vec3.ZERO, 0.55F + (float) width * 0.14F, RELEASE);
        for (int i = 0; i < RELEASE; i += 2) {
            int ii = i;
            ServerScheduler.later(i, () -> {
                if (!p.isAlive() || !live(p)) return;
                float f = ii / (float) RELEASE;                  // grows the whole way
                Vec3 look = Vec3.directionFromRotation(0, p.getYRot());
                Vec3 h = p.position().add(0, 0.95, 0).add(right(p).scale(-0.45)).add(look.scale(-0.35));     // cupped at the hip
                for (int k = 0; k < 4; k++) {          // sparks pulled in
                    double a = ii * 0.35 + k * Math.PI / 2, r = 2.4 - (ii % 20) * 0.1;
                    l.sendParticles(new DustParticleOptions(k % 2 == 0 ? yellow : red, 0.9F), h.x + Math.cos(a) * r, h.y + Math.sin(a * 1.7) * r * 0.5, h.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
                if (ii % 20 == 0) l.playSound(null, p.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.5F, 0.5F + f);
                if (ii % 40 == 0) l.playSound(null, p.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1F, 0.6F + f * 0.8F);
            });
        }
        ServerScheduler.later(RELEASE, () -> {
            if (!p.isAlive() || !live(p) || clashed(p)) return;
            l.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.6F, 0.85F);
            l.playSound(null, p.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.4F, 0.5F);
            fx(p, FX_BEAM, 0, false, Vec3.ZERO, Vec3.ZERO, (float) width, 70);
            Set<Integer> hit = new HashSet<>();
            landOnFrozen(p, l, hit, e -> { e.hurt(p.damageSources().indirectMagic(p, p), dmgFor(p, e, dmg)); e.setSecondsOnFire(5);
                e.push(p.getLookAngle().x * 1.8, 0.45, p.getLookAngle().z * 1.8); e.hurtMarked = true;
                l.sendParticles(ParticleTypes.EXPLOSION, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 1, 0, 0, 0, 0); });
            for (int k = 0; k < 70; k++) ServerScheduler.later(k, () -> {       // a long, huge beam
                if (!p.isAlive() || !live(p)) return;
                Vec3 s = p.getEyePosition().subtract(0, 0.4, 0).add(p.getLookAngle().scale(0.9)), d = p.getLookAngle();
                Vec3 up = Math.abs(d.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
                Vec3 u = d.cross(up).normalize(), v = d.cross(u).normalize();
                for (int j = 1; j <= RANGE; j++) {
                    double f = j / (double) RANGE, r = 0.35 + width * Math.pow(f, 0.6);       // widening cone
                    Vec3 q = s.add(d.scale(j));
                    // (the beam itself is drawn solid on the client - KiFx; just flames and embers flying off it here)
                    if (j % 4 == 0 && l.random.nextInt(3) == 0) l.sendParticles(ParticleTypes.FLAME, q.x, q.y, q.z, 1, r * 0.5, r * 0.5, r * 0.5, 0.04);
                    if (j % 7 == 0 && l.random.nextInt(4) == 0) l.sendParticles(ParticleTypes.LAVA, q.x, q.y, q.z, 1, r * 0.4, r * 0.4, r * 0.4, 0);
                }
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(s, s.add(d.scale(RANGE))).inflate(width + 1), e -> SkillTargeting.canTarget(p, e))) {
                    Vec3 rel = e.position().add(0, e.getBbHeight() / 2, 0).subtract(s);
                    double along = rel.dot(d);
                    double r = 0.35 + width * Math.pow(Math.max(0, along) / RANGE, 0.6);
                    if (along < 0 || along > RANGE || rel.subtract(d.scale(along)).length() > r + e.getBbWidth()) continue;
                    if (hit.add(e.getId())) {
                        e.hurt(p.damageSources().indirectMagic(p, p), dmgFor(p, e, dmg));
                        e.setSecondsOnFire(5);
                        e.push(d.x * 1.8, 0.45, d.z * 1.8);
                        e.hurtMarked = true;
                        l.sendParticles(ParticleTypes.EXPLOSION, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 1, 0, 0, 0, 0);
                    }
                }
            });
        });
    }

    /**
     * Rhalix (from the art): side-on, pointing a finger - a black orb of dark gravity crackling with purple lightning
     * forms on his fingertip, shoots out trailing purple light rays, and bursts on the first thing it hits.
     */
    private static void darkOrb(ServerPlayer p, ServerLevel l, float dmg, double width) {
        Vector3f black = new Vector3f(0.07F, 0.0F, 0.1F), purple = new Vector3f(0.78F, 0.35F, 1F), pink = new Vector3f(1F, 0.55F, 1F);
        int charge = RELEASE;
        com.xenowarriors.anim.Poses.play(p, com.xenowarriors.anim.Poses.XENO_RHALIX, CUTSCENE);
        l.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.4F, 0.4F);
        java.util.function.Supplier<Vec3> fingertip = () -> p.getEyePosition().subtract(0, 0.3, 0).add(p.getLookAngle().scale(1.3)).add(right(p).scale(0.25));
        fx(p, FX_CHARGE, 0, true, Vec3.ZERO, Vec3.ZERO, 0.9F + (float) width * 0.18F, charge);
        for (int i = 0; i < charge; i += 2) {
            int ii = i;
            ServerScheduler.later(i, () -> {
                if (!p.isAlive() || !live(p)) return;
                float f = ii / (float) charge;
                Vec3 h = fingertip.get();
                float sz = 0.8F + f * 3.2F;                     // the orb swells the whole time (drawn solid on the client - KiFx)
                l.sendParticles(ParticleTypes.ELECTRIC_SPARK, h.x, h.y, h.z, 2, sz * 0.12, sz * 0.12, sz * 0.12, 0.15);
                if (ii % 20 == 0) l.playSound(null, p.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.6F, 0.5F + f);
                if (ii % 30 == 0) l.playSound(null, p.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1F, 0.7F + f * 0.5F);
            });
        }
        ServerScheduler.later(charge, () -> {
            if (!p.isAlive() || !live(p) || clashed(p)) return;
            l.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.5F, 0.55F);
            Vec3 d = p.getLookAngle();
            Vec3[] pos = {fingertip.get()};
            fx(p, FX_ORB, 0, true, pos[0], d, 1.3F + (float) width * 0.18F, 60);
            boolean[] done = {false};
            double hitR = 0.9 + width * 0.3;
            for (int k = 0; k < 60; k++) {
                int kk = k;
                ServerScheduler.later(k, () -> {
                    if (done[0] || !live(p)) return;
                    Vec3 from = pos[0], to = from.add(d.scale(0.9));          // slow and heavy, for the camera
                    // light rays streaking out behind it, and a trail
                    l.sendParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 3, 0.4, 0.4, 0.4, 0.15);
                    var clip = l.clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
                    Vec3 end = clip.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK ? clip.getLocation() : to;
                    boolean struck = clip.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK || kk == 59
                            || !l.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(hitR), e -> SkillTargeting.canTarget(p, e)).isEmpty();
                    pos[0] = end;
                    if (struck) { done[0] = true; burst(p, l, end, dmg, 2.5 + width, black, purple, pink); }
                });
            }
        });
    }

    /** The dark orb: black core, purple shell, crackling lightning and rays of purple light. */
    private static void orb(ServerLevel l, Vec3 c, float size, Vector3f black, Vector3f purple, int seed) {
        l.sendParticles(new DustParticleOptions(black, Math.min(4F, size)), c.x, c.y, c.z, 5, size * 0.06, size * 0.06, size * 0.06, 0);
        l.sendParticles(new DustParticleOptions(purple, 1.2F), c.x, c.y, c.z, 4, size * 0.13, size * 0.13, size * 0.13, 0);
        l.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 3, size * 0.12, size * 0.12, size * 0.12, 0.15);
        java.util.Random r = new java.util.Random(seed * 31L + (long) (c.x * 7));
        for (int ray = 0; ray < 2; ray++) {
            Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.6, r.nextGaussian()).normalize();
            double len = 1.5 + r.nextDouble() * size * 0.9;
            for (double t = 0.4; t < len; t += 0.35) {
                Vec3 q = c.add(dir.scale(t));
                l.sendParticles(new DustParticleOptions(purple, 0.8F), q.x, q.y, q.z, 1, 0, 0, 0, 0);
            }
        }
    }

    /** The orb bursting: a sphere of dark energy and long purple rays; hurts everything nearby. */
    private static void burst(ServerPlayer p, ServerLevel l, Vec3 at, float dmg, double radius, Vector3f black, Vector3f purple, Vector3f pink) {
        l.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.6F, 0.6F);
        l.playSound(null, BlockPos.containing(at), SoundEvents.WITHER_BREAK_BLOCK, SoundSource.PLAYERS, 0.8F, 0.7F);
        l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        fx(p, FX_BURST, 0, true, at, Vec3.ZERO, (float) radius * 1.2F, 16);
        java.util.Random r = new java.util.Random();
        for (int ray = 0; ray < 6; ray++) {
            Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.7, r.nextGaussian()).normalize();
            for (double t = 0.5; t < radius * 2.4; t += 0.4) {
                Vec3 q = at.add(dir.scale(t));
                l.sendParticles(new DustParticleOptions(ray % 3 == 0 ? pink : purple, 1.3F), q.x, q.y, q.z, 1, 0, 0, 0, 0);
            }
        }
        Set<Integer> hit = new HashSet<>();
        landOnFrozen(p, l, hit, e -> { e.hurt(p.damageSources().indirectMagic(p, p), dmgFor(p, e, dmg * 0.8F));
            Vec3 push = e.position().subtract(at).normalize(); e.push(push.x * 1.4, 0.55, push.z * 1.4); e.hurtMarked = true; });
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius), e -> SkillTargeting.canTarget(p, e))) {
            if (hit.contains(e.getId())) continue;
            double dist = e.position().add(0, e.getBbHeight() / 2, 0).distanceTo(at);
            if (dist > radius + e.getBbWidth()) continue;
            float f = (float) Math.max(0.45, 1 - dist / (radius * 1.6));
            e.hurt(p.damageSources().indirectMagic(p, p), dmgFor(p, e, dmg * f));
            Vec3 push = e.position().subtract(at).normalize();
            e.push(push.x * 1.4, 0.55, push.z * 1.4);
            e.hurtMarked = true;
        }
    }

    /** The blast always lands on whoever the cutscene froze - flying, fallen, or ducked behind something - never a miss. */
    private static void landOnFrozen(ServerPlayer p, ServerLevel l, Set<Integer> hit, java.util.function.Consumer<LivingEntity> strike) {
        Cast c = castOf(p);
        if (c == null) return;
        for (int id : c.frozen) {
            if (l.getEntity(id) instanceof LivingEntity e && e.isAlive() && e != p && e.distanceTo(p) <= RANGE + 8 && SkillTargeting.canTarget(p, e) && hit.add(id)) strike.accept(e);
        }
    }

    private static float dmgOf(float power) { return 12F + power * 0.5F; }
    private static double widthOf(float power) { return 1.2 + Math.min(2.0, power / 80.0); }

    /**
     * Caught in a Xeno Blast as Mikiko or Rhalix: fire your own Xeno Blast back (your KA key) before it's let go, and
     * the two blasts meet in the middle and CLASH. It costs everything, just like casting one.
     */
    private static void clashBack(Cast c, ServerPlayer r) {
        CharacterType type = ModCapabilities.getType(r);
        if (!has(type)) {
            r.displayClientMessage(Component.literal("Only another Xeno Blast can meet a Xeno Blast head on.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (c.rival != null) {
            r.displayClientMessage(Component.literal(c.rival == r ? "You're already clashing!" : "Someone else already met it head on!").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (c.age() >= RELEASE) {
            r.displayClientMessage(Component.literal("Too late - it's already been let go!").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (Karma.regenLocked(r)) {
            r.displayClientMessage(Component.literal("Your Karma is still drained from the last Xeno Blast.").withStyle(ChatFormatting.DARK_RED), true);
            return;
        }
        boolean[] cool = {false};
        r.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> cool[0] = d.cooldownEnd[SkillManager.SUPER_SLOT] > r.level().getGameTime());
        if (cool[0] && !r.isCreative()) {
            r.displayClientMessage(Component.literal("Your Xeno Blast isn't ready yet.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        float karma = Karma.get(r);
        if (karma < MIN_KARMA && !r.isCreative()) {
            r.displayClientMessage(Component.literal("Not enough Karma left for a Xeno Blast (needs " + MIN_KARMA + ").").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        float power = r.isCreative() ? Math.max(karma, 100F) : karma;
        if (!r.isCreative()) Karma.add(r, -karma);
        int left = CUTSCENE - c.age();
        Karma.lockRegen(r, left + LOCK_TICKS);
        int cd = com.xenowarriors.ka.Kas.PLAYER_COOLDOWN;
        r.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.cooldownEnd[SkillManager.SUPER_SLOT] = r.level().getGameTime() + cd);
        com.xenowarriors.network.ModNetwork.sendToPlayer(new com.xenowarriors.network.SkillCooldownPacket(SkillManager.SUPER_SLOT, cd, cd), r);
        c.rival = r; c.rivalDmg = dmgOf(power); c.rivalWidth = widthOf(power); c.rivalRhalix = type == CharacterType.RHALIX;
        c.resist.remove(r.getId());
        // turn to face them, take the stance and start charging
        Vec3 to = c.caster.position().subtract(r.position());
        float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
        r.connection.teleport(r.getX(), r.getY(), r.getZ(), yaw, 0F);
        r.setYHeadRot(yaw); r.yBodyRot = yaw;
        com.xenowarriors.anim.Poses.play(r, c.rivalRhalix ? com.xenowarriors.anim.Poses.XENO_RHALIX : com.xenowarriors.anim.Poses.XENO_MIKIKO, left);
        ServerLevel l = r.serverLevel();
        Vector3f col = c.rivalRhalix ? new Vector3f(0.72F, 0.3F, 1F) : new Vector3f(1F, 0.55F, 0.12F);
        fx(r, FX_CHARGE, 0, c.rivalRhalix, Vec3.ZERO, Vec3.ZERO, (c.rivalRhalix ? 0.9F : 0.55F) + (float) c.rivalWidth * 0.15F, RELEASE - c.age());
        l.playSound(null, r.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.4F, 0.5F);
        Component msg = Component.literal(r.getName().getString() + " fired a Xeno Blast back - BEAM CLASH!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        if (!c.watchers.contains(r)) c.watchers.add(r);
        clashPacket(c, 0);
        for (ServerPlayer w : c.watchers) w.displayClientMessage(msg, true);
        com.xenowarriors.network.PopupPacket.toParty(r, com.xenowarriors.network.PopupPacket.REQUEST, "Beam Clash!",
                r.getName().getString() + " met " + c.caster.getName().getString() + "'s Xeno Blast with their own!", "rage");
    }

    private static Vec3 handOf(ServerPlayer p, boolean rhalix) {
        Vec3 look = Vec3.directionFromRotation(0, p.getYRot());
        return p.position().add(0, rhalix ? 1.35 : 1.15, 0).add(look.scale(1.1));
    }

    private static void clashPacket(Cast c, int state) {
        var pkt = new com.xenowarriors.network.XenoClashPacket(c.caster.getId(), c.rival.getId(), c.rivalRhalix, c.point, state);
        for (ServerPlayer w : c.watchers) com.xenowarriors.network.ModNetwork.sendToPlayer(pkt, w);
    }

    /** The clash itself: two blasts meeting, both sides mashing to push the meeting point onto the other. */
    private static void clashRun(Cast c) {
        ServerPlayer a = c.caster, b = c.rival;
        ServerLevel l = a.serverLevel();
        Vector3f aCore = c.rhalix ? new Vector3f(0.75F, 0.35F, 1F) : new Vector3f(1F, 0.85F, 0.3F);
        Vector3f aRim = c.rhalix ? new Vector3f(0.1F, 0F, 0.15F) : new Vector3f(1F, 0.45F, 0.08F);
        Vector3f bCore = c.rivalRhalix ? new Vector3f(0.75F, 0.35F, 1F) : new Vector3f(1F, 0.85F, 0.3F);
        Vector3f bRim = c.rivalRhalix ? new Vector3f(0.1F, 0F, 0.15F) : new Vector3f(1F, 0.45F, 0.08F);
        Vector3f white = new Vector3f(1F, 1F, 0.95F);
        l.playSound(null, a.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.6F, 0.8F);
        l.playSound(null, b.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.6F, 0.7F);
        // the stronger blast (more Karma behind it) slowly gains ground on its own; mashing decides it
        float bias = (c.dmg - c.rivalDmg) / (c.dmg + c.rivalDmg) * 0.003F;
        Vec3 rr = new Vec3(c.rivalRhalix ? 1 : 0, 0, 0);
        fx(a, FX_CLASH, b.getId(), c.rhalix, Vec3.ZERO, rr, c.point, CLASH + 40);
        int[] seen = {0, 0};
        boolean[] over = {false};
        for (int k = 0; k < CLASH; k++) {
            int kk = k;
            ServerScheduler.later(k, () -> {
                if (over[0] || c.cancelled) return;
                if (!a.isAlive() || !b.isAlive()) { over[0] = true; clashEnd(c, a.isAlive() ? 1F : 0F); return; }
                int da = c.casterPresses - seen[0], db = c.rivalPresses - seen[1];
                seen[0] = c.casterPresses; seen[1] = c.rivalPresses;
                c.point = Math.max(0F, Math.min(1F, c.point + (da - db) * 0.011F + bias));
                Vec3 ha = handOf(a, c.rhalix), hb = handOf(b, c.rivalRhalix);
                Vec3 meet = ha.lerp(hb, c.point);
                double len = ha.distanceTo(hb);
                // (the two blasts and the ball where they meet are drawn solid on the client - KiFx; sparks and lightning here)
                l.sendParticles(ParticleTypes.ELECTRIC_SPARK, meet.x, meet.y, meet.z, 6, 0.9, 0.9, 0.9, 0.4);
                if (kk % 3 == 0) l.sendParticles(ParticleTypes.FLASH, meet.x, meet.y, meet.z, 1, 0, 0, 0, 0);
                if (kk % 4 == 0) l.sendParticles(ParticleTypes.EXPLOSION, meet.x, meet.y, meet.z, 1, 0.6, 0.6, 0.6, 0);
                if (kk % 10 == 0) l.playSound(null, BlockPos.containing(meet), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7F, 0.6F + c.point * 0.6F);
                if (kk % 20 == 0) l.playSound(null, BlockPos.containing(meet), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.2F, 1.4F);
                if (kk % 2 == 0) { clashPacket(c, 1); fx(a, FX_CLASH, b.getId(), c.rhalix, Vec3.ZERO, rr, c.point, CLASH + 40); }
                if (c.point >= 0.92F || c.point <= 0.08F || kk == CLASH - 1) { over[0] = true; clashEnd(c, c.point); }
            });
        }
    }

    /** Whoever pushed the meeting point onto the other wins: their blast swallows both and hits the loser. */
    private static void clashEnd(Cast c, float point) {
        ServerPlayer a = c.caster, b = c.rival;
        ServerLevel l = a.serverLevel();
        // a couple of seconds to see who won, then the cutscene ends for everyone and the frozen are let go
        ServerScheduler.later(45, () -> {
            var stop = new com.xenowarriors.network.XenoCutscenePacket(a.getId(), false, 0, 0, 0, 0, 0, 0);
            for (ServerPlayer w : c.watchers) com.xenowarriors.network.ModNetwork.sendToPlayer(stop, w);
            for (int id : c.frozen) if (l.getEntity(id) instanceof LivingEntity e) com.xenowarriors.combat.Frozen.release(e);
            if (CASTS.get(a.getId()) == c) CASTS.remove(a.getId());
        });
        float margin = Math.abs(point - 0.5F) * 2F;
        com.xenowarriors.XenoWarriors.LOGGER.info("Xeno Blast clash over: {} vs {}, meeting point {} -> {}", a.getGameProfile().getName(), b.getGameProfile().getName(), point,
                margin < 0.25F ? "even, both blow up" : (point > 0.5F ? a : b).getGameProfile().getName() + " wins");
        // nobody is safe now - the cutscene protection is over for both
        a.getPersistentData().remove(SkillEvents.ULT_UNTIL);
        a.getPersistentData().remove(CASTING);
        com.xenowarriors.combat.Frozen.release(a);
        com.xenowarriors.combat.Frozen.release(b);
        Vec3 ha = handOf(a, c.rhalix), hb = handOf(b, c.rivalRhalix);
        fx(a, FX_CLASH, b.getId(), c.rhalix, Vec3.ZERO, Vec3.ZERO, point, 0);       // the clash beams go
        if (margin < 0.25F) {           // dead even: both blasts blow up between them
            Vec3 mid = ha.lerp(hb, point);
            fx(a, FX_BURST, 0, c.rhalix, mid, Vec3.ZERO, 5F, 20);
            fx(b, FX_BURST, 0, c.rivalRhalix, mid, Vec3.ZERO, 4F, 20);
            l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, mid.x, mid.y, mid.z, 3, 1, 1, 1, 0);
            l.playSound(null, BlockPos.containing(mid), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2F, 0.5F);
            for (ServerPlayer p : new ServerPlayer[]{a, b}) {
                ServerPlayer other = p == a ? b : a;
                float od = p == a ? c.rivalDmg : c.dmg;
                p.hurt(other.damageSources().indirectMagic(other, other), od * 0.35F);
                Vec3 push = p.position().subtract(mid).normalize();
                p.push(push.x * 2, 0.6, push.z * 2); p.hurtMarked = true;
            }
            Component msg = Component.literal("The Xeno Blasts cancelled each other out!").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
            for (ServerPlayer w : c.watchers) w.displayClientMessage(msg, true);
            clashPacket(c, 2);
            return;
        }
        boolean casterWins = point > 0.5F;
        ServerPlayer win = casterWins ? a : b, lose = casterWins ? b : a;
        float wd = casterWins ? c.dmg : c.rivalDmg;
        double ww = casterWins ? c.width : c.rivalWidth;
        boolean decisive = point >= 0.92F || point <= 0.08F;
        float dmg = decisive ? wd * 1.25F : wd * (0.6F + 0.4F * margin);   // pushed all the way through: both blasts' power
        Vec3 from = casterWins ? ha.lerp(hb, point) : hb.lerp(ha, 1F - point), at = lose.position().add(0, 1, 0);
        boolean wr = casterWins ? c.rhalix : c.rivalRhalix;
        fx(a, FX_SHOT, lose.getId(), wr, from, Vec3.ZERO, 0.9F + (float) ww * 0.2F, 8);
        for (int k = 0; k <= 8; k++) {
            int kk = k;
            ServerScheduler.later(k, () -> {
                if (kk == 8) {
                    fx(a, FX_BURST, 0, wr, at, Vec3.ZERO, 3F + (float) ww, 18);
                    l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                    l.playSound(null, lose.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2F, 0.6F);
                    for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(2 + ww), e -> e == lose || (e != win && SkillTargeting.canTarget(win, e)))) {
                        e.hurt(win.damageSources().indirectMagic(win, win), e == lose ? dmg : dmg * 0.5F);
                        e.setSecondsOnFire(5);
                        Vec3 push = e.position().subtract(win.position()).normalize();
                        e.push(push.x * 2, 0.6, push.z * 2); e.hurtMarked = true;
                    }
                }
            });
        }
        Component msg = Component.literal(win.getName().getString() + " won the clash!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        for (ServerPlayer w : c.watchers) w.displayClientMessage(msg, true);
        c.point = point;
        clashPacket(c, 2);
    }

    public static boolean use(ServerPlayer p) {
        CharacterType type = ModCapabilities.getType(p);
        if (Karma.regenLocked(p)) {
            p.displayClientMessage(Component.literal("Your Karma is still drained from the last Xeno Blast.").withStyle(ChatFormatting.DARK_RED), true);
            return false;
        }
        float karma = Karma.get(p);
        if (karma < MIN_KARMA && !p.isCreative()) {
            p.displayClientMessage(Component.literal("Not enough Karma left for a Xeno Blast (needs " + MIN_KARMA + ").").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        float power = p.isCreative() ? Math.max(karma, 100F) : karma;
        float dmg = dmgOf(power);
        double width = widthOf(power);
        boolean rhalix = type == CharacterType.RHALIX;
        Vector3f core = rhalix ? new Vector3f(0.72F, 0.3F, 1F) : new Vector3f(1F, 0.5F, 0.12F);
        Vector3f rim = rhalix ? new Vector3f(0.35F, 0.05F, 0.6F) : new Vector3f(1F, 0.85F, 0.3F);
        ServerLevel l = p.serverLevel();

        // it takes everything they have
        if (!p.isCreative()) Karma.add(p, -karma);
        Karma.lockRegen(p, CUTSCENE + LOCK_TICKS);          // 30 seconds of no regen after the cutscene ends
        p.displayClientMessage(Component.literal("XENO BLAST!").withStyle(rhalix ? ChatFormatting.DARK_PURPLE : ChatFormatting.GOLD, ChatFormatting.BOLD), true);
        Component line = Component.literal(p.getName().getString() + ": ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal("XENO BLAST!").withStyle(rhalix ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GOLD, ChatFormatting.BOLD));
        for (ServerPlayer o : l.players()) if (o != p && o.distanceToSqr(p) < 48 * 48) o.displayClientMessage(line, true);

        // the cutscene: everything around can't move, the caster is locked in and can't be hurt while it plays
        long until = l.getGameTime() + CUTSCENE;
        p.getPersistentData().putLong(CASTING, until);
        p.getPersistentData().putLong(SkillEvents.ULT_UNTIL, until);
        com.xenowarriors.combat.Frozen.freeze(p, CUTSCENE);
        Cast cast = new Cast(p);
        cast.dmg = dmg; cast.width = width; cast.rhalix = rhalix;
        CASTS.put(p.getId(), cast);
        ServerScheduler.later(RELEASE, () -> { if (CASTS.get(p.getId()) == cast && cast.rival != null && !cast.cancelled) clashRun(cast); });
        java.util.List<ServerPlayer> watchers = cast.watchers;
        watchers.add(p);
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(FREEZE_RANGE), e -> e != p && SkillTargeting.canTarget(p, e))) {
            com.xenowarriors.combat.Frozen.freeze(e, CUTSCENE);
            cast.frozen.add(e.getId());
            if (e instanceof ServerPlayer sp) watchers.add(sp);
        }
        ServerScheduler.later(CUTSCENE + 5, () -> { if (CASTS.get(p.getId()) == cast) CASTS.remove(p.getId()); });
        var pkt = new com.xenowarriors.network.XenoCutscenePacket(p.getId(), rhalix, CUTSCENE, RELEASE, p.getX(), p.getY(), p.getZ(), p.getYRot());
        for (ServerPlayer w : watchers) com.xenowarriors.network.ModNetwork.sendToPlayer(pkt, w);
        if (rhalix) darkOrb(p, l, dmg, width);
        else fireBeam(p, l, dmg, width);
        return true;
    }
}
