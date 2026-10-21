package com.xenowarriors.corrupted;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;

/**
 * A corrupted soul possessing a villager. Shambles after players and villagers at night. If the body was a real
 * villager the soul took over, the soul is driven out at sunrise and the villager comes back to itself; a lost
 * wandering body just burns away like a zombie.
 */
public class CorruptedVillagerEntity extends Zombie {
    private boolean wasVillager;

    public CorruptedVillagerEntity(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        this.xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 26.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    public void markWasVillager() {
        wasVillager = true;
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    protected boolean isSunSensitive() {
        return !wasVillager;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            if (random.nextInt(4) == 0) {
                level().addParticle(ParticleTypes.WITCH, getRandomX(0.5), getY() + 1.6 + random.nextDouble() * 0.5, getRandomZ(0.5), 0, 0.02, 0);
            }
            if (random.nextInt(12) == 0) {
                level().addParticle(ParticleTypes.SOUL, getRandomX(0.4), getY() + random.nextDouble() * 1.8, getRandomZ(0.4), 0, 0.03, 0);
            }
            return;
        }
        if (wasVillager && tickCount % 20 == 0 && level().isDay() && level().canSeeSky(blockPosition())) {
            Villager freed = convertTo(EntityType.VILLAGER, false);
            if (freed != null && level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.SOUL, freed.getX(), freed.getY() + 1.2, freed.getZ(), 20, 0.3, 0.5, 0.3, 0.05);
                server.playSound(null, freed.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CONVERTED, getSoundSource(), 1F, 1F);
            }
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ZOMBIE_VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ZOMBIE_VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ZOMBIE_VILLAGER_DEATH;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("WasVillager", wasVillager);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        wasVillager = tag.getBoolean("WasVillager");
    }
}
