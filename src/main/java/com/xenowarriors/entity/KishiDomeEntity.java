package com.xenowarriors.entity;

import com.xenowarriors.client.ClientSkillState;
import com.xenowarriors.damage.ModDamageTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkHooks;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Rhalix's Kishi: a heavy gravity dome slammed down around a target.
 * Everything inside (except Rhalix) is slowed, can't jump, gets pulled to the
 * ground, and takes damage that grows the longer it stays inside.
 */
public class KishiDomeEntity extends Entity {
    // ---- Tuning (change these to balance the skill) ----
    public static final float RADIUS = 3.0F;          // 6 blocks wide
    public static final float HEIGHT = 5.0F;
    public static final int DURATION = 160;           // 8 seconds
    public static final int MAX_LEVEL = 3;
    /** Every extra gravity level widens (and raises) the domain: 6, 9, then 12 blocks wide. */
    public static final float RADIUS_PER_LEVEL = 1.5F, HEIGHT_PER_LEVEL = 1.0F;
    public static final float MAX_RADIUS = RADIUS + RADIUS_PER_LEVEL * (MAX_LEVEL - 1);
    public static final float MAX_HEIGHT = HEIGHT + HEIGHT_PER_LEVEL * (MAX_LEVEL - 1);
    /** Slowness amplifier per gravity level (3 = -60%, 4 = -75%, 6 = can barely move). */
    private static final int[] SLOWNESS = {3, 4, 6};
    /** Damage every second = BASE[level] + RAMP[level] * seconds spent inside. */
    private static final float[] DAMAGE_BASE = {1.5F, 2.0F, 2.5F};
    private static final float[] DAMAGE_RAMP = {0.25F, 0.5F, 0.75F};

