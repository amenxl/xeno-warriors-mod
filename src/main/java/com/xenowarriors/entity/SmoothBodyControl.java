package com.xenowarriors.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.BodyRotationControl;

/**
 * The body turning smoothly too: vanilla snaps a mob's body straight to the way it's heading the moment it starts
 * walking - after standing with its head (and body) turned to look at something, that was a 70-150 degree jump in one
 * tick (people and bots spun round as they set off). At most MAX_TURN degrees a tick, and the head kept within its
 * usual reach of the body.
 */
public class SmoothBodyControl extends BodyRotationControl {
    public static final float MAX_TURN = 30F;
    private final Mob mob;

    public SmoothBodyControl(Mob mob) {
        super(mob);
        this.mob = mob;
    }

    @Override
    public void clientTick() {
        float before = mob.yBodyRot;
        super.clientTick();
        float d = Mth.wrapDegrees(mob.yBodyRot - before);
        if (Math.abs(d) > MAX_TURN) {
            mob.yBodyRot = before + Math.signum(d) * MAX_TURN;
            mob.yHeadRot = Mth.rotateIfNecessary(mob.yHeadRot, mob.yBodyRot, mob.getMaxHeadYRot());
        }
    }
}
