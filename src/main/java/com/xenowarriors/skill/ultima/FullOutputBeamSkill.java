package com.xenowarriors.skill.ultima;

import com.xenowarriors.skill.ServerScheduler;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillTargeting;
import com.xenowarriors.ultima.UltimaOutput;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/** Ultima's ultimate - Full Output Beam: a charge, then a huge green beam. Stronger the higher his output. */
public class FullOutputBeamSkill implements Skill {
    @Override public String getId() { return "full_output_beam"; }
    @Override public int getCooldownTicks() { return 60 * 20; }
    @Override public int getKarmaCost() { return 60; }
    @Override public int clashColor() { return 0x3FFF8C; }

    @Override
    public boolean use(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        int t = UltimaOutput.overloaded(p) ? 4 : UltimaOutput.tier(p);
        float dmg = 10F + 6F * t;
        double width = 1.0 + 0.4 * t;
        com.xenowarriors.anim.Poses.play(p, com.xenowarriors.anim.Poses.EL_CHARGE, 20);
        l.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 0.7F);
        for (int i = 0; i < 20; i++) ServerScheduler.later(i, () -> {
            Vec3 h = p.getEyePosition().add(p.getLookAngle().scale(0.8));
            l.sendParticles(new DustParticleOptions(UltimaBlasts.GREEN, 1.6F), h.x, h.y - 0.2, h.z, 4, 0.2, 0.2, 0.2, 0);
        });
        ServerScheduler.later(20, () -> {
            if (!p.isAlive()) return;
            com.xenowarriors.anim.Poses.play(p, com.xenowarriors.anim.Poses.EL_THRUST, 30);
            l.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.4F, 0.8F);
            Set<Integer> hit = new HashSet<>();
            for (int k = 0; k < 24; k++) ServerScheduler.later(k, () -> {
                if (!p.isAlive()) return;
                Vec3 s = p.getEyePosition().subtract(0, 0.2, 0), d = p.getLookAngle();
                for (int j = 1; j <= 32; j++) {
                    Vec3 q = s.add(d.scale(j));
                    l.sendParticles(new DustParticleOptions(UltimaBlasts.GREEN, 2.2F), q.x, q.y, q.z, 2, width * 0.25, width * 0.25, width * 0.25, 0);
                    if (j % 4 == 0) l.sendParticles(ParticleTypes.ELECTRIC_SPARK, q.x, q.y, q.z, 1, 0.2, 0.2, 0.2, 0.1);
                }
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(s, s.add(d.scale(32))).inflate(width), e -> SkillTargeting.canTarget(p, e))) {
                    Vec3 rel = e.position().add(0, e.getBbHeight() / 2, 0).subtract(s);
                    double along = rel.dot(d);
                    if (along < 0 || along > 32 || rel.subtract(d.scale(along)).length() > width + e.getBbWidth()) continue;
                    if (hit.add(e.getId())) { e.hurt(p.damageSources().indirectMagic(p, p), dmg); e.push(d.x * 1.2, 0.3, d.z * 1.2); e.hurtMarked = true; }
                }
            });
        });
        return true;
    }
}
