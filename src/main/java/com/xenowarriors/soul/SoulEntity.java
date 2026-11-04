package com.xenowarriors.soul;

import com.xenowarriors.character.CharacterType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

/**
 * A split-off soul (Soul Splitter): looks like its owner but pale and glowing, stays by them and fights what they fight.
 * Rhalix's soul also throws his gravity and wire attacks once his Soul Splitter is high enough.
 * If the owner gets too far away it fades and finds its way back to their body.
 */
public class SoulEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> CHAR = SynchedEntityData.defineId(SoulEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(SoulEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    public int soulLevel;
    private int castCd = 40;

    public SoulEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.34)
                .add(Attributes.ATTACK_DAMAGE, 3).add(Attributes.FOLLOW_RANGE, 32);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(CHAR, CharacterType.RHALIX.getId());
        entityData.define(OWNER, Optional.empty());
    }

    public CharacterType character() { return CharacterType.byId(entityData.get(CHAR)); }
    public void setCharacter(CharacterType t) { entityData.set(CHAR, t.getId()); }
    public UUID ownerId() { return entityData.get(OWNER).orElse(null); }
    public void setOwner(UUID id) { entityData.set(OWNER, Optional.ofNullable(id)); }
    public ServerPlayer owner() { return level() instanceof ServerLevel s && ownerId() != null ? (ServerPlayer) s.getPlayerByUUID(ownerId()) : null; }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.35, true));
        goalSelector.addGoal(4, new Follow());
        targetSelector.addGoal(1, new GuardTarget());
    }

    class Follow extends Goal {
        Follow() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() { ServerPlayer o = owner(); return o != null && getTarget() == null && distanceToSqr(o) > 9; }
        @Override public void tick() {
            ServerPlayer o = owner();
            if (o == null) return;
            getLookControl().setLookAt(o, 30, 30);
            if (distanceToSqr(o) > 20 * 20) teleportTo(o.getX() + random.nextGaussian(), o.getY(), o.getZ() + random.nextGaussian());
            else getNavigation().moveTo(o, 1.3);
        }
    }

    /** Whatever hurt the owner, whatever the owner hit, else the nearest monster close by. */
    class GuardTarget extends TargetGoal {
        private LivingEntity pick;
        GuardTarget() { super(SoulEntity.this, false); setFlags(EnumSet.of(Flag.TARGET)); }
        @Override public boolean canUse() {
            ServerPlayer o = owner();
            if (o == null) return false;
            pick = null;
            LivingEntity a = o.getLastHurtByMob(), b = o.getLastHurtMob();
            if (ok(a, o)) pick = a; else if (ok(b, o)) pick = b;
            else {
                double best = 12 * 12;
                for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, o.getBoundingBox().inflate(12), x -> x instanceof Enemy)) {
                    double d = e.distanceToSqr(o);
                    if (d < best && ok(e, o)) { best = d; pick = e; }
                }
            }
            return pick != null;
        }
        private boolean ok(LivingEntity e, ServerPlayer o) {
            return e != null && e.isAlive() && e != SoulEntity.this && e != o && !com.xenowarriors.combat.Allies.isAlly(SoulEntity.this, e)    // (never the owner's side)
                    && e.distanceToSqr(o) < 24 * 24;
        }
        @Override public void start() { setTarget(pick); super.start(); }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level() instanceof ServerLevel l) {
            ServerPlayer o = owner();
            if (o == null || o.level() != level() || o.isDeadOrDying()) { SoulSplit.soulGone(this, true); return; }
            if (distanceToSqr(o) > SoulSplit.LEASH * SoulSplit.LEASH) { SoulSplit.soulGone(this, true); return; }
            if (tickCount % 4 == 0) l.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 1.0, getZ(), 1, 0.25, 0.5, 0.25, 0.005);
            if (character() == CharacterType.RHALIX && getTarget() != null && --castCd <= 0) { castCd = 70; RhalixSoulArts.cast(this, getTarget(), soulLevel); }
            fly(o);
        }
    }

    private int flyHitCd;
    private boolean soulFlying;

    /** Whether the soul is flying right now (tests). */
    public boolean soulFlying() { return soulFlying; }

    /**
     * Its owner can fly (creative flight, DragonMineZ flight, Rhalix's wings, an elytra, any mod's flight): so can the
     * soul. It flies while its owner is up in the air, or to reach something in the air that it's fighting - rising,
     * hovering and coming down with them - and walks again once they've landed.
     */
    private void fly(ServerPlayer o) {
        boolean ownerUp = com.xenowarriors.bot.PartyBots.airborne(o);
        boolean ownerCan = ownerUp || o.getAbilities().mayfly || o.isFallFlying();
        LivingEntity t = getTarget();
        boolean targetUp = t != null && t.isAlive() && !t.onGround() && level().clip(new net.minecraft.world.level.ClipContext(t.position(), t.position().subtract(0, 2.5, 0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.ANY, t)).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
        boolean want = ownerCan && (ownerUp || targetUp);
        if (!want) {
            if (soulFlying) { soulFlying = false; setNoGravity(false); }
            return;
        }
        soulFlying = true;
        setNoGravity(true);
        fallDistance = 0;
        getNavigation().stop();
        Vec3 goal;
        if (t != null && t.isAlive()) {
            goal = t.position().add(0, t.getBbHeight() * 0.3, 0);
            if (distanceToSqr(t) < 2.6 * 2.6 && --flyHitCd <= 0) { flyHitCd = 20; doHurtTarget(t); swing(net.minecraft.world.InteractionHand.MAIN_HAND); }
        } else {
            Vec3 side = Vec3.directionFromRotation(0, o.getYRot() + 90F).scale(1.8);
            goal = o.position().add(side).add(0, 0.3, 0);
        }
        Vec3 d = goal.subtract(position());
        double dist = d.length();
        if (dist > 40) { teleportTo(o.getX(), o.getY(), o.getZ()); return; }
        double speed = dist > 10 ? 0.9 : dist > 4 ? 0.55 : 0.25;
        Vec3 v = dist < 1.2 ? Vec3.ZERO : d.normalize().scale(speed);
        setDeltaMovement(getDeltaMovement().scale(0.5).add(v.scale(0.5)));
        getLookControl().setLookAt(t != null ? t : o);
        if (dist > 1.2) { float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z)); setYRot(yaw); yBodyRot = yaw; }
    }

    @Override
    public boolean causeFallDamage(float dist, float mult, DamageSource src) {
        return !soulFlying && super.causeFallDamage(dist, mult, src);
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity t) {
        boolean hit = super.doHurtTarget(t);
        if (hit && level() instanceof ServerLevel l) l.sendParticles(ParticleTypes.SOUL, t.getX(), t.getY() + 1, t.getZ(), 4, 0.2, 0.3, 0.2, 0.02);
        return hit;
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (src.getEntity() != null && src.getEntity().getUUID().equals(ownerId())) return false;
        return super.hurt(src, amount);
    }

    @Override
    public void die(DamageSource src) {
        super.die(src);
        if (!level().isClientSide) SoulSplit.soulGone(this, false);
    }

    @Override protected void dropAllDeathLoot(DamageSource src) {}
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean canBeLeashed(Player p) { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); }

    public Vec3 eye() { return getEyePosition(); }
}
