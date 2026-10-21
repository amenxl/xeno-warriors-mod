package com.xenowarriors.entity;

import com.xenowarriors.client.ClientSkillState;
import com.xenowarriors.damage.ModDamageTypes;
import com.xenowarriors.skill.Rooting;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkHooks;

import java.util.Optional;
import java.util.UUID;

/**
 * Rhalix's Gravity Compress: a gravity sphere slams down around the target, then closes in on
 * them, crushing harder and harder. The target can't move or jump the whole time. It ends with
 * an implosion.
 */
public class GravityCompressEntity extends Entity {
    // ---- Timeline (ticks) and tuning ----
    public static final int FORM = 6;        // sphere slams down around the target
    public static final int CLOSED = 30;     // finished closing in
    public static final int IMPLODE = 74;    // final crush
    public static final int END = 84;
    public static final float START_RADIUS = 3.4F;
    private static final float[] CRUSH = {2.5F, 3F, 3.5F, 4F, 4.5F};   // every 9 ticks while closed
    private static final float IMPLODE_DAMAGE = 5F;

    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(GravityCompressEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> TIGHT_RADIUS = SynchedEntityData.defineId(GravityCompressEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(GravityCompressEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private Vec3 anchor;

    public GravityCompressEntity(EntityType<? extends GravityCompressEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static GravityCompressEntity create(Level level, Player owner, LivingEntity target) {
        GravityCompressEntity e = new GravityCompressEntity(ModEntities.GRAVITY_COMPRESS.get(), level);
        e.entityData.set(TARGET, target.getId());
        e.entityData.set(OWNER, Optional.of(owner.getUUID()));
        // hug the target: half its size plus a little room
        float tight = Math.max(target.getBbWidth(), target.getBbHeight()) * 0.55F + 0.25F;
        e.entityData.set(TIGHT_RADIUS, tight);
        e.anchor = target.position();
        e.setPos(target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ());
        return e;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(TARGET, -1);
        entityData.define(TIGHT_RADIUS, 1F);
        entityData.define(OWNER, Optional.empty());
    }

    public int targetId() { return entityData.get(TARGET); }
    public float tightRadius() { return entityData.get(TIGHT_RADIUS); }
    public UUID ownerId() { return entityData.get(OWNER).orElse(null); }

    public LivingEntity target() {
        return level().getEntity(targetId()) instanceof LivingEntity l ? l : null;
    }

    /** Sphere radius at a moment in time (same on both sides). */
    public float radius(float age) {
        float tight = tightRadius();
        if (age < FORM) return START_RADIUS * (1.4F - 0.4F * age / FORM);
        if (age < CLOSED) {
            float t = (age - FORM) / (CLOSED - FORM);
            float e = t * t * (3 - 2 * t);
            return Mth.lerp(e, START_RADIUS, tight);
        }
        if (age < IMPLODE) return tight * (1F - 0.05F * Mth.sin(age * 1.7F));
        return tight * Math.max(0F, 1F - (age - IMPLODE) / 3F);
    }

    /** 0..1 how hard the target is being squished (for their squash-and-shake). */
    public float squish(float age) {
        if (age < FORM + 8 || age > IMPLODE + 2) return 0F;
        return Mth.clamp((age - FORM - 8) / (CLOSED - FORM - 8F), 0F, 1F);
    }

    @Override
    public void tick() {
        super.tick();
        LivingEntity target = target();
        if (target != null) setPos(target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ());
        if (level().isClientSide) {
            clientTick(target);
            return;
        }
        Player owner = ownerId() == null ? null : level().getPlayerByUUID(ownerId());
        if (tickCount > END || target == null || !target.isAlive() || owner == null) {
            discard();
            return;
        }
        ServerLevel server = (ServerLevel) level();
        if (anchor == null) anchor = target.position();
        if (tickCount <= IMPLODE) Rooting.hold(target, anchor);

        if (tickCount == 1) {
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.4F, 0.5F);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6F, 0.4F);
        }
        if (tickCount > FORM && tickCount < CLOSED && tickCount % 5 == 0) {
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.8F,
                    0.5F + 0.5F * (tickCount - FORM) / (CLOSED - FORM));
        }
        if (tickCount >= CLOSED && tickCount < IMPLODE && (tickCount - CLOSED) % 9 == 0) {
            int hit = Math.min(CRUSH.length - 1, (tickCount - CLOSED) / 9);
            target.invulnerableTime = 0;
            target.hurt(ModDamageTypes.gravityCompress(server, this, owner), CRUSH[hit]);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 1.2F, 0.5F);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.6F, 0.5F);
        }
        if (tickCount == IMPLODE) {
            target.invulnerableTime = 0;
            target.hurt(ModDamageTypes.gravityCompress(server, this, owner), IMPLODE_DAMAGE);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 0.5F);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.2F, 0.6F);
            server.sendParticles(ParticleTypes.EXPLOSION, getX(), getY(), getZ(), 2, 0.3, 0.3, 0.3, 0);
            server.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY(), getZ(), 80, 0.2, 0.2, 0.2, 1.2);
        }
    }

    private void clientTick(LivingEntity target) {
        float r = radius(tickCount);
        if (target != null) {
            float s = squish(tickCount);
            if (s > 0F) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSkillState.markSquish(target.getId(), s));
        }
        if (tickCount < IMPLODE) {
            // particles getting sucked in from the shell
            for (int i = 0; i < 4; i++) {
                double u = random.nextDouble() * Math.PI * 2, v = Math.acos(2 * random.nextDouble() - 1);
                double x = Math.sin(v) * Math.cos(u), y = Math.cos(v), z = Math.sin(v) * Math.sin(u);
                double rr = r * 1.15;
                level().addParticle(ParticleTypes.REVERSE_PORTAL, getX() + x * rr, getY() + y * rr, getZ() + z * rr,
                        -x * 0.12, -y * 0.12, -z * 0.12);
            }
            if (tickCount % 3 == 0) {
                level().addParticle(ParticleTypes.END_ROD, getX() + (random.nextDouble() - 0.5) * r, getY() + r,
                        getZ() + (random.nextDouble() - 0.5) * r, 0, -0.25, 0);
            }
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
