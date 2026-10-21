package com.xenowarriors.fox;

import com.xenowarriors.mixin.FoxAccessor;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * Keeping Mikiko company: when he stands around for a bit the fox sits down next to him, and when he goes
 * to bed it curls up and sleeps at his side.
 */
public class FoxRestGoal extends Goal {
    private final Fox fox;
    private Player owner;
    private boolean sleep;
    private int time;

    public FoxRestGoal(Fox fox) {
        this.fox = fox;
        setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        Player p = FoxPets.owner(fox);
        if (p == null || fox.getTarget() != null || fox.isLeashed() || !fox.onGround()) return false;
        double d = fox.distanceToSqr(p);
        if (p.isSleeping() && d < 16 * 16) {
            sleep = true;
        } else if (FoxPets.stillTicks(p) >= 200 && !FoxPets.isPlaying(p) && d < 6 * 6) {
            sleep = false;
        } else {
            return false;
        }
        owner = p;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (owner == null || !owner.isAlive() || owner.level() != fox.level() || fox.getTarget() != null) return false;
        return sleep ? owner.isSleeping() : FoxPets.stillTicks(owner) > 0 && !FoxPets.isPlaying(owner) && fox.distanceToSqr(owner) < 8 * 8;
    }

    @Override
    public void start() {
        time = 0;
    }

    @Override
    public void stop() {
        FoxPets.wake(fox);
        owner = null;
    }

    @Override
    public void tick() {
        time++;
        FoxAccessor a = (FoxAccessor) fox;
        if (sleep) {
            if (fox.distanceToSqr(owner) > 1.8 * 1.8) {
                if (time % 10 == 1) fox.getNavigation().moveTo(owner, 1.0);
            } else {
                fox.getNavigation().stop();
                if (!fox.isSleeping()) a.xenowarriors$setSleeping(true);
            }
        } else {
            fox.getNavigation().stop();
            if (!fox.isSitting()) a.xenowarriors$setSitting(true);
            if (time % 80 < 30) fox.getLookControl().setLookAt(owner, 20F, 20F);
        }
    }
}
