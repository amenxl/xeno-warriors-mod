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
    private static final int FLIGHT_LIFE = 70;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(HekubladeShardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SLOT = SynchedEntityData.defineId(HekubladeShardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FIRED = SynchedEntityData.defineId(HekubladeShardEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(HekubladeShardEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SURF = SynchedEntityData.defineId(HekubladeShardEntity.class, EntityDataSerializers.BOOLEAN);
    /** Blade-surfing: launch speed, how fast it slows, when it starts to sink, and when it gives out. */
    public static final double SURF_SPEED = 1.3, SURF_DRAG = 0.985, SURF_SINK_BELOW = 0.6, SURF_END_BELOW = 0.18;
    private static final int SURF_LIFE = 20 * 20;

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
        entityData.define(SURF, false);
    }

    public boolean fired() { return entityData.get(FIRED); }
    public boolean surfing() { return entityData.get(SURF); }

    /**
     * Mikiko leaps on and rides it: the blade shoots off level in the direction he's facing, carrying him like a
     * hoverboard. It goes straight (no homing), slowly loses speed, starts to sink, and finally drops him.
     */
    public void surf(Player rider) {
        float yaw = rider.getYRot() * Mth.DEG_TO_RAD;
        float pitch = Mth.clamp(rider.getXRot(), -15F, 15F) * Mth.DEG_TO_RAD;     // mostly level, a slight climb or dive
        Vec3 dir = new Vec3(-Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
        setPos(rider.getX(), rider.getY() - 0.4, rider.getZ());
        setDeltaMovement(dir.scale(SURF_SPEED));
        entityData.set(FIRED, true);
        entityData.set(SURF, true);
        entityData.set(TARGET, -1);
        firedAt = tickCount;
        rider.startRiding(this, true);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, 1F, 1.3F);
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.5;      // feet planted on the flat of the blade
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return surfing() && getPassengers().isEmpty();
    }

    private void endSurf() {
        ejectPassengers();
        if (!level().isClientSide) shatter(position());
    }

    private void surfTick() {
        Vec3 v = getDeltaMovement();
        double flat = Math.sqrt(v.x * v.x + v.z * v.z);
        // loses momentum; once it's slow it starts to sink, then gives out
        double vy = v.y * 0.9;
        if (flat < SURF_SINK_BELOW) vy -= 0.035 * (1 - flat / SURF_SINK_BELOW) + 0.01;
        v = new Vec3(v.x * SURF_DRAG, vy, v.z * SURF_DRAG);
        setDeltaMovement(v);
        if (!level().isClientSide) {
            if (getPassengers().isEmpty() && tickCount - firedAt > 5 || flat < SURF_END_BELOW || tickCount - firedAt > SURF_LIFE) {
                endSurf();
                return;
            }
            // runs through anyone in its path (keeps going), stops dead at a wall or the ground
            for (Entity e : level().getEntities(this, getBoundingBox().expandTowards(v).inflate(0.6), this::canHitEntity)) {
                if (hasPassenger(e) || e == getOwner()) continue;
                if (e instanceof LivingEntity target && target.invulnerableTime <= 10) {
                    // rammed with Mikiko riding it: hits much harder - more the faster it's going, and with his strength
                    double speed = Math.sqrt(v.x * v.x + v.z * v.z);
                    double rider = getPassengers().isEmpty() || !(getFirstPassenger() instanceof LivingEntity l) ? 0
                            : l.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * 1.5;
                    float ram = (float) (Math.max(DAMAGE * 2.2, rider) * (0.6 + 0.6 * Math.min(1.0, speed / SURF_SPEED)));
                    target.hurt(ModDamageTypes.source(level(), ModDamageTypes.HEKUBLADE_SCATTER, this, getOwner()), ram);
                    if (level() instanceof net.minecraft.server.level.ServerLevel sl)
                        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
                    target.push(v.x * 0.9, 0.4, v.z * 0.9);
                    com.xenowarriors.mikiko.Hekublade.rollFire(target, 2);
                }
            }
            var block = level().clip(new net.minecraft.world.level.ClipContext(position(), position().add(v),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this));
            if (block.getType() != HitResult.Type.MISS) {
                endSurf();
                return;
            }
        } else {
            level().addParticle(ParticleTypes.FLAME, getX(), getY(), getZ(), 0, 0, 0);
            level().addParticle(new DustParticleOptions(new Vector3f(1F, 0.97F, 0.92F), 0.8F), getX() - v.x, getY(), getZ() - v.z, 0, 0, 0);
        }
        setPos(position().add(v));
    }
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
                fire(owner instanceof net.minecraft.server.level.ServerPlayer p ? com.xenowarriors.skill.SkillTargeting.homing(p, 40) : null);
            }
            return;
        }
        if (firedAt < 0) firedAt = tickCount;
        if (surfing()) {
            surfTick();
            return;
        }
        if (tickCount - firedAt > FLIGHT_LIFE) {
            if (!level().isClientSide) discard();
            return;
        }
        Entity target = level().getEntity(entityData.get(TARGET));
        if ((target == null || !target.isAlive()) && !level().isClientSide && entityData.get(TARGET) != -1) {
            // its target fell before it arrived: swing round to the nearest other enemy
            target = retarget();
            entityData.set(TARGET, target == null ? -1 : target.getId());
        }
        boolean homing = target != null && target.isAlive();
        if (homing) {
            Vec3 aim = target.getBoundingBox().getCenter();
            double dist = aim.distanceTo(position());
            // lead the target: aim where it will be when the blade gets there
            aim = aim.add(target.getDeltaMovement().multiply(1, 0.3, 1).scale(Math.min(dist / SPEED, 10)));
            Vec3 want = aim.subtract(position()).normalize().scale(SPEED);
            double turn = dist < 3 ? 1.0 : 0.45;
            setDeltaMovement(getDeltaMovement().lerp(want, turn).normalize().scale(SPEED));
        }
        if (!level().isClientSide) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            // while chasing a live target the blade cuts straight through walls in its way
            if (homing && hit.getType() == HitResult.Type.BLOCK) {
                var box = getBoundingBox().expandTowards(getDeltaMovement()).inflate(0.5);
                hit = target.getBoundingBox().intersects(box) ? new EntityHitResult(target) : HitResult.class.cast(
                        net.minecraft.world.phys.BlockHitResult.miss(position(), net.minecraft.core.Direction.UP, blockPosition()));
            }
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

    private LivingEntity retarget() {
        Entity owner = getOwner();
        if (!(owner instanceof net.minecraft.server.level.ServerPlayer p)) return null;
        return com.xenowarriors.skill.SkillTargeting.around(p, position(), 14, 8, 8).stream()
                .filter(e -> e instanceof net.minecraft.world.entity.monster.Enemy || e == p.getLastHurtMob())
                .findFirst().orElse(null);
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
        return fired() && super.canHitEntity(e) && !hasPassenger(e) && e instanceof LivingEntity && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
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
