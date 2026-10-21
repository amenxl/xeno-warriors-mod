package com.xenowarriors.entity;

import com.xenowarriors.damage.ModDamageTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.joml.Vector3f;

/**
 * One of the copies of the Hekublade made by Hekublade Scatter. It hovers beside Mikiko pointing
 * wherever he looks, until he fires it (each press of the skill key fires one) - then it flies at
 * his target, curving after it, and burns what it hits.
 */
public class HekubladeShardEntity extends Projectile {
    public static final int HOVER_LIFE = 160;          // auto-fires after 8 seconds
    public static final float SPEED = 2.6F, DAMAGE = 5F;
    private static final int FLIGHT_LIFE = 40;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(HekubladeShardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SLOT = SynchedEntityData.defineId(HekubladeShardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FIRED = SynchedEntityData.defineId(HekubladeShardEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(HekubladeShardEntity.class, EntityDataSerializers.INT);

    private int firedAt = -1;

    public HekubladeShardEntity(EntityType<? extends HekubladeShardEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static HekubladeShardEntity create(Player owner, int slot) {
        HekubladeShardEntity e = new HekubladeShardEntity(ModEntities.HEKUBLADE_SHARD.get(), owner.level());
        e.setOwner(owner);
        e.entityData.set(OWNER_ID, owner.getId());
        e.entityData.set(SLOT, slot);
        e.setPos(e.hoverPos(owner, 0F));
        return e;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(OWNER_ID, -1);
        entityData.define(SLOT, 0);
        entityData.define(FIRED, false);
        entityData.define(TARGET, -1);
    }

    public boolean fired() { return entityData.get(FIRED); }
    public int slot() { return entityData.get(SLOT); }
    public Entity ownerEntity() { return level().getEntity(entityData.get(OWNER_ID)); }

    /** Where this blade floats: three on each side of him, stepping up and out like a fan. */
    public Vec3 hoverPos(Entity owner, float partial) {
        int slot = slot();
        float yaw = Mth.lerp(partial, owner.yRotO, owner.getYRot()) * ((float) Math.PI / 180F);
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        Vec3 back = new Vec3(Mth.sin(yaw), 0, -Mth.cos(yaw));
        int side = slot % 2 == 0 ? 1 : -1, tier = slot / 2;
        float age = tickCount + partial;
        return owner.getPosition(partial)
                .add(right.scale(side * (1.35 + 0.4 * tier)))
                .add(0, 0.7 + tier * 0.72 + Mth.sin(age * 0.15F + slot) * 0.08, 0)
                .add(back.scale(0.25 + 0.15 * tier));
    }

    /** Point it at the target (or straight where he's looking) and let it fly. */
    public void fire(LivingEntity target) {
        Entity owner = ownerEntity();
        Vec3 dir = target != null ? target.getBoundingBox().getCenter().subtract(position())
                : owner instanceof LivingEntity l ? l.getEyePosition().add(l.getViewVector(1F).scale(30)).subtract(position()) : Vec3.ZERO;
        if (dir.lengthSqr() < 1.0E-4) dir = new Vec3(0, 0, 1);
        setDeltaMovement(dir.normalize().scale(SPEED));
        entityData.set(FIRED, true);
        entityData.set(TARGET, target == null ? -1 : target.getId());
        firedAt = tickCount;
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1F, 1.4F);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.4F, 1.6F);
    }

    @Override
    public void tick() {
        super.tick();
        Entity owner = ownerEntity();
        if (!fired()) {
            if (owner == null || !owner.isAlive()) {
                if (!level().isClientSide) discard();
                return;
            }
            setPos(hoverPos(owner, 0F));
            setDeltaMovement(Vec3.ZERO);
            if (level().isClientSide) {
                if (tickCount == 1) summonBurst();
                if (tickCount % 4 == 0) {
                    level().addParticle(new DustParticleOptions(new Vector3f(1F, 0.97F, 0.92F), 0.6F), getX(), getY(), getZ(), 0, 0, 0);
                }
            } else if (tickCount > HOVER_LIFE) {
                fire(owner instanceof Player p ? com.xenowarriors.skill.SkillTargeting.aimed((net.minecraft.server.level.ServerPlayer) p, 40, 10) : null);
            }
            return;
        }
        if (firedAt < 0) firedAt = tickCount;
        if (tickCount - firedAt > FLIGHT_LIFE) {
            if (!level().isClientSide) discard();
            return;
        }
        Entity target = level().getEntity(entityData.get(TARGET));
        if (target != null && target.isAlive()) {
            Vec3 want = target.getBoundingBox().getCenter().subtract(position()).normalize().scale(SPEED);
            setDeltaMovement(getDeltaMovement().lerp(want, 0.25).normalize().scale(SPEED));
        }
        if (!level().isClientSide) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                onHit(hit);
                return;
            }
        } else {
            level().addParticle(ParticleTypes.FLAME, getX(), getY(), getZ(), 0, 0, 0);
            level().addParticle(new DustParticleOptions(new Vector3f(1F, 0.5F, 0.1F), 0.8F), getX(), getY(), getZ(), 0, 0, 0);
        }
        setPos(position().add(getDeltaMovement()));
    }

    private void summonBurst() {
        Vector3f[] cols = {new Vector3f(0.95F, 0.12F, 0.1F), new Vector3f(1F, 0.55F, 0.08F), new Vector3f(1F, 0.97F, 0.92F)};
        for (int i = 0; i < 12; i++) {
            level().addParticle(new DustParticleOptions(cols[i % 3], 0.9F), getX(), getY(), getZ(),
                    (random.nextDouble() - 0.5) * 0.3, (random.nextDouble() - 0.5) * 0.3, (random.nextDouble() - 0.5) * 0.3);
        }
        level().addParticle(ParticleTypes.FLASH, getX(), getY(), getZ(), 0, 0, 0);
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        return fired() && super.canHitEntity(e) && e instanceof LivingEntity && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (result.getEntity() instanceof LivingEntity target) {
            target.invulnerableTime = 0;
            target.hurt(ModDamageTypes.source(level(), ModDamageTypes.HEKUBLADE_SCATTER, this, getOwner()), DAMAGE);
            com.xenowarriors.mikiko.Hekublade.rollFire(target, 2);
        }
        shatter(result.getLocation());
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        shatter(result.getLocation());
    }

    private void shatter(Vec3 at) {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 10, 0.15, 0.15, 0.15, 0.06);
            server.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.1, 0.1, 0.1, 0.3);
            server.sendParticles(new DustParticleOptions(new Vector3f(1F, 0.97F, 0.92F), 1F), at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0);
            server.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1F, 1.3F);
        }
        discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) { return dist < 96 * 96; }

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
