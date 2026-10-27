package com.xenowarriors.soul;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Rhalix's soul uses his elements once his Soul Splitter levels up: gravity slams (level 10+) and wire lashes (level 25+). */
final class RhalixSoulArts {
    static final int GRAVITY_LEVEL = 10, WIRE_LEVEL = 25;
    private static final DustParticleOptions PURPLE = new DustParticleOptions(new Vector3f(0.55F, 0.25F, 0.85F), 1.4F);
    private static final DustParticleOptions WIRE = new DustParticleOptions(new Vector3f(0.8F, 0.8F, 0.9F), 0.7F);

    private RhalixSoulArts() {}

    static void cast(SoulEntity s, LivingEntity t, int level) {
        if (level < GRAVITY_LEVEL || !(s.level() instanceof ServerLevel l) || s.distanceTo(t) > 14) return;
        float dmg = (float) s.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        boolean wire = level >= WIRE_LEVEL && s.getRandom().nextBoolean();
        if (wire) {                                                   // wire lash: yanks them in
            Vec3 a = s.getEyePosition(), b = t.position().add(0, t.getBbHeight() * 0.5, 0);
            for (int i = 0; i <= 20; i++) { Vec3 q = a.lerp(b, i / 20.0); l.sendParticles(WIRE, q.x, q.y, q.z, 1, 0, 0, 0, 0); }
            t.hurt(s.damageSources().mobAttack(s), dmg * 1.2F);
            Vec3 pull = s.position().subtract(t.position()).normalize().scale(0.9);
            t.push(pull.x, 0.25, pull.z); t.hurtMarked = true;
            l.playSound(null, t.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 1F, 0.7F);
        } else {                                                      // gravity slam: crushed down, slowed
            l.sendParticles(PURPLE, t.getX(), t.getY() + 0.2, t.getZ(), 30, 0.8, 0.1, 0.8, 0);
            l.sendParticles(ParticleTypes.REVERSE_PORTAL, t.getX(), t.getY() + 1.5, t.getZ(), 20, 0.4, 0.8, 0.4, 0.05);
            t.hurt(s.damageSources().mobAttack(s), dmg * 1.5F);
            t.setDeltaMovement(t.getDeltaMovement().multiply(0.3, 0, 0.3).add(0, -0.8, 0)); t.hurtMarked = true;
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 2));
            l.playSound(null, t.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.5F, 0.6F);
        }
    }
}
