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
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A Vessel: one of the Sky Rebellion's demon hosts (in their hoodie), a demon living inside them - red eyes, wings
 * twitching under the hoodie. At half health the demon fuses with them (five seconds - they can't fight back, and take
 * more damage): stop it before it's done and the demon dies with them (more loot). Fused, they're a demon: darker,
 * winged, flying, faster and harder hitting, throwing Devil Flame. They leave Demon Essence.
 */
public class VesselEntity extends PathfinderMob implements Enemy {
    public static final int HOST = 0, FUSING = 1, FUSED = 2, FUSE_TICKS = 100;
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(VesselEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LOOK = SynchedEntityData.defineId(VesselEntity.class, EntityDataSerializers.INT);
    private int fuseTicks, nextFlame = 60;
    /** (Tests) fusions stopped / completed. */
    public static int stopped, fused;

    public VesselEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setCustomName(Component.literal("Vessel").withStyle(ChatFormatting.DARK_RED));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 50).add(Attributes.ATTACK_DAMAGE, 6).add(Attributes.MOVEMENT_SPEED, 0.29)
                .add(Attributes.FOLLOW_RANGE, 32).add(Attributes.ARMOR, 5).add(Attributes.FLYING_SPEED, 0.5);
    }

    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(STATE, HOST); entityData.define(LOOK, 0); }

    public int state() { return entityData.get(STATE); }
    public String look() { return entityData.get(LOOK) % 2 == 0 ? "sr_1" : "sr_2"; }
    public void setLook(int l) { entityData.set(LOOK, l); }
    /** Client: the fusion's progress 0-1 (wings grow). */
    public float fuseProgress() { return state() == FUSED ? 1F : state() == FUSING ? Math.min(1F, fuseTicks / (float) FUSE_TICKS) : 0F; }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, false) { @Override public boolean canUse() { return state() != FUSING && super.canUse(); } });
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, p -> !((Player) p).isCreative() && !p.isSpectator()));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, com.xenowarriors.bot.PartyBotEntity.class, 10, true, false, e -> true));
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
        if (state() == FUSED) {
            LivingEntity t = getTarget();
            boolean fly = t != null && (t.getY() > getY() + 2.5 || distanceTo(t) > 10);
            setNoGravity(fly);
            if (fly) {
                Vec3 to = t.position().add(0, 1.5, 0).subtract(position());
                setDeltaMovement(getDeltaMovement().scale(0.6).add(to.normalize().scale(0.18)));
                getLookControl().setLookAt(t);
            }
            if (t != null && --nextFlame <= 0 && distanceTo(t) > 4 && hasLineOfSight(t)) {   // Devil Flame
                nextFlame = 70 + random.nextInt(30);
                Vec3 d = t.getEyePosition().subtract(getEyePosition());
                SmallFireball f = new SmallFireball(l, this, d.x, d.y, d.z);
                f.setPos(getX(), getEyeY() - 0.2, getZ());
                l.addFreshEntity(f);
                l.playSound(null, blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 0.8F, 0.6F);
            }
        }
    }

    private void startFusing() {
        entityData.set(STATE, FUSING);
        fuseTicks = 0;
        setTarget(null);
        for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(24)))
            p.displayClientMessage(Component.literal("The Vessel's demon is fusing with it - stop it!").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
    }

    private void finishFusion(ServerLevel l) {
        entityData.set(STATE, FUSED);
        fused++;
        setCustomName(Component.literal("Fused Vessel").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue() * 1.6);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.36);
        heal(getMaxHealth() * 0.4F);
        l.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
        l.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 1, getZ(), 60, 0.8, 1.2, 0.8, 0.1);
        l.playSound(null, blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.6F, 1.3F);
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(4), e -> e != this)) {   // the burst
            Vec3 d = e.position().subtract(position()).normalize();
            e.push(d.x, 0.4, d.z);
            e.hurtMarked = true;
        }
    }

    /** Fusing: it can't defend itself (half again the damage). */
    @Override
    public boolean hurt(DamageSource src, float amount) {
        return super.hurt(src, state() == FUSING ? amount * 1.5F : amount);
    }

    @Override public boolean causeFallDamage(float d, float m, DamageSource s) { return state() != FUSED && super.causeFallDamage(d, m, s); }

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
                p.displayClientMessage(Component.literal("You stopped the fusion - the demon died with its host.").withStyle(ChatFormatting.GOLD), true);
        } else if (random.nextFloat() < Foes.ESSENCE_CHANCE + looting * 0.05F) spawnAtLocation(new ItemStack(com.xenowarriors.you.YouItems.DEMON_ESSENCE.get()));
        if (random.nextFloat() < 0.05F) spawnAtLocation(new ItemStack(com.xenowarriors.item.ModItems.SR_HOODIE.get()));
    }

    @Override public int getExperienceReward() { return state() == FUSED ? 25 : 15; }

    @Override
    public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("State", state() == FUSING ? HOST : state()); t.putInt("Look", entityData.get(LOOK)); }

    @Override
    public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); entityData.set(STATE, t.getInt("State")); entityData.set(LOOK, t.getInt("Look")); }

    /** (Tests) */
    public void fuseNowForTest() { if (state() == HOST) startFusing(); fuseTicks = FUSE_TICKS - 1; }
}
