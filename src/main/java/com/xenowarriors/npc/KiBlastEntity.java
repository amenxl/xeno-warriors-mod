package com.xenowarriors.npc;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Vector3f;

/** A Ki blast (fighters, with DragonMineZ installed): a glowing ball of energy that bursts on impact. */
public class KiBlastEntity extends AbstractHurtingProjectile {
    private static final DustParticleOptions GLOW = new DustParticleOptions(new Vector3f(1F, 0.92F, 0.45F), 1.6F);

    public KiBlastEntity(EntityType<? extends KiBlastEntity> type, Level level) {
        super(type, level);
    }

    public KiBlastEntity(Level level, LivingEntity shooter, double dx, double dy, double dz) {
        super(ModNpcs.KI_BLAST.get(), shooter, dx, dy, dz, level);
    }

    @Override
    protected boolean shouldBurn() {
        return false;
    }

    @Override
    protected ParticleOptions getTrailParticle() {
        return GLOW;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            for (int i = 0; i < 3; i++)
                level().addParticle(GLOW, getX() + (random.nextDouble() - 0.5) * 0.3, getY() + 0.15 + (random.nextDouble() - 0.5) * 0.3,
                        getZ() + (random.nextDouble() - 0.5) * 0.3, 0, 0, 0);
            if (random.nextInt(3) == 0) level().addParticle(ParticleTypes.END_ROD, getX(), getY() + 0.15, getZ(), 0, 0, 0);
        }
        if (!level().isClientSide && tickCount > 100) discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (level().isClientSide) return;
        Entity target = hit.getEntity();
        Entity owner = getOwner();
        if (target == owner) return;
        target.hurt(damageSources().mobProjectile(this, owner instanceof LivingEntity l ? l : null), 6F);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.FLASH, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
            server.sendParticles(GLOW, getX(), getY(), getZ(), 16, 0.4, 0.4, 0.4, 0.05);
            server.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, getSoundSource(), 0.5F, 1.6F);
            discard();
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
