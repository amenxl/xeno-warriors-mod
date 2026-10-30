package com.xenowarriors.s3;

import com.xenowarriors.character.Seasons;
import com.xenowarriors.corrupted.CSoulBossEntity;
import com.xenowarriors.corrupted.CorruptedSoulEntity;
import com.xenowarriors.corrupted.ModCorrupted;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

import java.util.List;

/**
 * A Distortion (Season 3): a tear in the world that C-souls pour out of. The souls go for bodies - villagers,
 * animals, you. It's shielded while three or more of its souls are alive; thin them out, then break it.
 * Big ones (level 3) release a powerful C-soul. Someone with huge Karma nearby makes them bigger.
 */
public class DistortionEntity extends Mob {
    public static final String TAG = "xw_distortion";
    private static final EntityDataAccessor<Integer> LEVEL = SynchedEntityData.defineId(DistortionEntity.class, EntityDataSerializers.INT);
    private static final DustParticleOptions BLACK = new DustParticleOptions(new Vector3f(0.05F, 0.02F, 0.08F), 2.2F),
            PURPLE = new DustParticleOptions(new Vector3f(0.45F, 0.1F, 0.7F), 1.6F), RED = new DustParticleOptions(new Vector3f(0.8F, 0.1F, 0.2F), 1.2F);
    public static final int LIFETIME = 20 * 60 * 20;
    private boolean released;

    public DistortionEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setPersistenceRequired();
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 150.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(LEVEL, 1); }

    public int size() { return entityData.get(LEVEL); }

    public void setSize(int lvl) {
        entityData.set(LEVEL, Math.max(1, Math.min(3, lvl)));
        var hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) { hp.setBaseValue(100.0 + 60.0 * size()); setHealth(getMaxHealth()); }
    }

    @Override public boolean isPushable() { return false; }
    @Override protected void doPush(net.minecraft.world.entity.Entity e) {}
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public void knockback(double s, double x, double z) {}
    @Override protected boolean shouldDespawnInPeaceful() { return false; }

    private List<CorruptedSoulEntity> souls() {
        return level().getEntitiesOfClass(CorruptedSoulEntity.class, new AABB(blockPosition()).inflate(28), s -> s.getPersistentData().getBoolean(TAG));
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(0, 0, 0);
        int lv = size();
        if (level().isClientSide) {
            double r = 0.9 + 0.35 * lv, t = tickCount * 0.12;
            for (int i = 0; i < 6 + 3 * lv; i++) {                         // a swirling vertical tear
                double a = t + i * 0.7 + random.nextDouble() * 0.3, rr = r * (0.6 + 0.4 * random.nextDouble());
                double x = getX() + Math.cos(a) * rr * 0.45, y = getY() + 2.0 + Math.sin(a) * rr * 1.5, z = getZ() + Math.sin(a * 0.5) * 0.15;
                level().addParticle(i % 3 == 0 ? PURPLE : BLACK, x, y, z, 0, 0, 0);
            }
            if (random.nextInt(3) == 0) level().addParticle(ParticleTypes.PORTAL, getRandomX(0.6), getY() + 1 + random.nextDouble() * 2.5, getRandomZ(0.6), 0, 0.05, 0);
            if (random.nextInt(6) == 0) level().addParticle(RED, getX(), getY() + 2, getZ(), 0, 0, 0);
            return;
        }
        ServerLevel s = (ServerLevel) level();
        if (!Seasons.unlocked(s.getServer(), 3) || ++age > LIFETIME) { discard(); return; }
        if (tickCount % 40 == 0) s.playSound(null, blockPosition(), SoundEvents.PORTAL_AMBIENT, SoundSource.HOSTILE, 0.6F, 0.5F);
        if (tickCount % 100 == 20) {                                      // more C-souls pour out
            List<CorruptedSoulEntity> have = souls();
            int cap = 4 + 2 * lv;
            for (int i = 0; i < lv + 1 && have.size() + i < cap; i++) {
                CorruptedSoulEntity soul = ModCorrupted.CORRUPTED_SOUL.get().create(s);
                if (soul == null) continue;
                soul.moveTo(getX() + random.nextGaussian() * 0.5, getY() + 1.5 + random.nextDouble(), getZ() + random.nextGaussian() * 0.5, random.nextFloat() * 360F, 0);
                soul.getPersistentData().putBoolean(TAG, true);
                soul.finalizeSpawn(s, s.getCurrentDifficultyAt(blockPosition()), MobSpawnType.MOB_SUMMONED, null, null);
                s.addFreshEntity(soul);
                s.sendParticles(BLACK, soul.getX(), soul.getY(), soul.getZ(), 12, 0.3, 0.3, 0.3, 0);
            }
        }
        if (lv >= 3 && !released && age > 600) {                  // the powerful C-soul it was opening for
            released = true;
            CSoulBossEntity boss = ModCorrupted.C_SOUL.get().create(s);
            if (boss != null) {
                boss.moveTo(getX(), getY(), getZ() + 2, 0, 0);
                boss.getPersistentData().putBoolean(TAG, true);
                boss.finalizeSpawn(s, s.getCurrentDifficultyAt(blockPosition()), MobSpawnType.MOB_SUMMONED, null, null);
                s.addFreshEntity(boss);
                s.playSound(null, blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1F, 0.7F);
                for (ServerPlayer p : s.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(48)))
                    p.displayClientMessage(Component.literal("Something powerful crawls out of the Distortion...").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD), true);
            }
        }
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (level().isClientSide) return false;
        if (src.getEntity() == null && !src.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;   // no suffocating/drowning it
        if (souls().size() >= 3 && !src.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            if (src.getEntity() instanceof Player p)
                p.displayClientMessage(Component.literal("Its C-souls shield it - thin them out first").withStyle(ChatFormatting.DARK_PURPLE), true);
            ((ServerLevel) level()).sendParticles(PURPLE, getX(), getY() + 2, getZ(), 10, 0.5, 1, 0.5, 0);
            return false;
        }
        return super.hurt(src, amount);
    }

    @Override
    public void die(DamageSource src) {
        super.die(src);
        if (level() instanceof ServerLevel s) {
            s.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 2, getZ(), 120, 0.8, 1.5, 0.8, 0.3);
            s.sendParticles(BLACK, getX(), getY() + 2, getZ(), 80, 1, 1.5, 1, 0);
            s.playSound(null, blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2F, 0.4F);
            s.playSound(null, blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.HOSTILE, 1F, 1.4F);
            ExperienceOrb.award(s, position(), 40 * size());
            for (CorruptedSoulEntity soul : souls()) { s.sendParticles(BLACK, soul.getX(), soul.getY(), soul.getZ(), 10, .2, .2, .2, 0); soul.discard(); }
            for (ServerPlayer p : s.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(48)))
                p.displayClientMessage(Component.literal("The Distortion closes.").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
        discard();
    }

    private int age;                                                    // (saved: tickCount restarts at every reload)

    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("Size", size()); t.putBoolean("Released", released); t.putInt("Age", age); }
    @Override public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        float hp = getHealth();
        setSize(t.getInt("Size"));
        if (t.contains("Health")) setHealth(Math.min(hp, getMaxHealth()));   // (damage you dealt stays dealt)
        released = t.getBoolean("Released");
        age = t.getInt("Age");
    }
}
