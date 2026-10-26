package com.xenowarriors.skill.ultima;

import com.xenowarriors.skill.ServerScheduler;
import com.xenowarriors.skill.SkillTargeting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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

/** Ultima's green energy: travelling blasts and the full-output beam. */
final class UltimaBlasts {
    static final Vector3f GREEN = new Vector3f(.25F, 1F, .55F);

    private UltimaBlasts() {}

    /** A blast that flies forward ~1.6 blocks a tick and bursts on the first thing it hits (or passes through, if piercing). */
    static void blast(ServerPlayer p, Vec3 dir, float dmg, float size, boolean pierce, int delay) {
        ServerScheduler.later(delay, () -> {
            if (!p.isAlive()) return;
            ServerLevel l = p.serverLevel();
            Vec3[] pos = {p.getEyePosition().add(dir.scale(0.8)).subtract(0, 0.2, 0)};
            Set<Integer> hit = new HashSet<>();
            l.playSound(null, p.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 1.8F);
            for (int t = 0; t < 18; t++) {
                final int tick = t;
                ServerScheduler.later(t, () -> {
                    if (pos[0] == null) return;
                    Vec3 next = pos[0].add(dir.scale(1.6));
                    l.sendParticles(new DustParticleOptions(GREEN, size * 1.4F), next.x, next.y, next.z, 4, size * 0.15, size * 0.15, size * 0.15, 0);
                    l.sendParticles(ParticleTypes.ELECTRIC_SPARK, next.x, next.y, next.z, 1, 0.05, 0.05, 0.05, 0);
                    if (!l.getBlockState(net.minecraft.core.BlockPos.containing(next)).isAir()) { burst(l, next, size); pos[0] = null; return; }
                    for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(pos[0], next).inflate(0.6 * size), e -> SkillTargeting.canTarget(p, e))) {
                        if (!hit.add(e.getId())) continue;
                        e.hurt(p.damageSources().indirectMagic(p, p), dmg);
                        e.push(dir.x * 0.4, 0.15, dir.z * 0.4); e.hurtMarked = true;
                        if (!pierce) { burst(l, next, size); pos[0] = null; return; }
                    }
                    pos[0] = next;
                });
            }
        });
    }

    static void burst(ServerLevel l, Vec3 at, float size) {
        l.sendParticles(new DustParticleOptions(GREEN, 2F), at.x, at.y, at.z, (int) (14 * size), 0.3 * size, 0.3 * size, 0.3 * size, 0);
        l.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 8, 0.3, 0.3, 0.3, 0.2);
        l.playSound(null, net.minecraft.core.BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.4F, 1.7F);
    }
}
