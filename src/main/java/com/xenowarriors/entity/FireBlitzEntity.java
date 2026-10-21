package com.xenowarriors.entity;

import com.xenowarriors.damage.ModDamageTypes;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.TrackTargetPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

/**
 * Fire Blitz: a ball of fire Mikiko throws that sticks to whoever it hits. While it's stuck on
 * them they keep burning, and Mikiko can see them through walls.
 */
public class FireBlitzEntity extends Projectile {
    public static final int STUCK_TIME = 12 * 20;
    public static final float SPEED = 1.5F, IMPACT_DAMAGE = 3F;
    private static final int FLIGHT_LIFE = 50;

    private static final EntityDataAccessor<Integer> STUCK_TO = SynchedEntityData.defineId(FireBlitzEntity.class, EntityDataSerializers.INT);
    private int stuckAt = -1;

    public FireBlitzEntity(EntityType<? extends FireBlitzEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static FireBlitzEntity create(Player owner, Vec3 from, Vec3 dir) {
        FireBlitzEntity e = new FireBlitzEntity(ModEntities.FIRE_BLITZ.get(), owner.level());
        e.setOwner(owner);
        e.setPos(from);
        e.setDeltaMovement(dir.normalize().scale(SPEED));
        return e;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(STUCK_TO, -1);
    }

    public Entity stuckTo() {
        int id = entityData.get(STUCK_TO);
        return id < 0 ? null : level().getEntity(id);
    }

    public boolean isStuck() {
        return entityData.get(STUCK_TO) >= 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (isStuck()) {
            Entity t = stuckTo();
            if (stuckAt < 0) stuckAt = tickCount;
            if (t == null || !t.isAlive() || tickCount - stuckAt > STUCK_TIME) {
                if (!level().isClientSide) burst();
                return;
            }
            // ride along on them, a little in front of their chest
            setPos(t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ());
            if (!level().isClientSide && (tickCount - stuckAt) % 20 == 0) t.setSecondsOnFire(3);
            if (level().isClientSide) {
                level().addParticle(ParticleTypes.FLAME, getX() + (random.nextDouble() - 0.5) * 0.4, getY() + random.nextDouble() * 0.3,
                        getZ() + (random.nextDouble() - 0.5) * 0.4, 0, 0.03, 0);
                if (tickCount % 5 == 0) level().addParticle(ParticleTypes.LAVA, getX(), getY(), getZ(), 0, 0, 0);
            }
            return;
        }
        if (tickCount > FLIGHT_LIFE) {
            if (!level().isClientSide) burst();
            return;
        }
        setDeltaMovement(getDeltaMovement().add(0, -0.025, 0));
        if (!level().isClientSide) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                onHit(hit);
                if (isStuck() || isRemoved()) return;
            }
        } else {
            for (int i = 0; i < 2; i++) level().addParticle(ParticleTypes.FLAME, getX(), getY(), getZ(),
                    (random.nextDouble() - 0.5) * 0.05, (random.nextDouble() - 0.5) * 0.05, (random.nextDouble() - 0.5) * 0.05);
            level().addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0, 0, 0);
        }
        setPos(position().add(getDeltaMovement()));
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        return super.canHitEntity(e) && e instanceof LivingEntity && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!(result.getEntity() instanceof LivingEntity target)) return;
        target.hurt(ModDamageTypes.source(level(), ModDamageTypes.FIRE_BLITZ, this, getOwner()), IMPACT_DAMAGE);
        target.setSecondsOnFire(4);
        entityData.set(STUCK_TO, target.getId());
        stuckAt = tickCount;
        setDeltaMovement(Vec3.ZERO);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 1F, 0.7F);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1F, 1.1F);
        if (getOwner() instanceof ServerPlayer owner) {
            ModNetwork.sendToPlayer(new TrackTargetPacket(target.getId(), STUCK_TIME), owner);
            owner.displayClientMessage(Component.translatable("message.xenowarriors.blitz.stuck", target.getDisplayName()), true);
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        burst();
    }

    private void burst() {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), 20, 0.3, 0.3, 0.3, 0.08);
            server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY(), getZ(), 6, 0.2, 0.2, 0.2, 0.02);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 1.2F);
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
