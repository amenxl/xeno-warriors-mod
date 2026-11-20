package com.xenowarriors.client;

import com.xenowarriors.bot.PartyBotEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * A flying bot taking you along: you hang from its hand instead of sitting on it (PartyBotEntity.positionRider puts
 * you beside and below it) - your right hand up holding its left, legs dangling and swaying; it reaches down to you
 * with its left hand, its right hand free (a gun still fires). On our model for both (Epic Fight doesn't draw them meanwhile).
 */
public final class HangClient {
    private HangClient() {}

    /** You, hanging from a bot's hand. */
    public static boolean hanging(Entity e) { return e instanceof Player && e.getVehicle() instanceof PartyBotEntity; }

    /** A bot (or its stand-in body) holding someone by the hand. */
    public static boolean holding(Entity e) {
        PartyBotEntity b = e instanceof PartyBotEntity pb ? pb : e instanceof com.xenowarriors.client.bot.BotAvatar a ? a.bot() : null;
        return b != null && b.getFirstPassenger() instanceof Player;
    }

    public static void apply(HumanoidModel<?> m, Player p, float age) {
        if (hanging(p)) {
            float sway = Mth.sin(age * 0.12F);
            m.rightArm.xRot = 0F; m.rightArm.yRot = 0F; m.rightArm.zRot = Mth.PI - 0.32F;        // up, toward it
            m.leftArm.xRot = sway * 0.15F; m.leftArm.yRot = 0F; m.leftArm.zRot = -0.12F;
            m.rightLeg.xRot = sway * 0.22F; m.leftLeg.xRot = -sway * 0.22F;
            m.rightLeg.yRot = 0F; m.leftLeg.yRot = 0F; m.rightLeg.zRot = 0.04F; m.leftLeg.zRot = -0.04F;
            m.body.zRot = sway * 0.05F;
            m.head.xRot = Math.min(m.head.xRot, 0.3F);
            hangs++;
        } else if (holding(p)) {
            m.leftArm.xRot = 0.15F; m.leftArm.yRot = 0F; m.leftArm.zRot = -0.38F;              // down, toward you
            holds++;
        }
    }

    /** (Tests) */
    public static int hangs, holds;
}
