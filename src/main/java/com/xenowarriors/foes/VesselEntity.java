package com.xenowarriors.foes;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A Vessel - the Cynic Dancer (the user's design): a hooded, shadow-black figure with red eyes and claws, passing for a
 * C-soul so the C-souls leave it alone (the strong, smart ones see through it - foes.TypeTriangle). At half health it
 * evolves (five seconds - it can't fight back, and takes more damage): stop it before it's done and it dies (more
 * loot). Evolved, it has white skin and a new outfit, and is far stronger: fast three-hit combos, and grabs - it holds
 * you up and slams you down. They leave Demon Essence.
 */
public class VesselEntity extends PathfinderMob implements Enemy {
    public static final int HOST = 0, FUSING = 1, FUSED = 2, FUSE_TICKS = 100;
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(VesselEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LOOK = SynchedEntityData.defineId(VesselEntity.class, EntityDataSerializers.INT);
    private int fuseTicks, combo, grabCd = 60, grabT, grabbed = -1;
    /** (Tests) evolutions stopped / completed, combo finishers, grabs and slams. */
    public static int stopped, fused, finishers, grabs, slams, comboHits;

    public VesselEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setCustomName(Component.literal("Cynic Dancer").withStyle(ChatFormatting.DARK_RED));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 50).add(Attributes.ATTACK_DAMAGE, 6).add(Attributes.MOVEMENT_SPEED, 0.29)
                .add(Attributes.FOLLOW_RANGE, 32).add(Attributes.ARMOR, 5).add(Attributes.FLYING_SPEED, 0.5);
    }

    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(STATE, HOST); entityData.define(LOOK, 0); }

    public int state() { return entityData.get(STATE); }
    /** Its look: hooded and shadow-black, or (evolved) white-skinned in its new outfit. (LOOK: kept for more kinds of Vessel.) */
    public String look() { return state() == FUSED ? "cynic_evolved" : "cynic"; }
    /** Still passing for a C-soul (not evolved yet). */
    public boolean disguised() { return state() != FUSED; }
    public void setLook(int l) { entityData.set(LOOK, l); }
    /** Client: the fusion's progress 0-1 (wings grow). */
    public float fuseProgress() { return state() == FUSED ? 1F : state() == FUSING ? Math.min(1F, fuseTicks / (float) FUSE_TICKS) : 0F; }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, false) {
            @Override public boolean canUse() { return state() != FUSING && grabbed < 0 && super.canUse(); }
            @Override public boolean canContinueToUse() { return state() != FUSING && grabbed < 0 && super.canContinueToUse(); }
            // evolved: quick three-hit combos, a breath after each finisher
            @Override protected int getAttackInterval() { return state() == FUSED ? adjustedTickDelay(combo % 3 == 0 ? 24 : 7) : super.getAttackInterval(); }
        });
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, p -> !((Player) p).isCreative() && !p.isSpectator()));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, com.xenowarriors.bot.PartyBotEntity.class, 10, true, false, e -> true));
        // (the type triangle: Vessels hunt Spell Casters - magic does little to them)
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, ElementalistEntity.class, 10, true, false, e -> true));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (state() == FUSING) fuseTicks++;
            if (state() != HOST && tickCount % 3 == 0) level().addParticle(state() == FUSING ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SMOKE, getRandomX(0.6), getRandomY(), getRandomZ(0.6), 0, 0.03, 0);
            return;
        }
        ServerLevel l = (ServerLevel) level();
        if (state() == HOST && getHealth() <= getMaxHealth() * 0.5F && isAlive()) startFusing();
        if (state() == FUSING) {
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
            if (++fuseTicks % 10 == 0) {
                l.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 1, getZ(), 14, 0.4, 0.8, 0.4, 0.05);
                l.playSound(null, blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1F, 0.8F + fuseTicks / 200F);
            }
            if (fuseTicks >= FUSE_TICKS) finishFusion(l);
            return;
        }
        if (state() == FUSED) grabTick(l);
    }

    // ------------------------------------------------------------------ evolved: combos and grabs

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity t) {
        if (state() != FUSED) return super.doHurtTarget(t);
        combo++;
        comboHits++;
        boolean finisher = combo % 3 == 0;
        if (t instanceof LivingEntity v && combo % 3 != 1) v.invulnerableTime = 0;   // (a combo: each hit lands)
        var dmg = getAttribute(Attributes.ATTACK_DAMAGE);
        double base = dmg.getBaseValue();
        dmg.setBaseValue(base * (finisher ? 1.6 : 0.7));                       // (light, light, heavy)
        boolean hit;
        try { hit = super.doHurtTarget(t); } finally { dmg.setBaseValue(base); }
        if (hit && t instanceof LivingEntity v && level() instanceof ServerLevel l) {
            swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            if (finisher) {
                finishers++;
                Vec3 d = v.position().subtract(position()).normalize();
                v.push(d.x * 1.2, 0.5, d.z * 1.2);
                v.hurtMarked = true;
                l.sendParticles(ParticleTypes.SWEEP_ATTACK, v.getX(), v.getY() + 1, v.getZ(), 1, 0, 0, 0, 0);
                l.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 1F, 0.8F);
            } else l.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 0.7F, 1.4F);
        }
        return hit;
    }

    private LivingEntity grabbedOne() {
        return grabbed >= 0 && level().getEntity(grabbed) instanceof LivingEntity v && v.isAlive() ? v : null;
    }

    private void grabTick(ServerLevel l) {
        LivingEntity v = grabbedOne();
        if (grabbed >= 0 && v == null) { grabbed = -1; return; }
        if (v != null) {                                                        // holding it up in front of it
            getNavigation().stop();
            Vec3 f = Vec3.directionFromRotation(0, getYRot()).scale(1.1);
            double up = Math.min(1.6, (30 - grabT) * 0.08);
            if (v instanceof net.minecraft.server.level.ServerPlayer sp) sp.connection.teleport(getX() + f.x, getY() + 0.4 + up, getZ() + f.z, sp.getYRot(), sp.getXRot());
            else v.teleportTo(getX() + f.x, getY() + 0.4 + up, getZ() + f.z);
            v.setDeltaMovement(Vec3.ZERO);
            v.fallDistance = 0;
            if (--grabT <= 0) {                                                 // and down into the ground
                grabbed = -1;
                slams++;
                v.hurt(damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.8F);
                v.setDeltaMovement(0, -1.2, 0);
                v.hurtMarked = true;
                l.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, l.getBlockState(v.blockPosition().below())), v.getX(), v.getY(), v.getZ(), 30, 0.6, 0.1, 0.6, 0.1);
                l.playSound(null, v.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.6F, 1.4F);
                grabCd = 120 + random.nextInt(60);
            }
            return;
        }
        LivingEntity t = getTarget();
        if (--grabCd > 0 || t == null || distanceTo(t) > 2.6 || !hasLineOfSight(t) || com.xenowarriors.karma.KarmaSensing.boss(t)
                || t instanceof Player p && (p.isCreative() || p.isSpectator())) return;
        grabbed = t.getId();
        grabT = 30;
        grabs++;
        swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        l.playSound(null, blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 0.8F, 0.6F);
        if (t instanceof Player p) p.displayClientMessage(Component.literal("The Cynic Dancer grabbed you!").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
    }

    /** It lets go if it's hit hard while holding someone. */
    private void hitWhileHolding(float amount) { if (grabbed >= 0 && amount >= 6) { grabbed = -1; grabCd = 80; } }

    private void startFusing() {
        entityData.set(STATE, FUSING);
        fuseTicks = 0;
        setTarget(null);
        for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(24)))
            p.displayClientMessage(Component.literal("The Cynic Dancer is evolving - stop it!").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
    }

    private void finishFusion(ServerLevel l) {
        entityData.set(STATE, FUSED);
        fused++;
        evolvedStats();
        heal(getMaxHealth() * 0.5F);
        l.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
        l.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 1, getZ(), 60, 0.8, 1.2, 0.8, 0.1);
        l.playSound(null, blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.6F, 1.3F);
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(4), e -> e != this)) {   // the burst
            Vec3 d = e.position().subtract(position()).normalize();
            e.push(d.x, 0.4, d.z);
            e.hurtMarked = true;
        }
    }

    private void evolvedStats() {
        setCustomName(Component.literal("Evolved Cynic Dancer").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(9);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.36);
        getAttribute(Attributes.ARMOR).setBaseValue(8);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(70);
    }

    /** Evolving: it can't defend itself (half again the damage). */
    @Override
    public boolean hurt(DamageSource src, float amount) {
        float a = state() == FUSING ? amount * 1.5F : amount;
        if (!level().isClientSide) hitWhileHolding(a);
        return super.hurt(src, a);
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource src, int looting, boolean hit) {
        super.dropCustomDeathLoot(src, looting, hit);
        boolean byUs = src.getEntity() instanceof Player || src.getEntity() instanceof com.xenowarriors.bot.PartyBotEntity;
        if (!byUs) return;
        boolean stop = state() == FUSING;
        if (stop) {
            stopped++;
            spawnAtLocation(new ItemStack(com.xenowarriors.you.YouItems.DEMON_ESSENCE.get(), 1 + random.nextInt(2)));
            spawnAtLocation(new ItemStack(Items.EMERALD, 1 + random.nextInt(3)));
            for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(24)))
                p.displayClientMessage(Component.literal("You stopped it evolving!").withStyle(ChatFormatting.GOLD), true);
        } else if (random.nextFloat() < Foes.ESSENCE_CHANCE + looting * 0.05F) spawnAtLocation(new ItemStack(com.xenowarriors.you.YouItems.DEMON_ESSENCE.get()));
        if (random.nextFloat() < 0.05F) spawnAtLocation(new ItemStack(com.xenowarriors.item.ModItems.SR_HOODIE.get()));
    }

    @Override public int getExperienceReward() { return state() == FUSED ? 25 : 15; }

    @Override
    public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("State", state() == FUSING ? HOST : state()); t.putInt("Look", entityData.get(LOOK)); }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        entityData.set(STATE, t.getInt("State"));
        entityData.set(LOOK, t.getInt("Look"));
        if (state() == FUSED) evolvedStats();
    }

    /** (Tests) */
    public void fuseNowForTest() { if (state() == HOST) startFusing(); fuseTicks = FUSE_TICKS - 1; }
    public void grabSoonForTest() { grabCd = 1; }
    public boolean holding() { return grabbed >= 0; }
}
