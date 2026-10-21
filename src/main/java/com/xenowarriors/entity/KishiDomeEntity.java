package com.xenowarriors.entity;

import com.xenowarriors.damage.ModDamageTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Rhalix's Kishi: a heavy gravity dome slammed down around a target.
 * Everything inside (except Rhalix) is slowed, can't jump or fly, gets pulled to the
 * ground, and takes damage that grows the longer it stays inside.
 */
public class KishiDomeEntity extends Entity {
    // ---- Tuning (change these to balance the skill) ----
    public static final float RADIUS = 3.0F;          // 6 blocks wide
    public static final float HEIGHT = 5.0F;
    public static final int DURATION = 160;           // 8 seconds
    public static final int MAX_LEVEL = 3;
    /** Slowness amplifier per gravity level. */
    private static final int[] SLOWNESS = {3, 4, 6};
    /** Damage every second = BASE[level] + RAMP[level] * seconds spent inside. */
    private static final float[] DAMAGE_BASE = {1.5F, 2.0F, 2.5F};
    private static final float[] DAMAGE_RAMP = {0.25F, 0.5F, 0.75F};

    /** Set by the client entrypoint so the dome can tell the HUD/wings about itself (no-op on servers). */
    public static BiConsumer<UUID, Integer> clientHook = (owner, level) -> {};

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
        KishiDomeEntity dome = new KishiDomeEntity(ModEntities.KISHI_DOME, level);
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
        return dx * dx + dz * dz <= RADIUS * RADIUS && e.getY() >= getY() - 1 && e.getY() <= getY() + HEIGHT;
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
        AABB area = new AABB(getX() - RADIUS, getY() - 1, getZ() - RADIUS,
                getX() + RADIUS, getY() + HEIGHT, getZ() + RADIUS);

        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, area, this::affects)) {
            int ticks = ticksInside.merge(target.getId(), 1, Integer::sum);

            // heavy: slowed down
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, SLOWNESS[level - 1],
                    false, false, true));

            // pulled to the ground, no flying, no jumping
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
        clientHook.accept(getOwnerId(), level);

        // particles pressing down inside the dome
        for (int i = 0; i < 2 + level * 2; i++) {
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
        return new ClientboundAddEntityPacket(this);
    }
}
