package com.xenowarriors.fox;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * When Mikiko has been standing still for a while, his fox gets bored and happy at the same time:
 * it runs laps around him, hopping, head tilted, little hearts popping up.
 */
public class FoxPlayGoal extends Goal {
    private final Fox fox;
    private Player owner;
    private double angle;
    private int dir, time;

    public FoxPlayGoal(Fox fox) {
        this.fox = fox;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    private boolean ok(Player p) {
        return p != null && p.isAlive() && !p.isSpectator() && p.level() == fox.level() && FoxPets.isPlaying(p)
                && fox.getTarget() == null && !fox.isLeashed() && fox.distanceToSqr(p) < 16 * 16;
    }

    @Override
    public boolean canUse() {
        Player p = FoxPets.owner(fox);
        if (!ok(p)) return false;
        owner = p;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return ok(owner);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        FoxPets.wake(fox);
        angle = Math.atan2(fox.getZ() - owner.getZ(), fox.getX() - owner.getX());
        dir = fox.getRandom().nextBoolean() ? 1 : -1;
        time = 0;
        ((com.xenowarriors.mixin.FoxAccessor) fox).xenowarriors$setInterested(true);
    }

    @Override
    public void stop() {
        ((com.xenowarriors.mixin.FoxAccessor) fox).xenowarriors$setInterested(false);
        fox.getNavigation().stop();
        owner = null;
    }

    @Override
    public void tick() {
        time++;
        var r = fox.getRandom();
        double radius = 2.3 + 0.6 * Math.sin(time * 0.045);
        double dist = Math.sqrt(fox.distanceToSqr(owner.getX(), fox.getY(), owner.getZ()));
        // keep the lap going: aim a bit ahead of where the fox is on the circle
        if (dist < radius + 1.5) angle += dir * 0.11;
        else angle = Math.atan2(fox.getZ() - owner.getZ(), fox.getX() - owner.getX());
        if (time % 4 == 0) {
            double lead = angle + dir * 0.6;
            fox.getNavigation().moveTo(owner.getX() + Math.cos(lead) * radius, owner.getY(),
                    owner.getZ() + Math.sin(lead) * radius, 1.4);
        }
        if (r.nextInt(240) == 0) dir = -dir;   // spin around the other way now and then
        if (fox.onGround() && r.nextInt(12) == 0) fox.getJumpControl().jump();
        if (time % 50 < 18) fox.getLookControl().setLookAt(owner, 30F, 30F);

        if (fox.level() instanceof ServerLevel level) {
            if (time % 22 == 0) {
                level.sendParticles(ParticleTypes.HEART, fox.getX(), fox.getY() + 0.85, fox.getZ(), 1, 0.15, 0.1, 0.15, 0);
            }
            if (r.nextInt(110) == 0) {
                fox.playSound(SoundEvents.FOX_AMBIENT, 0.7F, 1.15F + r.nextFloat() * 0.25F);
            }
        }
    }
}
