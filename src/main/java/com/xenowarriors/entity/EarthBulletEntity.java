package com.xenowarriors.entity;

import com.xenowarriors.damage.ModDamageTypes;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/**
 * Bang: a tumbling chunk of earth flicked from Rhalix's finger. It flies fast and straight, curving
 * a little toward the target he aimed at, and shatters on impact.
 */
public class EarthBulletEntity extends Projectile {
    public static final float SPEED = 2.2F;
    public static final float DAMAGE = 4F;
    private static final int LIFETIME = 30;

    private static final EntityDataAccessor<Integer> HOMING_TARGET = SynchedEntityData.defineId(EarthBulletEntity.class, EntityDataSerializers.INT);

    public EarthBulletEntity(EntityType<? extends EarthBulletEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static EarthBulletEntity create(Level level, Player owner, Vec3 from, Vec3 dir, LivingEntity target) {
        EarthBulletEntity b = new EarthBulletEntity(ModEntities.EARTH_BULLET.get(), level);
        b.setOwner(owner);
        b.setPos(from);
        b.setDeltaMovement(dir.normalize().scale(SPEED));
        b.entityData.set(HOMING_TARGET, target == null ? -1 : target.getId());
        return b;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(HOMING_TARGET, -1);
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount > LIFETIME) {
            discard();
            return;
        }
        // curve gently toward the target
        Entity target = level().getEntity(entityData.get(HOMING_TARGET));
        if (target != null && target.isAlive()) {
            Vec3 want = target.getBoundingBox().getCenter().subtract(position()).normalize().scale(SPEED);
            setDeltaMovement(getDeltaMovement().lerp(want, 0.18).normalize().scale(SPEED));
        }

        if (!level().isClientSide) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                onHit(hit);
                return;
            }
        } else {
            level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
                    getX(), getY(), getZ(), 0, 0, 0);
            if (tickCount % 2 == 0) level().addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0, 0, 0);
        }
        setPos(position().add(getDeltaMovement()));
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        return super.canHitEntity(e) && e instanceof LivingEntity && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity owner = getOwner();
        if (result.getEntity() instanceof LivingEntity target) {
            target.invulnerableTime = 0;
            target.hurt(ModDamageTypes.bang(level(), this, owner), DAMAGE);
            Vec3 push = getDeltaMovement().normalize().scale(0.25);
            target.push(push.x, 0.08, push.z);
        }
        shatter(result.getLocation());
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        shatter(result.getLocation());
    }

    private void shatter(Vec3 at) {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBBLESTONE.defaultBlockState()),
                    at.x, at.y, at.z, 18, 0.15, 0.15, 0.15, 0.25);
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
                    at.x, at.y, at.z, 10, 0.15, 0.15, 0.15, 0.2);
            server.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.3);
            server.playSound(null, at.x, at.y, at.z, SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.0F, 0.8F + random.nextFloat() * 0.3F);
            server.playSound(null, at.x, at.y, at.z, SoundEvents.GRAVEL_HIT, SoundSource.PLAYERS, 1.0F, 0.6F);
        }
        discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) { return dist < 96 * 96; }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
