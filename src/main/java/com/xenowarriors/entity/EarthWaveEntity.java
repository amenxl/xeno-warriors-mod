package com.xenowarriors.entity;

import com.xenowarriors.damage.ModDamageTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Rhalix's Earth Wave: after his stomp lands, a wall of earth rolls out along the ground in the
 * direction he was facing, climbing one-block steps and dropping down ledges, and crashing to a stop
 * against taller walls. Everything it runs into (except Rhalix) takes damage and gets launched.
 * Purely visual - it never breaks or moves real blocks.
 */
public class EarthWaveEntity extends Entity {
    // ---- Tuning ----
    public static final float RANGE = 18F;            // blocks
    public static final float SPEED = 0.85F;          // blocks per tick
    public static final float DAMAGE = 7F;
    public static final float START_HALF_WIDTH = 1.5F; // 3 wide at Rhalix's feet...
    public static final float END_HALF_WIDTH = 2.5F;   // ...5 wide at the end
    public static final int FADE = 10;
    /** Ticks after the skill is used that Rhalix's foot hits the ground (matches his stomp animation). */
    public static final int IMPACT = 7;                // ticks the wave takes to sink after stopping

    private static final EntityDataAccessor<Float> START_X = SynchedEntityData.defineId(EarthWaveEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> START_Z = SynchedEntityData.defineId(EarthWaveEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_X = SynchedEntityData.defineId(EarthWaveEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_Z = SynchedEntityData.defineId(EarthWaveEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> STOP_AT = SynchedEntityData.defineId(EarthWaveEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> STOP_TICK = SynchedEntityData.defineId(EarthWaveEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(EarthWaveEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private final Set<Integer> hit = new HashSet<>();
    private int groundY;
    private float traveled;

    public EarthWaveEntity(EntityType<? extends EarthWaveEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static EarthWaveEntity create(Level level, Player owner) {
        EarthWaveEntity wave = new EarthWaveEntity(ModEntities.EARTH_WAVE.get(), level);
        float yaw = owner.getYRot() * ((float) Math.PI / 180F);
        float dx = -(float) Math.sin(yaw), dz = (float) Math.cos(yaw);
        wave.entityData.set(START_X, (float) owner.getX());
        wave.entityData.set(START_Z, (float) owner.getZ());
        wave.entityData.set(DIR_X, dx);
        wave.entityData.set(DIR_Z, dz);
        wave.entityData.set(OWNER, Optional.of(owner.getUUID()));
        wave.groundY = groundBelow(level, BlockPos.containing(owner.getX(), owner.getY(), owner.getZ()));
        wave.setPos(owner.getX(), wave.groundY + 1, owner.getZ());
        wave.setYRot(owner.getYRot());
        return wave;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(START_X, 0F);
        entityData.define(START_Z, 0F);
        entityData.define(DIR_X, 0F);
        entityData.define(DIR_Z, 1F);
        entityData.define(STOP_AT, -1F);
        entityData.define(STOP_TICK, -1);
        entityData.define(OWNER, Optional.empty());
    }

    public float startX() { return entityData.get(START_X); }
    public float startZ() { return entityData.get(START_Z); }
    public float dirX() { return entityData.get(DIR_X); }
    public float dirZ() { return entityData.get(DIR_Z); }
    public float stopAt() { return entityData.get(STOP_AT); }
    public int stopTick() { return entityData.get(STOP_TICK); }
    public UUID getOwnerId() { return entityData.get(OWNER).orElse(null); }

    /** How far the wave front has gone, smoothly between ticks (same on client and server). */
    public float traveled(float partialTick) {
        float stop = stopAt();
        if (stop >= 0F) return stop;
        float age = tickCount + partialTick - IMPACT;
        return Math.max(0F, Math.min(RANGE, age * SPEED));
    }

    public static float halfWidth(float traveled) {
        return START_HALF_WIDTH + (END_HALF_WIDTH - START_HALF_WIDTH) * Math.min(1F, traveled / RANGE);
    }

    /** Solid ground the wave can roll over. */
    public static boolean isGround(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && state.getRenderShape() == RenderShape.MODEL && state.getFluidState().isEmpty()
                && state.isCollisionShapeFullBlock(level, pos);
    }

    private static int groundBelow(Level level, BlockPos feet) {
        for (int dy = 0; dy <= 3; dy++) {
            if (isGround(level, feet.below(dy + 1))) return feet.getY() - dy - 1;
        }
        return feet.getY() - 1;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        Player owner = getOwnerId() == null ? null : level().getPlayerByUUID(getOwnerId());
        ServerLevel server = (ServerLevel) level();

        int stopTick = stopTick();
        if (stopTick >= 0) {
            if (tickCount - stopTick > FADE) discard();
            return;
        }
        if (tickCount < IMPACT) return;
        if (tickCount == IMPACT) {
            // the stomp lands
            BlockPos under = BlockPos.containing(startX(), groundY, startZ());
            server.playSound(null, startX(), groundY + 1, startZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7F, 0.55F);
            server.playSound(null, startX(), groundY + 1, startZ(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.5F, 0.6F);
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, server.getBlockState(under)),
                    startX(), groundY + 1.05, startZ(), 40, 1.2, 0.1, 1.2, 0.2);
        }

        // advance the front, following the ground
        float next = traveled(0F);
        Vec3 front = frontAt(next);
        BlockPos col = BlockPos.containing(front.x, groundY, front.z);
        boolean blocked = false;
        if (isGround(server, col.above())) {                      // step up
            if (isGround(server, col.above(2)) || isGround(server, col.above(3))) blocked = true;
            else groundY += 1;
        } else if (!isGround(server, col)) {                       // step down a ledge
            int drop = 1;
            while (drop <= 3 && !isGround(server, col.below(drop))) drop++;
            if (drop > 3) blocked = true;                          // nothing to roll on
            else groundY -= drop;
        }
        if (blocked || next >= RANGE || owner == null) {
            stop(server, traveled, owner == null);
            return;
        }
        traveled = next;
        setPos(front.x, groundY + 1, front.z);

        // rubble, rumble
        BlockState surface = server.getBlockState(BlockPos.containing(front.x, groundY, front.z));
        float half = halfWidth(traveled);
        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, surface),
                front.x, groundY + 1.4, front.z, 14, half * 0.6, 0.5, half * 0.6, 0.15);
        if (tickCount % 3 == 0) {
            server.playSound(null, front.x, groundY + 1, front.z, SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 1.2F, 0.5F + random.nextFloat() * 0.2F);
            server.playSound(null, front.x, groundY + 1, front.z, SoundEvents.BASALT_BREAK, SoundSource.PLAYERS, 0.9F, 0.6F);
        }

        hitEntities(server, owner, front, half);
    }

    private Vec3 frontAt(float dist) {
        return new Vec3(startX() + dirX() * dist, groundY + 1, startZ() + dirZ() * dist);
    }

    private void stop(ServerLevel server, float at, boolean quiet) {
        entityData.set(STOP_AT, at);
        entityData.set(STOP_TICK, tickCount);
        if (quiet) return;
        Vec3 front = frontAt(at);
        server.playSound(null, front.x, front.y, front.z, SoundEvents.DRIPSTONE_BLOCK_BREAK, SoundSource.PLAYERS, 1.5F, 0.5F);
        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, server.getBlockState(BlockPos.containing(front.x, groundY, front.z))),
                front.x, front.y + 1, front.z, 30, 1.5, 0.8, 1.5, 0.2);
    }

    private void hitEntities(ServerLevel server, Player owner, Vec3 front, float half) {
        double dx = dirX(), dz = dirZ();
        AABB box = new AABB(front.x - half - 2, front.y - 1, front.z - half - 2, front.x + half + 2, front.y + 3, front.z + half + 2);
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != owner)) {
            if (target instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            if (hit.contains(target.getId())) continue;
            double rx = target.getX() - front.x, rz = target.getZ() - front.z;
            double along = rx * dx + rz * dz;            // in front of / behind the crest
            double side = Math.abs(rx * -dz + rz * dx);   // distance from the wave's center line
            if (along < -1.8 || along > 1.0 || side > half + 0.6) continue;
            hit.add(target.getId());
            target.hurt(ModDamageTypes.earthWave(server, this, owner), DAMAGE);
            target.setDeltaMovement(dx * 0.9, 0.75, dz * 0.9);
            target.hurtMarked = true;
        }
    }

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) { return dist < 96 * 96; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
