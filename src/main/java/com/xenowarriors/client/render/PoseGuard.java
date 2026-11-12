package com.xenowarriors.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Drawing something of another mod's (DragonMineZ's player renderer for a bot, its Flying Nimbus) can throw partway
 * through, leaving the poses it pushed on the stack - and Minecraft then crashes at the end of the frame ("Pose stack
 * not empty"). Remember where the stack was before, and put it back after, whatever happened.
 */
public final class PoseGuard {
    private PoseGuard() {}

    /** Pops back down to this pose (the one that was on top before the call). */
    public static void restore(PoseStack pose, PoseStack.Pose base) {
        for (int i = 0; i < 64 && pose.last() != base && !pose.clear(); i++) pose.popPose();
    }
}