    private static final EntityDataAccessor<Integer> GRAVITY_LEVEL =
            SynchedEntityData.defineId(KishiDomeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(KishiDomeEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private final Map<Integer, Integer> ticksInside = new HashMap<>();

    public KishiDomeEntity(EntityType<? extends KishiDomeEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static KishiDomeEntity create(Level level, Player owner, Vec3 pos) {
        KishiDomeEntity dome = new KishiDomeEntity(ModEntities.KISHI_DOME.get(), level);
        dome.setPos(pos);
        dome.entityData.set(OWNER, Optional.of(owner.getUUID()));
        return dome;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(GRAVITY_LEVEL, 1);
        entityData.define(OWNER, Optional.empty());
    }

    public int getGravityLevel() { return entityData.get(GRAVITY_LEVEL); }

    public float radius() { return RADIUS + RADIUS_PER_LEVEL * (getGravityLevel() - 1); }

    public float height() { return HEIGHT + HEIGHT_PER_LEVEL * (getGravityLevel() - 1); }

    // client: the drawn size grows smoothly to the new level's size
    private float shownRadius = RADIUS, shownRadiusO = RADIUS, shownHeight = HEIGHT, shownHeightO = HEIGHT;

    public float shownRadius(float partial) { return net.minecraft.util.Mth.lerp(partial, shownRadiusO, shownRadius); }

    public float shownHeight(float partial) { return net.minecraft.util.Mth.lerp(partial, shownHeightO, shownHeight); }

    @Override
    public AABB getBoundingBoxForCulling() {
        return new AABB(getX() - MAX_RADIUS, getY() - 1, getZ() - MAX_RADIUS, getX() + MAX_RADIUS, getY() + MAX_HEIGHT, getZ() + MAX_RADIUS);
    }

    public UUID getOwnerId() { return entityData.get(OWNER).orElse(null); }

    /** Make the gravity heavier. Returns false if already at max. */
    public boolean raiseLevel() {
        int level = getGravityLevel();
        if (level >= MAX_LEVEL) return false;
        entityData.set(GRAVITY_LEVEL, level + 1);
        return true;
    }

    public boolean isInside(Entity e) {
        double dx = e.getX() - getX();
        double dz = e.getZ() - getZ();
        float r = radius();
        return dx * dx + dz * dz <= r * r && e.getY() >= getY() - 1 && e.getY() <= getY() + height();
    }

    /** Whether the dome should affect this entity. Rhalix himself is never affected. */
    public boolean affects(Entity e) {
        if (!(e instanceof LivingEntity) || !e.isAlive() || e.getUUID().equals(getOwnerId())) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        return isInside(e);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) clientTick();
        else serverTick();
    }

    private void serverTick() {
        Player owner = getOwnerId() == null ? null : level().getPlayerByUUID(getOwnerId());
        if (tickCount > DURATION || owner == null || !owner.isAlive()) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1F, 0.6F);
            discard();
            return;
        }

        int level = getGravityLevel();
        float r = radius();
        AABB area = new AABB(getX() - r, getY() - 1, getZ() - r, getX() + r, getY() + height(), getZ() + r);

        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, area, this::affects)) {
            int ticks = ticksInside.merge(target.getId(), 1, Integer::sum);

            // heavy: slowed down
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, SLOWNESS[level - 1],
                    false, false, true));

            // pulled to the ground, no flying
            if (target instanceof Player player) {
                if (player.getAbilities().flying) {
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                }
                if (player.isFallFlying()) player.stopFallFlying();
            }
            Vec3 v = target.getDeltaMovement();
            if (!target.onGround() || v.y > 0) {
                target.setDeltaMovement(v.x * 0.6, Math.min(v.y, -0.4 - 0.2 * level), v.z * 0.6);
                target.hurtMarked = true;
            }

            // damage once a second, growing the longer they stay in
            if (ticks % 20 == 0) {
                int seconds = ticks / 20 - 1;
                float damage = DAMAGE_BASE[level - 1] + DAMAGE_RAMP[level - 1] * seconds;
                Vec3 before = target.getDeltaMovement();
                target.hurt(ModDamageTypes.kishi(level(), this, owner), damage);
                target.setDeltaMovement(before.x, Math.min(before.y, 0), before.z); // no knockback out of the dome
                target.hurtMarked = true;
            }
        }
    }

    private void clientTick() {
        int level = getGravityLevel();
        shownRadiusO = shownRadius;
        shownHeightO = shownHeight;
        shownRadius += (radius() - shownRadius) * 0.25F;
        shownHeight += (height() - shownHeight) * 0.25F;
        float RADIUS = shownRadius, HEIGHT = shownHeight;
        if (Math.abs(radius() - shownRadius) > 0.3F && tickCount % 2 == 0) {
            // the edge pushing outward: a ring of dust on the ground
            for (int i = 0; i < 24; i++) {
                double angle = i / 24.0 * Math.PI * 2;
                level().addParticle(ParticleTypes.POOF, getX() + Math.cos(angle) * RADIUS, getY() + 0.1,
                        getZ() + Math.sin(angle) * RADIUS, Math.cos(angle) * 0.15, 0.02, Math.sin(angle) * 0.15);
            }
        }
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSkillState.markKishi(getOwnerId(), level));

        // particles pressing down inside the dome
        for (int i = 0; i < (2 + level * 2) * (RADIUS * RADIUS / 9F); i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = Math.sqrt(random.nextDouble()) * RADIUS;
            level().addParticle(ParticleTypes.END_ROD,
                    getX() + Math.cos(angle) * dist, getY() + random.nextDouble() * HEIGHT, getZ() + Math.sin(angle) * dist,
                    0, -0.3 - 0.15 * level, 0);
        }
        if (tickCount % 4 == 0) {
            for (int i = 0; i < 12; i++) {
                double angle = i / 12.0 * Math.PI * 2;
                level().addParticle(ParticleTypes.CLOUD,
                        getX() + Math.cos(angle) * RADIUS, getY() + 0.1, getZ() + Math.sin(angle) * RADIUS, 0, 0, 0);
            }
        }
    }

    @Override
    public boolean shouldBeSaved() {
        return false; // domes are temporary and don't save with the world
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
