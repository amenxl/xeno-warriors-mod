package com.xenowarriors.entity;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * Walking that turns smoothly: vanilla lets a mob swing round up to 90 degrees in one tick at a path's corner (bots
 * and people snapped round 100-140 degrees mid-stride). At most 35 degrees a tick: a full about-turn takes a quarter
 * of a second.
 */
public class SmoothMoveControl extends MoveControl {
    public static final float MAX_TURN = 35F;

    public SmoothMoveControl(Mob mob) {
        super(mob);
    }

    @Override
    protected float rotlerp(float from, float to, float max) {
        return super.rotlerp(from, to, Math.min(max, MAX_TURN));
    }
}
