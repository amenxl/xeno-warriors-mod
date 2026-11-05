package com.xenowarriors.client.boss;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.boss.ZombieSaiyanEntity;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.util.Mth;

/**
 * The Zombie Saiyan's body: a plain humanoid (64x64 skin), posed by what it's doing - zombie arms in its base form,
 * clenched fists and head thrown back powering up, arms flung wide screaming, both hands forward for a ki attack.
 */
public class ZombieSaiyanModel extends HumanoidModel<ZombieSaiyanEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(XenoWarriors.id("zombie_saiyan"), "main");

    public ZombieSaiyanModel(ModelPart root) { super(root); }

    public static LayerDefinition create() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0F);
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(ZombieSaiyanEntity e, float limbSwing, float limbAmount, float age, float yaw, float pitch) {
        super.setupAnim(e, limbSwing, limbAmount, age, yaw, pitch);
        int act = e.act();
        if (e.ko() > 0) {                                                        // limp on the ground
            rightArm.xRot = -0.2F; leftArm.xRot = -0.1F; rightArm.zRot = 0.5F; leftArm.zRot = -0.3F;
            rightLeg.xRot = 0.1F; leftLeg.xRot = -0.15F; head.xRot = 0.3F; head.yRot = 0.6F;
            return;
        }
        if (act == ZombieSaiyanEntity.ACT_POWER) {                               // powering up: fists clenched at the sides, shaking
            float s = Mth.sin(age * 2.5F) * 0.05F;
            rightArm.xRot = -0.25F + s; leftArm.xRot = -0.25F - s;
            rightArm.zRot = 0.55F; leftArm.zRot = -0.55F;
            rightArm.yRot = 0; leftArm.yRot = 0;
            head.xRot = -0.45F + s; body.xRot = 0.05F;
            rightLeg.zRot = 0.12F; leftLeg.zRot = -0.12F; rightLeg.xRot = 0; leftLeg.xRot = 0;
            return;
        }
        if (act == ZombieSaiyanEntity.ACT_SCREAM) {                              // screaming at the sky
            rightArm.xRot = -0.3F; leftArm.xRot = -0.3F;
            rightArm.zRot = 1.1F; leftArm.zRot = -1.1F;
            head.xRot = -0.8F;
            rightLeg.zRot = 0.18F; leftLeg.zRot = -0.18F; rightLeg.xRot = 0; leftLeg.xRot = 0;
            return;
        }
        if (act == ZombieSaiyanEntity.ACT_KI) {                                  // both hands forward
            rightArm.xRot = -1.5F + head.xRot; leftArm.xRot = -1.5F + head.xRot;
            rightArm.yRot = -0.25F; leftArm.yRot = 0.25F;
            rightArm.zRot = 0; leftArm.zRot = 0;
            return;
        }
        if (e.form() == ZombieSaiyanEntity.BASE) AnimationUtils.animateZombieArms(leftArm, rightArm, e.isAggressive(), attackTime, age);
    }
}
