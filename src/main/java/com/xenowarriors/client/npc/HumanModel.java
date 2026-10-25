package com.xenowarriors.client.npc;

import com.xenowarriors.npc.HumanNpcEntity;
import com.xenowarriors.npc.HumanReactions;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** Townsfolk's bodies, with their reactions on top of walking: cowering, awe, dancing, rain, waving, hands up, cheering. */
public class HumanModel extends PlayerModel<HumanNpcEntity> {
    public HumanModel(ModelPart root) {
        super(root, false);
    }

    @Override
    public void setupAnim(HumanNpcEntity e, float limbSwing, float amount, float age, float yaw, float pitch) {
        super.setupAnim(e, limbSwing, amount, age, yaw, pitch);
        switch (e.reaction()) {
            case HumanReactions.COWER -> {          // ducking, arms over the head
                arms(-2.7F, 0.45F); head.xRot = 0.6F; body.xRot = 0.45F;
                rightLeg.xRot = -0.3F; leftLeg.xRot = 0.1F;
            }
            case HumanReactions.AWE -> {            // hands to the face
                arms(-2.1F, -0.55F); rightArm.yRot = -0.6F; leftArm.yRot = 0.6F; head.xRot = -0.2F;
            }
            case HumanReactions.DANCE -> {
                float s = Mth.sin(age * 0.45F), c = Mth.cos(age * 0.45F);
                rightArm.xRot = -1.6F + s * 1.1F; leftArm.xRot = -1.6F - s * 1.1F;
                rightArm.zRot = 0.4F + c * 0.3F; leftArm.zRot = -0.4F + c * 0.3F;
                head.zRot = s * 0.15F; body.yRot = s * 0.2F;
                rightLeg.xRot = Math.max(0F, s) * -0.5F; leftLeg.xRot = Math.max(0F, -s) * -0.5F;
            }
            case HumanReactions.RAIN -> { rightArm.xRot = -2.9F; rightArm.zRot = -0.35F; rightArm.yRot = 0F; }
            case HumanReactions.WAVE -> { rightArm.xRot = -2.7F; rightArm.yRot = 0F; rightArm.zRot = 0.35F + Mth.sin(age * 0.7F) * 0.45F; }
            case HumanReactions.NERVOUS -> { arms(-2.9F, 0.25F); head.xRot = 0.1F; }
            case HumanReactions.CHEER -> {
                float b = Mth.sin(age * 0.6F);
                arms(-2.8F + b * 0.3F, 0.45F);
            }
            default -> { return; }
        }
        hat.copyFrom(head); jacket.copyFrom(body);
        rightSleeve.copyFrom(rightArm); leftSleeve.copyFrom(leftArm);
        rightPants.copyFrom(rightLeg); leftPants.copyFrom(leftLeg);
    }

    private void arms(float x, float z) {
        rightArm.xRot = x; leftArm.xRot = x; rightArm.zRot = z; leftArm.zRot = -z; rightArm.yRot = 0F; leftArm.yRot = 0F;
    }
}
