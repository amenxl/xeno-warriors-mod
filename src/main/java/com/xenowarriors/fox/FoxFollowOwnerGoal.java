package com.xenowarriors.fox;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;

/** A pet fox trots after Mikiko, and pops over to him if it falls far behind. */
public class FoxFollowOwnerGoal extends Goal {
    private final Fox fox;
    private Player owner;
    private int recalc;

    public FoxFollowOwnerGoal(Fox fox) {
        this.fox = fox;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private double startDistance() {
        return fox.getTarget() != null && fox.getTarget().isAlive() ? 14 : 7;
    }

    @Override
    public boolean canUse() {
        Player p = FoxPets.owner(fox);
        if (p == null || p.isSpectator() || fox.isLeashed() || fox.isPassenger() || com.xenowarriors.party.PetRest.resting(fox)) return false;
        double d = startDistance();
        if (fox.distanceToSqr(p) < d * d) return false;
        owner = p;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return owner != null && owner.isAlive() && owner.level() == fox.level() && !owner.isSpectator()
                && fox.distanceToSqr(owner) > 3 * 3;
    }

    @Override
    public void start() {
        FoxPets.wake(fox);
        recalc = 0;
    }

    @Override
    public void stop() {
        owner = null;
        fox.getNavigation().stop();
    }

    @Override
    public void tick() {
        fox.getLookControl().setLookAt(owner, 10F, fox.getMaxHeadXRot());
        if (--recalc > 0) return;
        recalc = 10;
        double d = fox.distanceToSqr(owner);
        if (d > 20 * 20 && owner.onGround()) {
            teleportNear(fox, owner);
        } else {
            fox.getNavigation().moveTo(owner, d > 10 * 10 ? 1.25 : 1.0);
        }
    }

    /** Puts the fox on a safe spot a few blocks from the owner. */
    public static boolean teleportNear(Fox fox, Entity owner) {
        if (com.xenowarriors.party.PetRest.resting(fox)) return false;        // staying behind (party pet limit)
        BlockPos base = owner.blockPosition();
        RandomSource r = fox.getRandom();
        for (int i = 0; i < 16; i++) {
            int dx = r.nextInt(7) - 3, dy = r.nextInt(3) - 1, dz = r.nextInt(7) - 3;
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) continue;
            BlockPos pos = base.offset(dx, dy, dz);
            if (!fox.level().getBlockState(pos.below()).entityCanStandOn(fox.level(), pos.below(), fox)) continue;
            if (!fox.level().getFluidState(pos).isEmpty()) continue;
            AABB box = fox.getBoundingBox().move(pos.getX() + 0.5 - fox.getX(), pos.getY() - fox.getY(), pos.getZ() + 0.5 - fox.getZ());
            if (!fox.level().noCollision(fox, box)) continue;
            fox.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, fox.getYRot(), fox.getXRot());
            fox.getNavigation().stop();
            return true;
        }
        return false;
    }
}
