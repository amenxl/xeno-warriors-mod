package com.xenowarriors.corrupted;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The C-Soul: a huge corrupted soul, the thing the smaller souls gather around. It hovers over its prey and
 * throws volleys of soul bolts, calls corrupted souls to its side, and shrieks to blast back anyone who gets
 * close. At half health it becomes enraged - faster, more bolts, more souls.
 */
public class CSoulBossEntity extends Monster {
    private int boltCooldown = 60, summonCooldown = 160, shriekCooldown = 100, meleeCooldown;
    private boolean enraged;

    public CSoulBossEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 10, true);
        this.setNoGravity(true);
        this.xpReward = 150;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 260.0)
                .add(Attributes.FLYING_SPEED, 0.5)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.ATTACK_DAMAGE, 9.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanFloat(true);
        return nav;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(8, new WaterAvoidingRandomFlyingGoal(this, 0.6));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 24F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        if (!enraged && getHealth() < getMaxHealth() * 0.5F) {
            enraged = true;
            playSound(SoundEvents.WITHER_SPAWN, 2F, 1.4F);
            level.sendParticles(CorruptedFx.BLACK, getX(), getY() + 2, getZ(), 40, 1.2, 1.2, 1.2, 0.1);
            getAttribute(Attributes.FLYING_SPEED).setBaseValue(0.75);
        }
        LivingEntity target = getTarget();
        int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, net.minecraft.util.Mth.floor(getX()),
                net.minecraft.util.Mth.floor(getZ()));
        if (target == null || !target.isAlive()) {
            // nothing to hunt: settles at one height instead of drifting ever upward
            if (getY() > ground + 6) setDeltaMovement(getDeltaMovement().multiply(1, 0.5, 1).add(0, -0.05, 0));
            return;
        }
        getLookControl().setLookAt(target, 30F, 30F);

        // hover above the target, slowly circling
        double a = tickCount * (enraged ? 0.05 : 0.03);
        double r = distanceTo(target) < 6 ? 7 : 5;
        // over something that can fly it keeps above it; over someone who can't, it stays low enough to be hit
        boolean targetFlies = (target instanceof Player tp && (tp.getAbilities().flying || tp.getAbilities().mayfly)) || target.isNoGravity()
                || target.isFallFlying();
        double lift = targetFlies ? 3.5 : 0.8;
        Vec3 want = target.position().add(Math.cos(a) * r, lift, Math.sin(a) * r);
        if (!targetFlies) want = new Vec3(want.x, Math.min(want.y, Math.max(ground, target.getY()) + 1.2), want.z);
        moveControl.setWantedPosition(want.x, want.y, want.z, enraged ? 1.4 : 1.0);

        double dist = distanceToSqr(target);
        if (meleeCooldown > 0) meleeCooldown--;
        if (dist < 9 && meleeCooldown == 0) {
            doHurtTarget(target);
            meleeCooldown = 25;
        }
        if (--boltCooldown <= 0 && hasLineOfSight(target)) {
            int n = enraged ? 5 : 3;
            for (int i = 0; i < n; i++) {
                Vec3 eye = position().add(0, getBbHeight() * 0.7, 0);
                Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(eye);
                double spread = (i - (n - 1) / 2.0) * 0.12;
                Vec3 dir = to.normalize().yRot((float) spread);
                SoulBoltEntity bolt = new SoulBoltEntity(level, this, dir.x, dir.y, dir.z);
                bolt.setPos(eye.x + dir.x * 1.5, eye.y, eye.z + dir.z * 1.5);
                level.addFreshEntity(bolt);
            }
            playSound(SoundEvents.BLAZE_SHOOT, 1.5F, 0.5F);
            boltCooldown = enraged ? 35 : 55;
        }
        if (--summonCooldown <= 0) {
            int nearby = level.getEntitiesOfClass(CorruptedSoulEntity.class, getBoundingBox().inflate(24)).size();
            int count = Math.min(enraged ? 3 : 2, 6 - nearby);
            for (int i = 0; i < count; i++) {
                CorruptedSoulEntity soul = ModCorrupted.CORRUPTED_SOUL.get().create(level);
                if (soul == null) continue;
                soul.moveTo(getX() + random.nextGaussian() * 2, getY() + 1, getZ() + random.nextGaussian() * 2, random.nextFloat() * 360, 0);
                soul.setTarget(target);
                level.addFreshEntity(soul);
                level.sendParticles(CorruptedFx.BLACK, soul.getX(), soul.getY(), soul.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
            }
            if (count > 0) playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 1.5F, 0.6F);
            summonCooldown = enraged ? 180 : 260;
        }
        if (--shriekCooldown <= 0 && dist < 36) {
            shriek(level);
            shriekCooldown = enraged ? 120 : 180;
        }
    }

    /** A screaming blast: knocks everyone near away and blinds them with darkness. */
    private void shriek(ServerLevel level) {
        playSound(SoundEvents.WARDEN_SONIC_BOOM, 2F, 1.3F);
        level.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getY() + getBbHeight() * 0.5, getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(CorruptedFx.BLACK, getX(), getY() + 1, getZ(), 40, 2.5, 1, 2.5, 0.1);
        for (Player p : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(7))) {
            if (p.isCreative() || p.isSpectator()) continue;
            Vec3 push = p.position().subtract(position()).normalize();
            p.push(push.x * 1.6, 0.6, push.z * 1.6);
            p.hurtMarked = true;
            p.hurt(damageSources().mobAttack(this), 6F);
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0), this);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            for (int i = 0; i < (enraged() ? 4 : 2); i++) {
                level().addParticle(ParticleTypes.WITCH, getRandomX(1.0), getY() + random.nextDouble() * getBbHeight(), getRandomZ(1.0), 0, 0, 0);
            }
            if (random.nextInt(3) == 0) {
                level().addParticle(CorruptedFx.DARK, getRandomX(1.2), getY() + random.nextDouble() * 1.5, getRandomZ(1.2), 0, 0.03, 0);
            }
        }
    }

    private boolean enraged() {
        return getHealth() < getMaxHealth() * 0.5F;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // its own bolts and souls don't hurt it
        if (source.getEntity() instanceof CorruptedSoulEntity || source.getDirectEntity() instanceof SoulBoltEntity) return false;
        return super.hurt(source, amount);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return !getPersistentData().getBoolean(com.xenowarriors.s3.DistortionEntity.TAG) && super.removeWhenFarAway(distance);   // (wild ones despawn like any monster)
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {}

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SCULK_SHRIEKER_SHRIEK;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WITHER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WITHER_DEATH;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 220;
    }

    @Override
    public float getVoicePitch() {
        return 0.5F;
    }

    @Override
    protected float getSoundVolume() {
        return 2F;
    }
}
