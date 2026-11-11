package com.xenowarriors.boss;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Xeno Dragon's Dragon Bomb (its Spirit Bomb): a sphere of dark ender energy that grows over its raised hands
 * while it charges (radius up to {@link #MAX_R}), then is thrown at where its target stood - slow enough to get out
 * from under (a ring on the ground shows where it lands). Blast radius {@link #BLAST_R}, up to 4x its attack, no
 * blocks broken. If the dragon is stopped while charging, the bomb collapses harmlessly. Never saved.
 */
public class DragonBombEntity extends Entity {
    public static final float MAX_R = 5F, BLAST_R = 8F, SPEED = 0.38F;
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(DragonBombEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(DragonBombEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockPos> TARGET = SynchedEntityData.defineId(DragonBombEntity.class, EntityDataSerializers.BLOCK_POS);
    public static final int CHARGING = 0, THROWN = 1;
    private int ownerId = -1, targetId = -1, flying;
    private Vec3 aim;
    /** (For the tests) the last blast: how many it hit. */
    public static int lastHits = -1, explosions;
    /** (For the tests' stills) stays as it is. */
    public boolean testHold;

    public DragonBombEntity(EntityType<? extends DragonBombEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(RADIUS, 0.4F);
        entityData.define(STATE, CHARGING);
        entityData.define(TARGET, BlockPos.ZERO);
    }

    public float radius() { return entityData.get(RADIUS); }
    public boolean thrown() { return entityData.get(STATE) == THROWN; }
    public BlockPos targetPos() { return entityData.get(TARGET); }
    public void setRadiusForTest(float r) { entityData.set(RADIUS, r); }

    void start(XenoDragonEntity owner, LivingEntity target) {
        ownerId = owner.getId();
        targetId = target.getId();
        moveTo(owner.getX(), owner.getY() + 4.2, owner.getZ());
        entityData.set(TARGET, target.blockPosition());
    }

    void throwAt(Vec3 at) {
        aim = at;
        entityData.set(TARGET, BlockPos.containing(at));
        entityData.set(STATE, THROWN);
        flying = 0;
    }

    private XenoDragonEntity owner() { return level().getEntity(ownerId) instanceof XenoDragonEntity x ? x : null; }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || testHold) return;
        ServerLevel level = (ServerLevel) level();
        XenoDragonEntity o = owner();
        if (!thrown()) {
            if (o == null || !o.isAlive() || o.act() != XenoDragonEntity.ACT_BOMB) { collapse(); return; }
            float k = 1F - o.actTicks() / (float) XenoDragonEntity.BOMB_TICKS;
            float r = 0.4F + (MAX_R - 0.4F) * Math.min(1F, k);
            entityData.set(RADIUS, r);
            setPos(o.getX(), o.getY() + 3.6 + r, o.getZ());
            if (level.getEntity(targetId) instanceof LivingEntity t && t.isAlive()) entityData.set(TARGET, t.blockPosition());
            for (int i = 0; i < 5; i++) {                                         // energy streaming in from all around
                double a = random.nextDouble() * Math.PI * 2, b = random.nextDouble() * Math.PI - Math.PI / 2, d = r + 6 + random.nextDouble() * 6;
                double dx = Math.cos(a) * Math.cos(b), dy = Math.sin(b), dz = Math.sin(a) * Math.cos(b);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX() + dx * d, getY() + dy * d, getZ() + dz * d, 0, -dx, -dy, -dz, 0.6);
            }
            if (tickCount % 20 == 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 4F, 0.5F + k * 0.5F);
            return;
        }
        flying++;
        Vec3 to = aim == null ? position().add(0, -1, 0) : aim;
        Vec3 d = to.subtract(position());
        double r = radius();
        if (d.length() < Math.max(1.0, r * 0.5) || flying > 200) { explode(level); return; }
        Vec3 step = d.normalize().scale(Math.min(SPEED, d.length()));
        setPos(getX() + step.x, getY() + step.y, getZ() + step.z);
        BlockPos below = BlockPos.containing(getX(), getY() - r * 0.7, getZ());
        if (flying > 5 && !level.getBlockState(below).isAir()) { explode(level); return; }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r * 0.7), e -> e != o && o != null && o.enemy(e))) { explode(level); return; }
        level.sendParticles(ParticleTypes.DRAGON_BREATH, getX(), getY(), getZ(), 6, r * 0.5, r * 0.5, r * 0.5, 0.02);
        if (flying % 10 == 0) for (int i = 0; i < 24; i++) {                      // the landing ring
            double a = i * Math.PI / 12;
            level.sendParticles(ParticleTypes.DRAGON_BREATH, to.x + Math.cos(a) * BLAST_R, to.y + 0.2, to.z + Math.sin(a) * BLAST_R, 1, 0, 0, 0, 0);
        }
    }

    /** Dropped while charging: fizzles out. */
    void collapse() {
        if (level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY(), getZ(), 80, radius() * 0.5, radius() * 0.5, radius() * 0.5, 0.4);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 4F, 0.6F);
        }
        discard();
    }

    private void explode(ServerLevel level) {
        XenoDragonEntity o = owner();
        float atk = o != null ? (float) o.getAttributeValue(Attributes.ATTACK_DAMAGE) : 30F;
        level.sendParticles(ParticleTypes.FLASH, getX(), getY(), getZ(), 3, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY(), getZ(), 8, BLAST_R * 0.4, 1, BLAST_R * 0.4, 0);
        level.sendParticles(ParticleTypes.DRAGON_BREATH, getX(), getY(), getZ(), 300, BLAST_R * 0.5, 1.5, BLAST_R * 0.5, 0.15);
        level.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 80, 1, 1, 1, 0.6);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 8F, 0.5F);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 6F, 1.4F);
        int hits = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(BLAST_R + radius()), e -> e != o && e.isAlive() && (o == null || o.enemy(e)))) {
            double dist = Math.max(0, e.position().add(0, e.getBbHeight() / 2, 0).distanceTo(position()) - radius() * 0.5);
            if (dist > BLAST_R) continue;
            float dmg = atk * 4F * (float) (1 - 0.5 * dist / BLAST_R);
            e.invulnerableTime = 0;
            e.hurt(level.damageSources().explosion(this, o), dmg);
            Vec3 push = e.position().subtract(position()).multiply(1, 0, 1);
            if (push.lengthSqr() < 1e-4) push = new Vec3(1, 0, 0);
            push = push.normalize().scale(2.2 * (1 - dist / BLAST_R) + 0.4);
            e.setDeltaMovement(push.x, 0.9, push.z);
            e.hurtMarked = true;
            hits++;
        }
        lastHits = hits;
        explosions++;
        XenoDragons.note("dragon bomb exploded at " + blockPosition().toShortString() + " hit " + hits + " (atk " + Math.round(atk) + ")");
        discard();
    }

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    public boolean isPickable() { return false; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {
        return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);
    }
}
