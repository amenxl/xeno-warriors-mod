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

    /** Whether this player is in their Xeno Blast cutscene right now. */
    public static boolean casting(ServerPlayer p) { return p.getPersistentData().getLong(CASTING) > p.level().getGameTime(); }

    private XenoBlast() {}

    /** Whether this character's KA slot is the Xeno Blast. */
    public static boolean has(CharacterType t) { return t == CharacterType.MIKIKO || t == CharacterType.RHALIX; }

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
        for (int i = 0; i < RELEASE; i += 2) {
            int ii = i;
            ServerScheduler.later(i, () -> {
                if (!p.isAlive()) return;
                float f = ii / (float) RELEASE;                  // grows the whole way
                Vec3 look = Vec3.directionFromRotation(0, p.getYRot());
                Vec3 h = p.position().add(0, 0.95, 0).add(right(p).scale(-0.45)).add(look.scale(-0.35));     // cupped at the hip
                float sz = 0.6F + f * 2.4F;
                l.sendParticles(new DustParticleOptions(white, Math.min(4F, sz + 0.6F)), h.x, h.y, h.z, 2, 0.05 + f * 0.1, 0.05 + f * 0.1, 0.05 + f * 0.1, 0);
                l.sendParticles(new DustParticleOptions(orange, Math.min(4F, sz + 1F)), h.x, h.y, h.z, 3, 0.12 + f * 0.3, 0.12 + f * 0.2, 0.12 + f * 0.3, 0);
                for (int k = 0; k < 4; k++) {          // sparks pulled in
                    double a = ii * 0.35 + k * Math.PI / 2, r = 2.4 - (ii % 20) * 0.1;
                    l.sendParticles(new DustParticleOptions(k % 2 == 0 ? yellow : red, 0.9F), h.x + Math.cos(a) * r, h.y + Math.sin(a * 1.7) * r * 0.5, h.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
                if (ii % 20 == 0) l.playSound(null, p.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.5F, 0.5F + f);
                if (ii % 40 == 0) l.playSound(null, p.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1F, 0.6F + f * 0.8F);
            });
        }
        ServerScheduler.later(RELEASE, () -> {
            if (!p.isAlive()) return;
            l.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.6F, 0.85F);
            l.playSound(null, p.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.4F, 0.5F);
            Set<Integer> hit = new HashSet<>();
            for (int k = 0; k < 70; k++) ServerScheduler.later(k, () -> {       // a long, huge beam
                if (!p.isAlive()) return;
                Vec3 s = p.getEyePosition().subtract(0, 0.4, 0).add(p.getLookAngle().scale(0.9)), d = p.getLookAngle();
                Vec3 up = Math.abs(d.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
                Vec3 u = d.cross(up).normalize(), v = d.cross(u).normalize();
                for (int j = 1; j <= RANGE; j++) {
                    double f = j / (double) RANGE, r = 0.35 + width * Math.pow(f, 0.6);       // widening cone
                    Vec3 q = s.add(d.scale(j));
                    l.sendParticles(new DustParticleOptions(j < 4 ? white : yellow, 2.2F), q.x, q.y, q.z, 1, r * 0.15, r * 0.15, r * 0.15, 0);
                    l.sendParticles(new DustParticleOptions(orange, 2.8F), q.x, q.y, q.z, 2, r * 0.4, r * 0.4, r * 0.4, 0);
                    double a = (j * 0.9 + l.getGameTime() * 0.7) % (Math.PI * 2);
                    for (int e = 0; e < 3; e++) {        // red edge
                        double aa = a + e * Math.PI * 2 / 3;
                        Vec3 rim = q.add(u.scale(Math.cos(aa) * r)).add(v.scale(Math.sin(aa) * r));
                        l.sendParticles(new DustParticleOptions(red, 1.8F), rim.x, rim.y, rim.z, 1, 0, 0, 0, 0);
                    }
                    if (j % 6 == 0) l.sendParticles(ParticleTypes.FLAME, q.x, q.y, q.z, 1, r * 0.3, r * 0.3, r * 0.3, 0.02);
                }
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(s, s.add(d.scale(RANGE))).inflate(width + 1), e -> SkillTargeting.canTarget(p, e))) {
                    Vec3 rel = e.position().add(0, e.getBbHeight() / 2, 0).subtract(s);
                    double along = rel.dot(d);
                    double r = 0.35 + width * Math.pow(Math.max(0, along) / RANGE, 0.6);
                    if (along < 0 || along > RANGE || rel.subtract(d.scale(along)).length() > r + e.getBbWidth()) continue;
                    if (hit.add(e.getId())) {
                        e.hurt(p.damageSources().indirectMagic(p, p), dmg);
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
        for (int i = 0; i < charge; i += 2) {
            int ii = i;
            ServerScheduler.later(i, () -> {
                if (!p.isAlive()) return;
                float f = ii / (float) charge;
                Vec3 h = fingertip.get();
                float sz = 0.8F + f * 3.2F;                     // the orb swells the whole time
                orb(l, h, sz, black, purple, ii);
                if (ii % 20 == 0) l.playSound(null, p.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.6F, 0.5F + f);
                if (ii % 30 == 0) l.playSound(null, p.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1F, 0.7F + f * 0.5F);
            });
        }
        ServerScheduler.later(charge, () -> {
            if (!p.isAlive()) return;
            l.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.5F, 0.55F);
            Vec3 d = p.getLookAngle();
            Vec3[] pos = {fingertip.get()};
            boolean[] done = {false};
            double hitR = 0.9 + width * 0.3;
            for (int k = 0; k < 60; k++) {
                int kk = k;
                ServerScheduler.later(k, () -> {
                    if (done[0]) return;
                    Vec3 from = pos[0], to = from.add(d.scale(0.9));          // slow and heavy, for the camera
                    // light rays streaking out behind it, and a trail
                    for (int t = 0; t < 4; t++) { Vec3 q = from.add(d.scale(t * 0.45)); l.sendParticles(new DustParticleOptions(purple, 1.4F), q.x, q.y, q.z, 1, 0.08, 0.08, 0.08, 0); }
                    orb(l, to, 3.2F, black, purple, kk);
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
        l.sendParticles(new DustParticleOptions(black, 4F), at.x, at.y, at.z, 40, radius * 0.35, radius * 0.35, radius * 0.35, 0);
        l.sendParticles(new DustParticleOptions(purple, 2.5F), at.x, at.y, at.z, 40, radius * 0.5, radius * 0.5, radius * 0.5, 0);
        java.util.Random r = new java.util.Random();
        for (int ray = 0; ray < 10; ray++) {
            Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.7, r.nextGaussian()).normalize();
            for (double t = 0.5; t < radius * 2.4; t += 0.4) {
                Vec3 q = at.add(dir.scale(t));
                l.sendParticles(new DustParticleOptions(ray % 3 == 0 ? pink : purple, 1.3F), q.x, q.y, q.z, 1, 0, 0, 0, 0);
            }
        }
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius), e -> SkillTargeting.canTarget(p, e))) {
            double dist = e.position().add(0, e.getBbHeight() / 2, 0).distanceTo(at);
            if (dist > radius + e.getBbWidth()) continue;
            float f = (float) Math.max(0.45, 1 - dist / (radius * 1.6));
            e.hurt(p.damageSources().indirectMagic(p, p), dmg * f);
            Vec3 push = e.position().subtract(at).normalize();
            e.push(push.x * 1.4, 0.55, push.z * 1.4);
            e.hurtMarked = true;
        }
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
        float dmg = 12F + power * 0.5F;
        double width = 1.2 + Math.min(2.0, power / 80.0);
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
        java.util.List<ServerPlayer> watchers = new java.util.ArrayList<>(java.util.List.of(p));
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(FREEZE_RANGE), e -> e != p && SkillTargeting.canTarget(p, e))) {
            com.xenowarriors.combat.Frozen.freeze(e, CUTSCENE);
            if (e instanceof ServerPlayer sp) watchers.add(sp);
        }
        var pkt = new com.xenowarriors.network.XenoCutscenePacket(p.getId(), rhalix, CUTSCENE, RELEASE, p.getX(), p.getY(), p.getZ(), p.getYRot());
        for (ServerPlayer w : watchers) com.xenowarriors.network.ModNetwork.sendToPlayer(pkt, w);
        if (rhalix) darkOrb(p, l, dmg, width);
        else fireBeam(p, l, dmg, width);
        return true;
    }
}
