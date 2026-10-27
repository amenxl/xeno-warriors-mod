package com.xenowarriors.skill.kaeru;

import com.xenowarriors.skill.ServerScheduler;
import com.xenowarriors.skill.Skill;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Kaeru's ultimate - Hekubeats Finale: the Hekublade turns into a beat, and four pulses of sound blast out around her
 * (each bigger), stunning and battering everything within 12 blocks; allies inside get a speed and strength buff
 * from the music.
 */
public class HekubeatsFinaleSkill implements Skill {
    @Override public String getId() { return "hekubeats_finale"; }
    @Override public int getCooldownTicks() { return 90 * 20; }
    @Override public int getKarmaCost() { return 80; }

    @Override
    public boolean use(ServerPlayer p) {
        for (int i = 0; i < 4; i++) {
            final int beat = i;
            ServerScheduler.later(i * 10, () -> pulse(p, beat));
        }
        return true;
    }

    private static void pulse(ServerPlayer p, int beat) {
        if (!p.isAlive()) return;
        var l = p.serverLevel();
        double r = 5 + beat * 2.4;
        float dmg = (6F + beat * 2F) * 1F;
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r))) {
            if (e == p || e.distanceTo(p) > r) continue;
            if (com.xenowarriors.skill.SkillTargeting.canTarget(p, e)) {
                e.hurt(p.damageSources().playerAttack(p), dmg);
                e.knockback(0.8 + beat * 0.2, p.getX() - e.getX(), p.getZ() - e.getZ());
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3));
            } else if (e instanceof ServerPlayer o && com.xenowarriors.party.Parties.sameParty(p, o)) {
                o.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 1));
                o.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 0));
            }
        }
        for (int i = 0; i < 40; i++) {
            double a = i * Math.PI * 2 / 40;
            l.sendParticles(i % 4 == 0 ? ParticleTypes.NOTE : ParticleTypes.SONIC_BOOM, p.getX() + Math.cos(a) * r, p.getY() + 0.8, p.getZ() + Math.sin(a) * r, 1, 0, 0, 0, i % 4 == 0 ? 0.6 : 0);
        }
        l.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.PLAYERS, 2F, 0.6F + beat * 0.15F);
        l.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.6F, 1.2F + beat * 0.1F);
        com.xenowarriors.XwFx.send(l, com.xenowarriors.network.FxPacket.ULTIMATE, p.getX(), p.getY(), p.getZ(), 0xE8701C, (float) r);
    }
}
