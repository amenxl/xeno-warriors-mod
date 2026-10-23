package com.xenowarriors.npc;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** What the townsfolk shoot or throw: the gunner's bullets, the phone guy's phone, the briefcase. */
public class NpcThrownEntity extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(NpcThrownEntity.class, EntityDataSerializers.FLOAT);
    private boolean bullet;

    public NpcThrownEntity(EntityType<? extends NpcThrownEntity> type, Level level) {
        super(type, level);
    }

    public NpcThrownEntity(Level level, LivingEntity shooter, net.minecraft.world.item.ItemStack look, float damage, boolean bullet) {
        super(ModNpcs.THROWN.get(), shooter, level);
        setItem(look);
        entityData.set(DAMAGE, damage);
        this.bullet = bullet;
        if (bullet) setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DAMAGE, 3F);
    }

    @Override
    protected Item getDefaultItem() {
        return net.minecraft.world.item.Items.IRON_NUGGET;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && bullet()) level().addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0, 0, 0);
        if (!level().isClientSide && tickCount > 80) discard();
    }

    private boolean bullet() {
        return bullet || isNoGravity();
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (hit.getEntity() == getOwner()) return;
        hit.getEntity().hurt(damageSources().thrown(this, getOwner()), entityData.get(DAMAGE));
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) {
            ((net.minecraft.server.level.ServerLevel) level()).sendParticles(new ItemParticleOption(ParticleTypes.ITEM, getItem()),
                    getX(), getY(), getZ(), 6, 0.1, 0.1, 0.1, 0.05);
            discard();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Damage", entityData.get(DAMAGE));
        tag.putBoolean("Bullet", bullet);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DAMAGE, tag.getFloat("Damage"));
        bullet = tag.getBoolean("Bullet");
    }
}
