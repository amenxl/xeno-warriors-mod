package com.xenowarriors.corrupted;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.List;

/**
 * A corrupted soul with no body: a drifting, glowing wisp that hunts at night. It would rather have a body -
 * it seeks out animals (and villagers) to possess - but will claw at players that come close.
 * Daylight burns it away.
 */
public class CorruptedSoulEntity extends Monster {
    public CorruptedSoulEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setNoGravity(true);
        this.xpReward = 6;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 14.0)
                .add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new PossessGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true));
        goalSelector.addGoal(6, new WaterAvoidingRandomFlyingGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            if (random.nextInt(2) == 0) {
                level().addParticle(ParticleTypes.WITCH, getRandomX(0.4), getY() + random.nextDouble() * 0.8, getRandomZ(0.4), 0, 0, 0);
            }
            if (random.nextInt(6) == 0) {
                level().addParticle(CorruptedFx.BLACK, getRandomX(0.3), getY() + 0.3, getRandomZ(0.3), 0, 0.02, 0);
            }
            return;
        }
        if (tickCount % 10 == 0 && level().isDay() && level().canSeeSky(blockPosition())) {
            vanish();
        }
    }

    void vanish() {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(CorruptedFx.BLACK, getX(), getY() + 0.4, getZ(), 15, 0.3, 0.3, 0.3, 0.05);
            server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.4, getZ(), 6, 0.2, 0.2, 0.2, 0.01);
        }
        playSound(SoundEvents.SOUL_ESCAPE, 1F, 0.8F);
        discard();
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {}

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.VEX_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.VEX_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SOUL_ESCAPE;
    }

    @Override
    public float getVoicePitch() {
        return 0.6F;
    }

    /** Fly to the nearest body that can be taken over and possess it. */
    static class PossessGoal extends Goal {
        private final CorruptedSoulEntity soul;
        private Mob host;
        private int recheck;

        PossessGoal(CorruptedSoulEntity soul) {
            this.soul = soul;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (--recheck > 0) return false;
            recheck = 40;
            // a player right next to it gets clawed instead
            LivingEntity target = soul.getTarget();
            if (target != null && soul.distanceToSqr(target) < 36) return false;
            List<Mob> bodies = soul.level().getEntitiesOfClass(Mob.class, soul.getBoundingBox().inflate(16),
                    m -> m.isAlive() && ((m instanceof Animal a && CorruptedAnimals.canPossess(a))
                            || (m instanceof Villager && m.getClass() == Villager.class)
                            || (m instanceof Monster mon && m != soul && CorruptedAnimals.canPossess(mon))
                            || (m instanceof net.minecraft.world.entity.PathfinderMob pm && CorruptedAnimals.canPossessOther(pm))));
            host = null;
            double best = Double.MAX_VALUE;
            for (Mob m : bodies) {
                double d = soul.distanceToSqr(m);
                if (d < best) {
                    best = d;
                    host = m;
                }
            }
            return host != null;
        }

        @Override
        public boolean canContinueToUse() {
            return host != null && host.isAlive() && soul.distanceToSqr(host) < 400
                    && !CorruptedAnimals.isCorrupted(host);
        }

        @Override
        public void stop() {
            host = null;
        }

        @Override
        public void tick() {
            soul.getLookControl().setLookAt(host, 30F, 30F);
            soul.getNavigation().moveTo(host.getX(), host.getY() + host.getBbHeight() * 0.6, host.getZ(), 1.3);
            if (soul.distanceToSqr(host.getX(), host.getY() + host.getBbHeight() * 0.5, host.getZ()) < 2.5) {
                boolean took = false;
                if (host instanceof Animal a) {
                    took = CorruptedAnimals.corrupt(a);
                } else if (host instanceof Monster mon) {
                    took = CorruptedAnimals.corrupt(mon);
                } else if (host instanceof net.minecraft.world.entity.PathfinderMob pm && CorruptedAnimals.canPossessOther(pm)) {
                    took = CorruptedAnimals.corruptOther(pm);
                } else if (host instanceof Villager v) {
                    CorruptedVillagerEntity cv = v.convertTo(ModCorrupted.CORRUPTED_VILLAGER.get(), false);
                    if (cv != null) {
                        cv.markWasVillager();
                        cv.setPersistenceRequired();
                        took = true;
                    }
                }
                if (took) {
                    if (soul.level() instanceof ServerLevel server) {
                        server.sendParticles(CorruptedFx.BLACK, host.getX(), host.getY() + host.getBbHeight() * 0.6, host.getZ(),
                                12, 0.3, 0.3, 0.3, 0.02);
                    }
                    soul.playSound(SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.6F, 1.6F);
                    soul.discard();
                }
                host = null;
            }
        }
    }

    @SuppressWarnings("unused")
    private static boolean isBody(PathfinderMob m) {
        return m instanceof Animal || m instanceof Villager;
    }
}
