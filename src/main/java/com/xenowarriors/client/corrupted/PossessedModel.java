package com.xenowarriors.client.corrupted;

import com.xenowarriors.corrupted.CorruptedVillagerEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Possessed people move like players (no zombie arms), with their own attacks - a right hook, a left hook and a
 * two-handed overhead slam - and a flinch when they're hit.
 */
public class PossessedModel extends PlayerModel<CorruptedVillagerEntity> {
    public PossessedModel(ModelPart root) {
        super(root, false);
    }

    @Override
    public void setupAnim(CorruptedVillagerEntity e, float limbSwing, float limbSwingAmount, float age, float yaw, float pitch) {
        super.setupAnim(e, limbSwing, limbSwingAmount, age, yaw, pitch);
        // a slight forward hunch while chasing, like someone running at you
        if (e.isSprinting()) {
            body.xRot += 0.15F;
            head.xRot -= 0.1F;
        }
        float attack = attackTime;
        if (attack > 0F) {
            float s = Mth.sin(attack * (float) Math.PI);
            switch (e.attackStyle()) {
                case CorruptedVillagerEntity.SLAM -> {
                    // both arms up over the head, then brought crashing down
                    float up = attack < 0.4F ? attack / 0.4F : 1F - (attack - 0.4F) / 0.6F;
                    float arm = -2.9F * up + (attack > 0.4F ? 1.2F * (attack - 0.4F) / 0.6F : 0F);
                    rightArm.xRot = arm;
                    leftArm.xRot = arm;
                    rightArm.zRot = 0.15F;
                    leftArm.zRot = -0.15F;
                    body.xRot += attack > 0.4F ? 0.45F * s : -0.15F * up;
                }
                case CorruptedVillagerEntity.LEFT -> {
                    leftArm.xRot = -1.5F * s - 0.2F;
                    leftArm.yRot = 0.4F * s;
                    body.yRot = 0.35F * s;
                    rightArm.xRot = -0.6F;
                }
                default -> {
                    rightArm.xRot = -1.5F * s - 0.2F;
                    rightArm.yRot = -0.4F * s;
                    body.yRot = -0.35F * s;
                    leftArm.xRot = -0.6F;
                }
            }
            copyOuter();
        }
        // a lurching, uneven walk and sudden head twitches - something else is driving the body
        body.zRot = Mth.sin(limbSwing * 0.3331F) * 0.12F * limbSwingAmount;
        float twitchPhase = (age + e.getId() * 13) % 70F;
        if (twitchPhase < 4F) {
            float k = Mth.sin(twitchPhase / 4F * Mth.PI);
            head.zRot = (e.getId() % 2 == 0 ? 0.55F : -0.55F) * k;
            head.yRot += 0.3F * k;
        } else head.zRot = 0F;
        copyOuter();
        // getting hit: head snaps back, body rocks, arms fly out
        if (e.hurtTime > 0) {
            float h = e.hurtTime / (float) Math.max(1, e.hurtDuration);
            float k = Mth.sin(h * (float) Math.PI);
            head.xRot -= 0.5F * k;
            body.xRot -= 0.25F * k;
            rightArm.zRot += 0.6F * k;
            leftArm.zRot -= 0.6F * k;
            rightArm.xRot += 0.3F * k;
            leftArm.xRot += 0.3F * k;
            copyOuter();
        }
    }

    private void copyOuter() {
        hat.copyFrom(head);
        jacket.copyFrom(body);
        leftSleeve.copyFrom(leftArm);
        rightSleeve.copyFrom(rightArm);
    }
}
