package com.xenowarriors.corrupted;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** A bolt of corrupted soul fire thrown by the C-Soul. Withers what it hits. */
public class SoulBoltEntity extends AbstractHurtingProjectile {
    public SoulBoltEntity(EntityType<? extends SoulBoltEntity> type, Level level) {
        super(type, level);
    }

    public SoulBoltEntity(Level level, LivingEntity shooter, double dx, double dy, double dz) {
        super(ModCorrupted.SOUL_BOLT.get(), shooter, dx, dy, dz, level);
    }

    @Override
    protected boolean shouldBurn() {
        return false;
    }

    @Override
    protected ParticleOptions getTrailParticle() {
        return CorruptedFx.DARK;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && random.nextBoolean()) {
            level().addParticle(ParticleTypes.WITCH, getX(), getY() + 0.2, getZ(), 0, 0, 0);
        }
        if (!level().isClientSide && tickCount > 120) discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (level().isClientSide) return;
        Entity target = hit.getEntity();
        Entity owner = getOwner();
        if (owner instanceof CSoulBossEntity || owner instanceof CorruptedSoulEntity) {
            if (target instanceof CSoulBossEntity || target instanceof CorruptedSoulEntity) return;
        }
        if (target.hurt(damageSources().mobProjectile(this, owner instanceof LivingEntity l ? l : null), 7F)
                && target instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0), owner);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(CorruptedFx.BLACK, getX(), getY(), getZ(), 10, 0.3, 0.3, 0.3, 0.05);
            server.playSound(null, blockPosition(), SoundEvents.SOUL_ESCAPE, getSoundSource(), 1F, 0.7F);
            discard();
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
