package com.xenowarriors.client;

import com.xenowarriors.entity.HekubladeShardEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/** Riding a Hekublade like a hoverboard: standing sideways-wide on the blade, knees loose, arms out for balance. */
public final class SurfPose {
    private SurfPose() {}

    public static void apply(HumanoidModel<?> m, Player p, float age) {
        if (!(p.getVehicle() instanceof HekubladeShardEntity blade) || !blade.surfing()) return;
        float sway = Mth.sin(age * 0.25F) * 0.08F;
        m.rightLeg.xRot = -0.15F;
        m.leftLeg.xRot = 0.15F;
        m.rightLeg.yRot = 0.25F;
        m.leftLeg.yRot = -0.25F;
        m.rightLeg.zRot = 0.22F;
        m.leftLeg.zRot = -0.22F;
        m.rightArm.xRot = -0.25F + sway;
        m.leftArm.xRot = 0.2F - sway;
        m.rightArm.yRot = 0F;
        m.leftArm.yRot = 0F;
        m.rightArm.zRot = 1.15F + sway;
        m.leftArm.zRot = -1.0F + sway;
        m.body.xRot = 0.2F;
        m.head.xRot = Math.max(m.head.xRot, -0.3F);
        m.hat.copyFrom(m.head);
    }
}
