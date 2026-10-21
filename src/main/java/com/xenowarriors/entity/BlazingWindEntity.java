package com.xenowarriors.entity;

import com.xenowarriors.damage.ModDamageTypes;
import com.xenowarriors.skill.SkillEvents;
import com.xenowarriors.skill.SkillTargeting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.Optional;
import java.util.UUID;

/**
 * Mikiko's ultimate, Blazing Wind: a giant tornado of fire. It forms in front of him (cutscene),
 * he sends it off, and it rolls across the land dragging in everyone nearby, lifting and burning
 * them, until it bursts apart and flings them out.
 *   0-44   FORM   - wind gathers, the tornado spins up in front of him
 *   44-54  SEND   - he thrusts it forward
 *   54-260 TRAVEL - it moves off, pulling everything in
 *   260    BURST
 */
public class BlazingWindEntity extends Entity {
    public static final int SEND = 44, MOVE = 54, BURST = 260, END = 275, CUTSCENE_END = 104;
    public static final float SPEED = 0.22F, MAX_HEIGHT = 16F, MAX_RADIUS = 4.2F;
    public static final double PULL_RADIUS = 26, HURT_RADIUS = 4.8;
    public static final float DAMAGE = 2.5F, BURST_DAMAGE = 5F;

    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(BlazingWindEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(BlazingWindEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DIR_X = SynchedEntityData.defineId(BlazingWindEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_Z = SynchedEntityData.defineId(BlazingWindEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> START_X = SynchedEntityData.defineId(BlazingWindEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> START_Y = SynchedEntityData.defineId(BlazingWindEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> START_Z = SynchedEntityData.defineId(BlazingWindEntity.class, EntityDataSerializers.FLOAT);

    private double groundY;

    public BlazingWindEntity(EntityType<? extends BlazingWindEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static BlazingWindEntity create(ServerPlayer owner) {
        BlazingWindEntity e = new BlazingWindEntity(ModEntities.BLAZING_WIND.get(), owner.level());
        float yaw = owner.getYRot() * ((float) Math.PI / 180F);
        e.entityData.set(OWNER, Optional.of(owner.getUUID()));
        e.entityData.set(OWNER_ID, owner.getId());
        e.entityData.set(DIR_X, -Mth.sin(yaw));
        e.entityData.set(DIR_Z, Mth.cos(yaw));
        e.entityData.set(START_X, (float) owner.getX());
        e.entityData.set(START_Y, (float) owner.getY());
        e.entityData.set(START_Z, (float) owner.getZ());
        e.groundY = owner.getY();
        e.setPos(e.centerAt(0));
        return e;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(OWNER, Optional.empty());
        entityData.define(OWNER_ID, -1);
        entityData.define(DIR_X, 0F);
        entityData.define(DIR_Z, 1F);
        entityData.define(START_X, 0F);
        entityData.define(START_Y, 0F);
        entityData.define(START_Z, 0F);
    }

    public int ownerId() { return entityData.get(OWNER_ID); }
    public Vec3 dir() { return new Vec3(entityData.get(DIR_X), 0, entityData.get(DIR_Z)); }
    public Vec3 start() { return new Vec3(entityData.get(START_X), entityData.get(START_Y), entityData.get(START_Z)); }

    /** How far in front of Mikiko the tornado is at a moment in time. */
    public float distance(float age) {
        float push = age < SEND ? 0F : age < MOVE ? smooth((age - SEND) / (MOVE - SEND)) * 2.5F : 2.5F + (Math.min(age, BURST) - MOVE) * SPEED;
        return 4.5F + push;
    }

    public Vec3 centerAt(float age) {
        Vec3 s = start();
        Vec3 d = dir().scale(distance(age));
        return new Vec3(s.x + d.x, level().isClientSide ? getY() : groundY, s.z + d.z);
    }

    /** Height and radius of the funnel at a moment (it spins up, then shrinks away when it bursts). */
    public float height(float age) {
        float grow = smooth(age / (SEND - 4F));
        float fade = age > BURST ? 1F - smooth((age - BURST) / (END - BURST)) : 1F;
        return MAX_HEIGHT * grow * fade;
    }

    public float radius(float age) {
        float grow = smooth((age - 4F) / (SEND - 4F));
        float burst = age > BURST ? 1F + (age - BURST) / (END - BURST) * 1.5F : 1F;
        return Math.max(0.3F, MAX_RADIUS * grow) * burst;
    }

    public static float smooth(float x) {
        x = Math.max(0F, Math.min(1F, x));
        return x * x * (3F - 2F * x);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
            return;
        }
        ServerLevel server = (ServerLevel) level();
        Player p = entityData.get(OWNER).map(server::getPlayerByUUID).orElse(null);
        if (tickCount > END || p == null) {
            discard();
            return;
        }
        if (tickCount == 1) p.getPersistentData().putLong(SkillEvents.ULT_UNTIL, server.getGameTime() + CUTSCENE_END);
        // follow the ground
        Vec3 c = centerAt(tickCount);
        groundY = ground(server, c.x, groundY, c.z);
        setPos(c.x, groundY, c.z);

        float h = height(tickCount), r = radius(tickCount);
        if (tickCount % 16 == 0) server.playSound(null, getX(), getY() + 3, getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.5F, 0.5F);
        if (tickCount % 20 == 0) server.playSound(null, getX(), getY() + 3, getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.35F, 0.6F);
        if (tickCount % 30 == 0) server.playSound(null, getX(), getY() + 3, getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.35F, 0.4F);
        if (tickCount == SEND) {
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.35F, 1.6F);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.45F, 0.6F);
        }

        // drag everyone nearby in, lift and spin them, burn whoever is inside
        if (tickCount >= 24 && tickCount <= BURST) {
            AABB box = new AABB(getX() - PULL_RADIUS, getY() - 3, getZ() - PULL_RADIUS, getX() + PULL_RADIUS, getY() + h + 4, getZ() + PULL_RADIUS);
            for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, box, e -> p instanceof ServerPlayer sp && SkillTargeting.canTarget(sp, e))) {
                Vec3 to = new Vec3(getX() - e.getX(), 0, getZ() - e.getZ());
                double dist = to.length();
                if (dist > PULL_RADIUS || dist < 1.0E-3) continue;
                Vec3 in = to.scale(1 / dist);
                Vec3 swirl = new Vec3(-in.z, 0, in.x);
                double pull = 0.2 + 0.35 * (1 - dist / PULL_RADIUS);     // a strong, hungry vortex
                double lift = dist < r + 1.5 ? 0.16 : 0;
                Vec3 v = e.getDeltaMovement().add(in.scale(pull)).add(swirl.scale(dist < r + 2 ? 0.2 : 0.08));
                double top = getY() + h * 0.7;
                v = new Vec3(v.x * 0.9, e.getY() > top ? Math.min(v.y, 0) : Math.max(v.y, lift > 0 ? lift : v.y), v.z * 0.9);
                e.setDeltaMovement(v);
                e.hurtMarked = true;
                e.fallDistance = 0;
                if (dist < HURT_RADIUS && tickCount % 10 == 0) {
                    e.invulnerableTime = 0;
                    e.hurt(ModDamageTypes.source(server, ModDamageTypes.BLAZING_WIND, this, p), DAMAGE);
                    e.setSecondsOnFire(4);
                }
            }
        }
        // the tornado bursts apart and flings everyone out
        if (tickCount == BURST) {
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6F, 0.5F);
            server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 2, getZ(), 1, 0, 0, 0, 0);
            server.sendParticles(ParticleTypes.FLAME, getX(), getY() + 3, getZ(), 120, 3, 3, 3, 0.3);
            for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(8, h, 8),
                    e -> p instanceof ServerPlayer sp && SkillTargeting.canTarget(sp, e))) {
                Vec3 out = new Vec3(e.getX() - getX(), 0, e.getZ() - getZ());
                out = out.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : out.normalize();
                e.invulnerableTime = 0;
                e.hurt(ModDamageTypes.source(server, ModDamageTypes.BLAZING_WIND, this, p), BURST_DAMAGE);
                e.setDeltaMovement(out.x * 1.3, 0.6, out.z * 1.3);
                e.hurtMarked = true;
            }
        }
    }

    private static double ground(Level level, double x, double y, double z) {
        BlockPos.MutableBlockPos pos = BlockPos.containing(x, y + 4, z).mutable();
        for (int i = 0; i < 12; i++) {
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return pos.getY() + 1;
            pos.move(0, -1, 0);
        }
        return y;
    }

    private void clientTick() {
        float h = height(tickCount), r = radius(tickCount);
        if (h < 0.5F) return;
        // embers and fire spiralling up the funnel, smoke at the top, dust swept along the ground
        for (int i = 0; i < 10; i++) {
            float k = random.nextFloat();
            double y = k * h;
            double rr = r * (0.3 + 0.7 * Math.pow(k, 1.2)) * (0.85 + random.nextFloat() * 0.3);
            double a = random.nextFloat() * Math.PI * 2;
            double x = getX() + Math.cos(a) * rr, z = getZ() + Math.sin(a) * rr;
            double t = 0.35;
            level().addParticle(i % 3 == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME, x, getY() + y, z,
                    -Math.sin(a) * t, 0.12, Math.cos(a) * t);
        }
        if (tickCount % 2 == 0) {
            double a = random.nextFloat() * Math.PI * 2;
            level().addParticle(ParticleTypes.LARGE_SMOKE, getX() + Math.cos(a) * r, getY() + h, getZ() + Math.sin(a) * r, 0, 0.1, 0);
            level().addParticle(ParticleTypes.CLOUD, getX() + Math.cos(a) * (r + 3), getY() + 0.2, getZ() + Math.sin(a) * (r + 3),
                    -Math.cos(a) * 0.3, 0.05, -Math.sin(a) * 0.3);
        }
        // wind gathering around Mikiko while it forms
        if (tickCount < SEND) {
            Entity o = level().getEntity(ownerId());
            if (o != null) {
                for (int i = 0; i < 4; i++) {
                    double a = (tickCount * 0.5 + i * Math.PI / 2);
                    double rr = 2.5 - tickCount / (float) SEND * 1.5;
                    level().addParticle(ParticleTypes.CLOUD, o.getX() + Math.cos(a) * rr, o.getY() + 0.3 + i * 0.4, o.getZ() + Math.sin(a) * rr, 0, 0.02, 0);
                    level().addParticle(ParticleTypes.SMALL_FLAME, o.getX() + Math.cos(a + 1) * rr, o.getY() + 0.6 + i * 0.35, o.getZ() + Math.sin(a + 1) * rr, 0, 0.01, 0);
                }
            }
        }
    }

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) { return dist < 160 * 160; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
