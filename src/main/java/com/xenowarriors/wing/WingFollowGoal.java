package com.xenowarriors.wing;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/** A winged companion (chicken, bee) sticks close to Rhalix and pops over to him if left far behind. */
public class WingFollowGoal extends Goal {
    private final PathfinderMob mob;
    private Player owner;
    private int recalc;

    public WingFollowGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        Player p = WingPets.owner(mob);
        if (p == null || p.isSpectator() || mob.isLeashed() || mob.isPassenger() || com.xenowarriors.party.PetRest.resting(mob) || mob.distanceToSqr(p) < 5 * 5) return false;
        owner = p;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return owner != null && owner.isAlive() && owner.level() == mob.level() && mob.distanceToSqr(owner) > 2.5 * 2.5;
    }

    @Override
    public void start() {
        recalc = 0;
    }

    @Override
    public void stop() {
        owner = null;
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        mob.getLookControl().setLookAt(owner, 10F, mob.getMaxHeadXRot());
        if (--recalc > 0) return;
        recalc = 10;
        if (mob.distanceToSqr(owner) > 20 * 20) WingPets.teleportNear(mob, owner);
        else mob.getNavigation().moveTo(owner, 1.3);
    }
}
