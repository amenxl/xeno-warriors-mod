package com.xenowarriors.npc;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Skylanders: people with wings. They fly about high up (round the sky islands), and now and then come down to hang
 * out with people on the ground - where Skyhaters go for them and try to rip their wings off. A Skylander who loses
 * them never grows them back: a Wingless Skylander, grounded and miserable. Help one fight the Skyhaters off and they
 * thank you for it. Corrupted souls can't take a Skylander.
 */
public class SkylanderEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(SkylanderEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(SkylanderEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> WINGLESS = SynchedEntityData.defineId(SkylanderEntity.class, EntityDataSerializers.BOOLEAN);
    public static final String[] LOOKS = {"sky_scout", "sky_lexy", "sky_max", "sky_royal", "sky_patrol", "sky_kid"};
    private Vec3 flyTarget;
    private BlockPos landAt;
    private int groundTime, threatenedAt = -10000;
    private UUID helper;
    private int helpedAt = -10000;
    /** Sky island dungeon guardians: where their temple is (null = an ordinary Skylander). */
    private BlockPos guardHome;

    public SkylanderEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 22).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 2.5).add(Attributes.FOLLOW_RANGE, 24);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(VARIANT, 0);
        entityData.define(FLYING, false);
        entityData.define(WINGLESS, false);
    }

    public int variant() { return entityData.get(VARIANT); }
    public boolean flying() { return entityData.get(FLYING); }
    public boolean wingless() { return entityData.get(WINGLESS); }
    public String look() { return LOOKS[Math.floorMod(variant(), LOOKS.length)]; }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.1, true) {
            @Override public boolean canUse() { return !flying() && !wingless() && super.canUse(); }
        });
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, HumanNpcEntity.class, 8F, 1.0, 1.3, e -> ((HumanNpcEntity) e).skyHater()) {
            @Override public boolean canUse() { return !flying() && wingless() && super.canUse(); }
        });
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7) {
            @Override public boolean canUse() { return !flying() && super.canUse(); }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, SpawnGroupData data, CompoundTag tag) {
        entityData.set(VARIANT, level.getRandom().nextInt(LOOKS.length));
        SpawnGroupData r = super.finalizeSpawn(level, difficulty, reason, data, tag);
        setFlying(reason != MobSpawnType.SPAWN_EGG && reason != MobSpawnType.STRUCTURE);    // eggs / islands: start on their feet
        return r;
    }

    public boolean guardian() { return guardHome != null; }
    public BlockPos guardHome() { return guardHome; }

    /** Guards a sky temple: stays by it, tougher, and leaves people be unless they break things or open the chests. */
    public void makeGuardian(BlockPos home) {
        guardHome = home.immutable();
        restrictTo(guardHome, 12);
        var hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(40);
        var dmg = getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null) dmg.setBaseValue(6);
        setHealth(getMaxHealth());
        setCustomName(Component.literal("Skylander Guardian").withStyle(ChatFormatting.AQUA));
        setPersistenceRequired();
    }

    /** A guardian turns on someone who's raiding the temple. */
    public void defendAgainst(LivingEntity thief) {
        if (wingless()) return;
        if (flying()) setFlying(false);
        setTarget(thief);
        threatenedAt = tickCount;
    }

    public void setFlying(boolean f) {
        entityData.set(FLYING, f && !wingless());
        setNoGravity(flying());
        if (flying()) getNavigation().stop();
    }

    /** A Skyhater got them: the wings are gone for good. */
    public void loseWings(LivingEntity by) {
        if (wingless()) return;
        entityData.set(WINGLESS, true);
        setFlying(false);
        setCustomName(Component.literal("Wingless Skylander").withStyle(ChatFormatting.GRAY));
        setCustomNameVisible(true);
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(0.2);
        setTarget(null);
        if (level() instanceof ServerLevel s) {
            s.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1.3, getZ() + 0.3, 30, 0.5, 0.4, 0.5, 0.06);
            s.sendParticles(ParticleTypes.DAMAGE_INDICATOR, getX(), getY() + 1.3, getZ(), 8, 0.3, 0.3, 0.3, 0.1);
        }
        playSound(SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, 1F, 0.7F);
        playSound(SoundEvents.ITEM_BREAK, 1F, 0.6F);
        setPersistenceRequired();
    }

    /** A player hit a Skyhater who was after this one. */
    public void helpedBy(Player p) {
        helper = p.getUUID();
        helpedAt = tickCount;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof HumanNpcEntity h && h.skyHater()) threatenedAt = tickCount;
        boolean r = super.hurt(source, amount);
        // fighting back on the ground; now and then they get away into the air
        if (r && !level().isClientSide && guardian() && source.getEntity() instanceof LivingEntity attacker && !(attacker instanceof SkylanderEntity)) {
            // one guardian attacked: all of them come
            for (SkylanderEntity g : level().getEntitiesOfClass(SkylanderEntity.class, getBoundingBox().inflate(24), SkylanderEntity::guardian))
                if (g != this && g.getTarget() == null) g.defendAgainst(attacker);
        }
        if (r && !level().isClientSide && !guardian() && !flying() && !wingless() && random.nextFloat() < 0.15F && getHealth() > getMaxHealth() * 0.4F) takeOff();
        return r;
    }

    private void takeOff() {
        setFlying(true);
        flyTarget = position().add(random.nextGaussian() * 6, 14 + random.nextInt(10), random.nextGaussian() * 6);
        groundTime = 0;
        landAt = null;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            if (wingless() && random.nextInt(40) == 0) level().addParticle(ParticleTypes.FALLING_WATER, getX(), getEyeY(), getZ(), 0, 0, 0);
            return;
        }
        if (flying()) flyTick();
        else if (!wingless()) {
            if (guardian()) {
                // back to their post once the fight's over
                if (getTarget() == null && tickCount % 40 == 0 && distanceToSqr(Vec3.atCenterOf(guardHome)) > 14 * 14)
                    getNavigation().moveTo(guardHome.getX() + 0.5, guardHome.getY(), guardHome.getZ() + 0.5, 1.0);
            } else if (onGround() && ++groundTime > 2400 && tickCount - threatenedAt > 200) takeOff();     // done hanging out
        }
        // saved: once nobody's after them any more, they thank whoever helped
        if (helper != null && tickCount - threatenedAt > 100 && tickCount - helpedAt < 1200 && !wingless()) {
            boolean stillHunted = !level().getEntitiesOfClass(HumanNpcEntity.class, getBoundingBox().inflate(16), h -> h.skyHater() && h.getTarget() == this).isEmpty();
            if (!stillHunted && level().getPlayerByUUID(helper) instanceof ServerPlayer p && p.distanceToSqr(this) < 48 * 48) {
                p.sendSystemMessage(Component.literal("Skylander: \"You saved my wings! Thank you, really - I owe you one!\"").withStyle(ChatFormatting.AQUA));
                ((ServerLevel) level()).sendParticles(ParticleTypes.HEART, getX(), getY() + 2, getZ(), 6, 0.4, 0.3, 0.4, 0);
                getLookControl().setLookAt(p);
                helper = null;
            }
        }
        if (helper != null && tickCount - helpedAt > 2400) helper = null;
    }

    /** Free flight: drifting between points in the sky; now and then gliding down to land by people. */
    private void flyTick() {
        Vec3 pos = position();
        if (landAt == null && random.nextInt(600) == 0) {
            var people = level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(48, 80, 48), m -> m instanceof HumanNpcEntity || m instanceof Villager);
            if (!people.isEmpty()) landAt = people.get(random.nextInt(people.size())).blockPosition().offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2);
        }
        Vec3 dest;
        if (landAt != null) {
            BlockPos ground = level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, landAt);
            dest = Vec3.atBottomCenterOf(ground);
            if (pos.distanceTo(dest) < 1.2 || (onGround() && pos.distanceTo(dest) < 4)) {
                setFlying(false);
                groundTime = 0;
                landAt = null;
                return;
            }
        } else {
            if (flyTarget == null || pos.distanceTo(flyTarget) < 2 || random.nextInt(300) == 0) {
                int surface = level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(getX()), Mth.floor(getZ()));
                double y = Math.max(surface + 18 + random.nextInt(24), 100 + random.nextInt(60));
                flyTarget = new Vec3(getX() + random.nextGaussian() * 20, Math.min(y, 230), getZ() + random.nextGaussian() * 20);
            }
            dest = flyTarget;
        }
        Vec3 to = dest.subtract(pos);
        double speed = landAt != null ? 0.32 : 0.22;
        Vec3 want = to.length() > speed ? to.normalize().scale(speed) : to;
        Vec3 v = getDeltaMovement().lerp(want.add(0, Math.sin(tickCount * 0.1) * 0.02, 0), 0.12);
        setDeltaMovement(v);
        if (v.horizontalDistanceSqr() > 1.0E-4) {
            setYRot((float) (Mth.atan2(v.z, v.x) * Mth.RAD_TO_DEG) - 90F);
            yBodyRot = getYRot();
            yHeadRot = getYRot();
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return !wingless() && super.causeFallDamage(distance, multiplier, source);
    }

    @Override
    public boolean removeWhenFarAway(double dist) {
        return !wingless() && !guardian() && !isPersistenceRequired() && dist > 160 * 160;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Variant", variant());
        tag.putBoolean("Flying", flying());
        tag.putBoolean("Wingless", wingless());
        if (guardHome != null) tag.putLong("GuardHome", guardHome.asLong());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(VARIANT, tag.getInt("Variant"));
        entityData.set(WINGLESS, tag.getBoolean("Wingless"));
        setFlying(tag.getBoolean("Flying"));
        if (tag.contains("GuardHome")) {
            guardHome = BlockPos.of(tag.getLong("GuardHome"));
            restrictTo(guardHome, 12);
        }
        if (wingless()) {
            var speed = getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) speed.setBaseValue(0.2);
        }
    }
}
