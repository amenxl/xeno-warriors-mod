package com.xenowarriors.skill.kaeru;

import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillTargeting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Kaeru - Sound Burst (sound element): a cone of sound that hits everything in front of her, knocks it back and leaves its ears ringing. */
public class SoundBurstSkill implements Skill {
    @Override public String getId() { return "sound_burst"; }
    @Override public int getCooldownTicks() { return 8 * 20; }
    @Override public int getKarmaCost() { return 20; }
    @Override public int clashColor() { return 0xFFB060; }

    @Override
    public boolean use(ServerPlayer p) {
        Vec3 look = p.getLookAngle(), eye = p.getEyePosition();
        float dmg = 7F * 1F;
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(9))) {
            if (e == p || !SkillTargeting.canTarget(p, e)) continue;
            Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0).subtract(eye);
            if (to.length() > 9 || to.normalize().dot(look) < 0.55) continue;
            e.hurt(p.damageSources().playerAttack(p), dmg);
            e.knockback(1.3, -look.x, -look.z);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1));
            e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60, 0));
        }
        for (int ring = 1; ring <= 4; ring++) {
            double d = ring * 2.0;
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI / 6;
                Vec3 side = new Vec3(-look.z, 0, look.x).normalize();
                Vec3 at = eye.add(look.scale(d)).add(side.scale(Math.cos(a) * d * 0.35)).add(0, Math.sin(a) * d * 0.35, 0);
                p.serverLevel().sendParticles(ring % 2 == 0 ? ParticleTypes.NOTE : ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0, 0, 0, ring % 2 == 0 ? 0.5 : 0);
                if (ring % 2 == 1) break;
            }
        }
        p.level().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1.4F, 0.7F);
        p.level().playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.5F, 1.6F);
        return true;
    }
}
