package com.xenowarriors.foes;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Ancient Swordstress (the user's design): a C-soul with a human shape - a green cat-eared hood, a white bob, a
 * shadow face with glowing green ring eyes - and a giant crystal sword. It swings slow but hits very hard. It leaps at
 * you and smashes the sword into the ground (hitting everything around where it lands) - and sometimes the sword gets
 * stuck: for a few seconds it can't fight back, and takes far more damage. Now and then it spins the sword over its
 * head like a helicopter's blades, flies to you, grabs you, carries you up and slams you back into the ground.
 * As a C-soul it's strong and smart enough to see through a Cynic Dancer's disguise (see TypeTriangle).
 */
public class SwordstressEntity extends Monster {
    public static final int WALK = 0, LEAP = 1, STUCK = 2, SPIN = 3, CARRY = 4;
    public static final int STUCK_TICKS = 70;
    public static final float STUCK_DAMAGE = 1.75F, STUCK_CHANCE = 0.4F;
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(SwordstressEntity.class, EntityDataSerializers.INT);
    private int stateT, leapCd = 60, spinCd = 160, held = -1;
    /** (Tests) a fixed stuck roll (null = random); leaps, smashes, stuck swords, spins, slams. */
    public static Boolean testStuck;
    public static int leaps, smashes, stucks, spins, slams;

    public SwordstressEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        setCustomName(Component.literal("Ancient Swordstress").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD));
        xpReward = 30;
        arm();
    }

    /** Its greatsword in hand (it used to be part of its arm's model); it only drops now and then (see the loot). */
    private void arm() {
        if (!getMainHandItem().is(com.xenowarriors.item.ModItems.SWORDSTRESS_GREATSWORD.get()))
            setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(com.xenowarriors.item.ModItems.SWORDSTRESS_GREATSWORD.get()));
        setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0F);
    }

    /** The greatsword drop: rare (one in twenty, a bit more with Looting). */
    public static final float SWORD_DROP = 0.05F;
    public static int swordDrops;

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 90).add(Attributes.ATTACK_DAMAGE, 13).add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.FOLLOW_RANGE, 32).add(Attributes.ARMOR, 6).add(Attributes.KNOCKBACK_RESISTANCE, 0.6).add(Attributes.ATTACK_KNOCKBACK, 1.2);
    }

    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(STATE, WALK); }

    public int state() { return entityData.get(STATE); }
    private void set(int s) { entityData.set(STATE, s); stateT = 0; }
    public String look() { return "swordstress"; }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true) {
            @Override public boolean canUse() { return state() == WALK && super.canUse(); }
            @Override public boolean canContinueToUse() { return state() == WALK && super.canContinueToUse(); }
            @Override protected int getAttackInterval() { return adjustedTickDelay(44); }   // (a slow, heavy swing)
        });
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, p -> !((Player) p).isCreative() && !p.isSpectator()));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, com.xenowarriors.bot.PartyBotEntity.class, 10, true, false, e -> true));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, VesselEntity.class, 10, true, false, e -> true));   // (it sees through them)
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (state() == SPIN && tickCount % 2 == 0) level().addParticle(ParticleTypes.HAPPY_VILLAGER, getRandomX(1.4), getY() + 2.4, getRandomZ(1.4), 0, 0, 0);
            if (state() == STUCK && tickCount % 4 == 0) level().addParticle(ParticleTypes.CRIT, getRandomX(1), getY() + 0.3, getRandomZ(1), 0, 0.1, 0);
            return;
        }
        ServerLevel l = (ServerLevel) level();
        stateT++;
        LivingEntity t = getTarget();
        switch (state()) {
            case WALK -> {
                setNoGravity(false);
                if (t == null) return;
                double d = distanceTo(t);
                if (--spinCd <= 0 && (d > 9 || t.getY() > getY() + 3) && d < 28) startSpin(l);
                else if (--leapCd <= 0 && onGround() && d > 3.5 && d < 13 && hasLineOfSight(t)) leap(l, t);
            }
            case LEAP -> {
                if (stateT > 4 && (onGround() || stateT > 60)) smash(l);
            }
            case STUCK -> {
                getNavigation().stop();
                setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
                if (stateT >= STUCK_TICKS) {
                    set(WALK);
                    l.playSound(null, blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 0.6F, 1.6F);
                }
            }
            case SPIN -> spin(l, t);
            case CARRY -> carry(l);
            default -> set(WALK);
        }
    }

    // ------------------------------------------------------------------ the leap and the smash

    private void leap(ServerLevel l, LivingEntity t) {
        leaps++;
        set(LEAP);
        getNavigation().stop();
        Vec3 d = t.position().subtract(position());
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        setDeltaMovement(d.x / h * Math.min(1.3, h * 0.11), 0.75, d.z / h * Math.min(1.3, h * 0.11));
        hasImpulse = true;
        lookAt(t, 360, 360);
        swing(InteractionHand.MAIN_HAND);
        FoeAnims.swing(this, new String[]{"wom:biped/combat/solar_quemadura"});   // (the leap: a burning overhead)
        l.playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.5F, 1.6F);
    }

    private void smash(ServerLevel l) {
        smashes++;
        leapCd = 90 + random.nextInt(50);
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.1F;
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3.5, 1.5, 3.5), e -> e != this && e.isAlive() && !(e instanceof SwordstressEntity))) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            e.hurt(damageSources().mobAttack(this), dmg);
            Vec3 d = e.position().subtract(position()).normalize();
            e.push(d.x * 0.6, 0.55, d.z * 0.6);
            e.hurtMarked = true;
        }
        Vec3 f = Vec3.directionFromRotation(0, getYRot()).scale(1.6);
        l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, l.getBlockState(blockPosition().below())), getX() + f.x, getY() + 0.1, getZ() + f.z, 50, 1.2, 0.1, 1.2, 0.15);
        l.sendParticles(ParticleTypes.EXPLOSION, getX() + f.x, getY() + 0.3, getZ() + f.z, 1, 0, 0, 0, 0);
        com.xenowarriors.compat.Effeks.play(l, com.xenowarriors.compat.Effeks.ROCK, position().add(f.x, 0.1, f.z), 1.0F, getYRot());   // (AAA Particles: the ground breaks)
        l.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.8F, 1.2F);
        boolean stuck = testStuck != null ? testStuck : random.nextFloat() < STUCK_CHANCE;
        if (stuck) {
            stucks++;
            set(STUCK);
            l.playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.7F, 0.6F);
            for (Player p : l.getEntitiesOfClass(Player.class, getBoundingBox().inflate(24)))
                p.displayClientMessage(Component.literal("Its sword is stuck in the ground - attack now!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
        } else set(WALK);
    }

    // ------------------------------------------------------------------ the helicopter spin and the grab

    private void startSpin(ServerLevel l) {
        spins++;
        set(SPIN);
        getNavigation().stop();
        l.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1F, 0.5F);
    }

    private void spin(ServerLevel l, LivingEntity t) {
        setNoGravity(true);
        fallDistance = 0;
        if (stateT % 6 == 0) l.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 0.6F, 0.6F + stateT * 0.005F);
        if (t == null || !t.isAlive() || stateT > 100) { endSpin(); return; }
        Vec3 to = t.position().add(0, stateT < 15 ? 3.5 : 0.6, 0).subtract(position());
        setDeltaMovement(getDeltaMovement().scale(0.5).add(to.normalize().scale(0.32)));
        getLookControl().setLookAt(t);
        setYRot((float) (Math.atan2(to.z, to.x) * 180 / Math.PI) - 90F);
        if (stateT > 10 && distanceTo(t) < 2.0) {
            boolean canHold = !com.xenowarriors.karma.KarmaSensing.boss(t) && !(t instanceof Player p && (p.isCreative() || p.isSpectator()));
            if (!canHold) { t.hurt(damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE)); endSpin(); return; }
            held = t.getId();
            set(CARRY);
            if (t instanceof Player p) p.displayClientMessage(Component.literal("The Ancient Swordstress grabbed you!").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD), true);
        }
    }

    private void endSpin() { setNoGravity(false); spinCd = 220 + random.nextInt(100); set(WALK); }

    private void carry(ServerLevel l) {
        LivingEntity v = held >= 0 && l.getEntity(held) instanceof LivingEntity e && e.isAlive() ? e : null;
        if (v == null) { held = -1; endSpin(); return; }
        setNoGravity(true);
        if (stateT <= 20) setDeltaMovement(0, 0.22, 0);                         // up...
        else setDeltaMovement(0, -1.4, 0);                                       // ...and straight back down
        Vec3 f = Vec3.directionFromRotation(0, getYRot()).scale(0.9);
        double x = getX() + f.x, y = getY() - 0.6, z = getZ() + f.z;
        if (v instanceof ServerPlayer sp) sp.connection.teleport(x, y, z, sp.getYRot(), sp.getXRot());
        else v.teleportTo(x, y, z);
        v.setDeltaMovement(Vec3.ZERO);
        v.fallDistance = 0;
        if (stateT > 20 && (onGround() || v.onGround() || stateT > 60)) {
            slams++;
            held = -1;
            v.hurt(damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.6F);
            v.setDeltaMovement(0, 0.3, 0);
            v.hurtMarked = true;
            l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, l.getBlockState(v.blockPosition().below())), v.getX(), v.getY(), v.getZ(), 40, 0.8, 0.1, 0.8, 0.15);
            l.playSound(null, v.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.8F, 1.0F);
            endSpin();
        }
    }

    // ------------------------------------------------------------------

    /** Its sword stuck in the ground: it can't defend itself. */
    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (state() == STUCK) {
            amount *= STUCK_DAMAGE;
            if (level() instanceof ServerLevel l) l.sendParticles(ParticleTypes.CRIT, getX(), getY() + 1.2, getZ(), 10, 0.3, 0.4, 0.3, 0.2);
        }
        return super.hurt(src, amount);
    }

    @Override
    public boolean doHurtTarget(Entity t) {
        FoeAnims.swing(this, FoeAnims.SOLAR);                                    // (with Epic Fight: the Aatrox greatsword's combo)
        boolean hit = super.doHurtTarget(t);
        if (hit && level() instanceof ServerLevel l) {
            l.sendParticles(ParticleTypes.SWEEP_ATTACK, t.getX(), t.getY() + 1, t.getZ(), 1, 0, 0, 0, 0);
            l.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1F, 0.6F);
        }
        return hit;
    }

    @Override public boolean causeFallDamage(float d, float m, DamageSource s) { return false; }

    @Override
    protected void dropCustomDeathLoot(DamageSource src, int looting, boolean hit) {
        super.dropCustomDeathLoot(src, looting, hit);
        if (!(src.getEntity() instanceof Player) && !(src.getEntity() instanceof com.xenowarriors.bot.PartyBotEntity)) return;
        spawnAtLocation(new ItemStack(Items.EMERALD, 2 + random.nextInt(3 + looting)));
        if (random.nextInt(3) == 0) spawnAtLocation(new ItemStack(Items.DIAMOND));
        if (random.nextFloat() < SWORD_DROP + looting * 0.02F) {
            spawnAtLocation(new ItemStack(com.xenowarriors.item.ModItems.SWORDSTRESS_GREATSWORD.get()));
            swordDrops++;
        }
    }

    @Override protected boolean shouldDespawnInPeaceful() { return true; }

    @Override
    public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); }

    @Override
    public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); set(WALK); arm(); }

    /** (Tests) */
    public void leapNowForTest() { leapCd = 0; }
    public void spinNowForTest() { spinCd = 0; }
    public boolean holding() { return held >= 0; }
    public void stateForTest(int st) { set(st); }
}
