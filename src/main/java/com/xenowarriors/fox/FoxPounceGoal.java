package com.xenowarriors.fox;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** A tamed fox springs high into the air and comes down on its enemy, biting it on the way in. */
public class FoxPounceGoal extends Goal {
    private final Fox fox;
    private LivingEntity target;
    private int cooldown, airTicks;
    private boolean hit;

    public FoxPounceGoal(Fox fox) {
        this.fox = fox;
        setFlags(EnumSet.of(Flag.JUMP, Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        LivingEntity t = fox.getTarget();
        if (t == null || !t.isAlive() || !fox.onGround() || fox.isInWater() || fox.isPassenger()) return false;
        double d = fox.distanceToSqr(t);
        if (d < 2.5 * 2.5 || d > 7.5 * 7.5 || !fox.getSensing().hasLineOfSight(t)) return false;
        if (!com.xenowarriors.fox.PetKarma.spend(fox, 8F)) return false;      // a pounce costs the fox a little Karma
        target = t;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return airTicks < 30 && (airTicks < 3 || !fox.onGround()) && target.isAlive();
    }

    @Override
    public void start() {
        airTicks = 0;
        hit = false;
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(fox.position());
        Vec3 flat = new Vec3(to.x, 0, to.z);
        double dist = flat.length();
        Vec3 dir = dist < 0.01 ? Vec3.ZERO : flat.scale(1 / dist);
        double speed = Math.min(1.1, 0.25 + dist * 0.12);
        fox.setDeltaMovement(dir.x * speed, 0.78, dir.z * speed);   // about three blocks up
        fox.getNavigation().stop();
        fox.getLookControl().setLookAt(target, 60F, 30F);
        fox.playSound(SoundEvents.FOX_AGGRO, 1F, 1.2F);
        if (fox.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.CLOUD, fox.getX(), fox.getY() + 0.1, fox.getZ(), 6, 0.2, 0.05, 0.2, 0.02);
        }
    }

    @Override
    public void tick() {
        airTicks++;
        fox.getLookControl().setLookAt(target, 60F, 30F);
        if (!hit && fox.getBoundingBox().inflate(0.6).intersects(target.getBoundingBox())) {
            hit = true;
            fox.doHurtTarget(target);
            if (fox.level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.7, target.getZ(),
                        8, 0.25, 0.25, 0.25, 0.1);
            }
        }
    }

    @Override
    public void stop() {
        cooldown = 40 + fox.getRandom().nextInt(50);
        target = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
