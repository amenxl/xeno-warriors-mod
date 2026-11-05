package com.xenowarriors.npc.rebuild;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** A townsperson walking over to the spot VillageRebuild gave them (the placing itself happens there). */
public class RebuildGoal extends Goal {
    private final PathfinderMob m;
    private int repath;

    public RebuildGoal(PathfinderMob m) {
        this.m = m;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() { return VillageRebuild.spotFor(m) != null; }

    @Override
    public boolean canContinueToUse() { return canUse(); }

    @Override
    public void start() { repath = 0; }

    @Override
    public void stop() { m.getNavigation().stop(); }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void tick() {
        BlockPos p = VillageRebuild.spotFor(m);
        if (p == null) return;
        m.getLookControl().setLookAt(p.getX() + .5, p.getY() + .5, p.getZ() + .5);
        if (VillageRebuild.inReach(m, p)) { m.getNavigation().stop(); return; }
        if (--repath <= 0 || m.getNavigation().isDone()) {
            repath = 20;
            m.getNavigation().moveTo(p.getX() + .5, p.getY(), p.getZ() + .5, 0.75);
        }
    }
}
