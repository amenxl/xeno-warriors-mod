package com.xenowarriors.skill.mikiko;

import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.SkillAnimPacket;
import com.xenowarriors.skill.ServerScheduler;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillTargeting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Komatsu Norio's Dance Kick, learned through True Empathy: a disco spin up into the air that kicks everything around
 * him away, then a flying finishing kick into whoever he's aiming at.
 */
public class DanceKickSkill implements Skill {
    private static final Vector3f[] DISCO = {new Vector3f(1F, .2F, .8F), new Vector3f(.2F, .9F, 1F), new Vector3f(1F, .9F, .2F), new Vector3f(.5F, 1F, .3F)};

    @Override public String getId() { return "dance_kick"; }
    @Override public int getCooldownTicks() { return 14 * 20; }
    @Override public int getKarmaCost() { return 25; }

    @Override
    public boolean use(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        p.displayClientMessage(Component.literal("The Komatsu Norio! Dance Kick!").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), true);
        ModNetwork.sendToTrackingAndSelf(new SkillAnimPacket(p.getId(), SkillAnimPacket.DANCE_KICK, false), p);
        p.setDeltaMovement(p.getDeltaMovement().multiply(0.3, 0, 0.3).add(0, 0.55, 0));
        p.hurtMarked = true;
        level.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1F, 1.4F);
        // the spin: three sweeps around him
        for (int i = 0; i < 3; i++) {
            final int k = i;
            ServerScheduler.later(3 + i * 3, () -> {
                if (!p.isAlive()) return;
                for (int a = 0; a < 16; a++) {
                    double ang = a * Math.PI / 8 + k;
                    level.sendParticles(new DustParticleOptions(DISCO[(a + k) % 4], 1.2F), p.getX() + Math.cos(ang) * 2.2, p.getY() + 0.6, p.getZ() + Math.sin(ang) * 2.2, 1, 0, 0, 0, 0);
                }
                level.sendParticles(ParticleTypes.NOTE, p.getX(), p.getY() + 2.2, p.getZ(), 2, 0.6, 0.2, 0.6, 1);
                level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9F, 1.2F + k * 0.15F);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(3.2, 1.5, 3.2), e -> SkillTargeting.canTarget(p, e))) {
                    e.hurt(p.damageSources().playerAttack(p), 3.5F);
                    Vec3 away = e.position().subtract(p.position()).multiply(1, 0, 1).normalize().scale(0.7);
                    e.push(away.x, 0.25, away.z);
                    e.hurtMarked = true;
                }
            });
        }
        // the finisher: a flying kick at whoever he's aiming at
        ServerScheduler.later(12, () -> {
            if (!p.isAlive()) return;
            LivingEntity t = SkillTargeting.aimed(p, 9, 25);
            Vec3 dir = t != null ? t.position().subtract(p.position()).normalize() : p.getLookAngle();
            p.setDeltaMovement(dir.scale(1.3).add(0, 0.1, 0));
            p.hurtMarked = true;
            ServerScheduler.later(4, () -> {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(1.8), e -> SkillTargeting.canTarget(p, e))) {
                    e.hurt(p.damageSources().playerAttack(p), 9F);
                    e.push(dir.x * 1.6, 0.5, dir.z * 1.6);
                    e.hurtMarked = true;
                    level.sendParticles(ParticleTypes.EXPLOSION, e.getX(), e.getY() + 1, e.getZ(), 1, 0, 0, 0, 0);
                    level.playSound(null, e.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2F, 0.8F);
                }
                p.setDeltaMovement(p.getDeltaMovement().scale(0.2));
                p.hurtMarked = true;
            });
        });
        p.fallDistance = 0;
        return true;
    }
}
