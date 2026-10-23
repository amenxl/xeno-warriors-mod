package com.xenowarriors.entity;

import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;

/**
 * The Season 3 Hekublade thrown like a boomerang: spins out (homing on Mikiko's lock-on target, however far), cuts
 * around them for a moment, then drifts slowly back to his hand. Its damage follows his attack power.
 */
public class ThrownHekubladeEntity extends Entity {
    public static final int OUT = 0, LINGER = 1, BACK = 2, MAX_LIFE = 15 * 20;
    private static final double OUT_SPEED = 1.3, BACK_SPEED = 0.5, FREE_RANGE = 24;
    private static final int LINGER_TIME = 30;
    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(ThrownHekubladeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(ThrownHekubladeEntity.class, EntityDataSerializers.INT);

    private int targetId = -1, phaseStart;
    private Vec3 start = Vec3.ZERO, dir = new Vec3(0, 0, 1);
    private final Set<Integer> hit = new HashSet<>();

    public ThrownHekubladeEntity(EntityType<? extends ThrownHekubladeEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static ThrownHekubladeEntity create(ServerPlayer owner, LivingEntity target) {
        ThrownHekubladeEntity e = new ThrownHekubladeEntity(ModEntities.THROWN_HEKUBLADE.get(), owner.level());
        e.entityData.set(OWNER_ID, owner.getId());
        e.targetId = target != null ? target.getId() : -1;
        e.dir = owner.getViewVector(1F);
        e.start = owner.getEyePosition().add(e.dir.scale(0.8));
        e.setPos(e.start);
        return e;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(OWNER_ID, -1);
        entityData.define(PHASE, OUT);
    }

    public int ownerId() { return entityData.get(OWNER_ID); }
    public int phase() { return entityData.get(PHASE); }

    private void phase(int p) {
        entityData.set(PHASE, p);
        phaseStart = tickCount;
        hit.clear();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (!(level().getEntity(ownerId()) instanceof ServerPlayer owner) || !owner.isAlive()) { discard(); return; }
        if (tickCount > MAX_LIFE) { home(owner); return; }
        Vec3 pos = position();
        LivingEntity target = level().getEntity(targetId) instanceof LivingEntity l && l.isAlive() ? l : null;
        Vec3 next;
        switch (phase()) {
            case OUT -> {
                Vec3 dest = target != null ? target.position().add(0, target.getBbHeight() * 0.6, 0) : start.add(dir.scale(FREE_RANGE));
                Vec3 to = dest.subtract(pos);
                next = to.length() <= OUT_SPEED ? dest : pos.add(to.normalize().scale(OUT_SPEED));
                if (to.length() < 1.5 || (target == null && next.distanceTo(start) >= FREE_RANGE)) phase(LINGER);
            }
            case LINGER -> {
                // circling and slashing round the target (or hanging in the air where it ran out)
                if (target != null) {
                    double a = (tickCount - phaseStart) * 0.45;
                    next = target.position().add(Math.cos(a) * 1.3, target.getBbHeight() * 0.6, Math.sin(a) * 1.3);
                    if ((tickCount - phaseStart) % 10 == 0) hit.clear();
                } else next = pos;
                if (tickCount - phaseStart > LINGER_TIME) phase(BACK);
            }
            default -> {
                Vec3 to = owner.position().add(0, 1.1, 0).subtract(pos);
                if (to.length() < 1.4) { home(owner); return; }
                next = pos.add(to.normalize().scale(BACK_SPEED));
            }
        }
        setDeltaMovement(next.subtract(pos));
        setPos(next);
        cut(owner);
        if (tickCount % 4 == 0) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.4F, 1.6F);
            ((ServerLevel) level()).sendParticles(new DustParticleOptions(new Vector3f(0.75F, 0.8F, 0.9F), 0.8F), getX(), getY(), getZ(), 2, 0.2, 0.2, 0.2, 0);
        }
    }

    private void cut(ServerPlayer owner) {
        float dmg = (float) Math.max(3.0, owner.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.9);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(position(), position()).inflate(1.1))) {
            if (e == owner || !e.isAlive() || !hit.add(e.getId())) continue;
            e.hurt(owner.damageSources().playerAttack(owner), dmg);
        }
    }

    /** Back in his hand. */
    private void home(ServerPlayer owner) {
        com.xenowarriors.mikiko.RopeBlade.returned(owner, this);
        discard();
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {}

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
