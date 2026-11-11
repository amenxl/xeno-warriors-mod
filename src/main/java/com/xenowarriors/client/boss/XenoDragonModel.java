package com.xenowarriors.client.boss;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.boss.XenoDragonEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;

/**
 * The Xeno Dragon: a humanoid Ender Dragon - black scales, a horned dragon head with a snout and a jaw that opens,
 * clawed hands and feet, two big membrane wings (two segments each) and a three-part tail with a blade at the end.
 * Posed by what it's doing (breath, wing gust, rising, diving, the Dragon Bomb overhead, powering up, knocked out).
 * (The boxes and the 128x64 texture come from one generator script, so the UV layout always matches.)
 */
public class XenoDragonModel extends HumanoidModel<XenoDragonEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(XenoWarriors.id("xeno_dragon"), "main");
    public final ModelPart jaw, wingR, wingR2, wingL, wingL2, tail1, tail2, tail3;

    public XenoDragonModel(ModelPart root) {
        super(root);
        jaw = head.getChild("jaw");
        wingR = body.getChild("wing_r"); wingR2 = wingR.getChild("wing_r2");
        wingL = body.getChild("wing_l"); wingL2 = wingL.getChild("wing_l2");
        tail1 = body.getChild("tail1"); tail2 = tail1.getChild("tail2"); tail3 = tail2.getChild("tail3");
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(64, 0).addBox(-3.5F, -6F, -4F, 7F, 6F, 7F)
                .texOffs(40, 31).addBox(-2.5F, -4F, -10F, 5F, 3F, 6F)
                .texOffs(74, 49).addBox(-3.5F, -7F, -4F, 2F, 1F, 3F)
                .texOffs(84, 49).addBox(1.5F, -7F, -4F, 2F, 1F, 3F)
                .texOffs(24, 59).addBox(-2F, -5F, -9F, 1F, 1F, 2F)
                .texOffs(30, 59).addBox(1F, -5F, -9F, 1F, 1F, 2F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_jaw = p_head.addOrReplaceChild("jaw", CubeListBuilder.create()
                .texOffs(80, 31).addBox(-2F, 0F, -6F, 4F, 2F, 6F),
                PartPose.offsetAndRotation(0F, -1F, -4F, 0F, 0F, 0F));
        PartDefinition p_horn_r = p_head.addOrReplaceChild("horn_r", CubeListBuilder.create()
                .texOffs(96, 41).addBox(-1F, -4F, -1F, 2F, 4F, 2F),
                PartPose.offsetAndRotation(-2.5F, -5.5F, 0.5F, -1F, 0F, -0.3F));
        PartDefinition p_horn_r2 = p_horn_r.addOrReplaceChild("horn_r2", CubeListBuilder.create()
                .texOffs(50, 49).addBox(-0.5F, -4F, -0.5F, 1F, 4F, 1F),
                PartPose.offsetAndRotation(0F, -4F, 0F, -0.55F, 0F, 0F));
        PartDefinition p_horn_l = p_head.addOrReplaceChild("horn_l", CubeListBuilder.create()
                .texOffs(104, 41).addBox(-1F, -4F, -1F, 2F, 4F, 2F),
                PartPose.offsetAndRotation(2.5F, -5.5F, 0.5F, -1F, 0F, 0.3F));
        PartDefinition p_horn_l2 = p_horn_l.addOrReplaceChild("horn_l2", CubeListBuilder.create()
                .texOffs(54, 49).addBox(-0.5F, -4F, -0.5F, 1F, 4F, 1F),
                PartPose.offsetAndRotation(0F, -4F, 0F, -0.55F, 0F, 0F));
        PartDefinition p_hat = root.addOrReplaceChild("hat", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-5F, 0F, -3F, 10F, 12F, 6F)
                .texOffs(28, 18).addBox(-3.5F, 1F, -4F, 7F, 10F, 1F)
                .texOffs(0, 49).addBox(-2.5F, -2F, -2F, 5F, 2F, 4F)
                .texOffs(94, 49).addBox(-0.5F, 0F, 3F, 1F, 2F, 2F)
                .texOffs(100, 49).addBox(-0.5F, 3F, 3F, 1F, 2F, 2F)
                .texOffs(106, 49).addBox(-0.5F, 6F, 3F, 1F, 2F, 2F)
                .texOffs(112, 49).addBox(-0.5F, 9F, 3F, 1F, 2F, 2F),
                PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_right_arm = root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(32, 0).addBox(-3F, -2F, -2F, 4F, 11F, 4F)
                .texOffs(100, 31).addBox(-3.5F, -2.5F, -2.5F, 5F, 3F, 5F)
                .texOffs(58, 49).addBox(-1.5F, 4F, 2F, 1F, 2F, 3F)
                .texOffs(18, 49).addBox(-3F, 9F, -2F, 4F, 2F, 4F)
                .texOffs(36, 59).addBox(-3F, 11F, -2F, 1F, 2F, 1F)
                .texOffs(40, 59).addBox(-1.5F, 11F, -2F, 1F, 2F, 1F)
                .texOffs(44, 59).addBox(0F, 11F, -2F, 1F, 2F, 1F),
                PartPose.offsetAndRotation(-6F, 2F, 0F, 0F, 0F, 0F));
        PartDefinition p_left_arm = root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(48, 0).addBox(-1F, -2F, -2F, 4F, 11F, 4F)
                .texOffs(0, 41).addBox(-1.5F, -2.5F, -2.5F, 5F, 3F, 5F)
                .texOffs(66, 49).addBox(0.5F, 4F, 2F, 1F, 2F, 3F)
                .texOffs(34, 49).addBox(-1F, 9F, -2F, 4F, 2F, 4F)
                .texOffs(48, 59).addBox(2F, 11F, -2F, 1F, 2F, 1F)
                .texOffs(52, 59).addBox(0.5F, 11F, -2F, 1F, 2F, 1F)
                .texOffs(56, 59).addBox(-1F, 11F, -2F, 1F, 2F, 1F),
                PartPose.offsetAndRotation(6F, 2F, 0F, 0F, 0F, 0F));
        PartDefinition p_right_leg = root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(44, 18).addBox(-2.5F, 0F, -2.5F, 5F, 6F, 5F)
                .texOffs(20, 41).addBox(-2F, 6F, -1.5F, 4F, 4F, 4F)
                .texOffs(36, 41).addBox(-2.5F, 10F, -4F, 5F, 2F, 6F)
                .texOffs(72, 59).addBox(-2.5F, 11F, -5F, 1F, 1F, 1F)
                .texOffs(76, 59).addBox(-0.5F, 11F, -5F, 1F, 1F, 1F)
                .texOffs(80, 59).addBox(1.5F, 11F, -5F, 1F, 1F, 1F)
                .texOffs(60, 59).addBox(-0.5F, 8F, 2F, 1F, 1F, 2F),
                PartPose.offsetAndRotation(-2.6F, 12F, 0F, 0F, 0F, 0F));
        PartDefinition p_left_leg = root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(64, 18).addBox(-2.5F, 0F, -2.5F, 5F, 6F, 5F)
                .texOffs(58, 41).addBox(-2F, 6F, -1.5F, 4F, 4F, 4F)
                .texOffs(74, 41).addBox(-2.5F, 10F, -4F, 5F, 2F, 6F)
                .texOffs(84, 59).addBox(1.5F, 11F, -5F, 1F, 1F, 1F)
                .texOffs(88, 59).addBox(-0.5F, 11F, -5F, 1F, 1F, 1F)
                .texOffs(92, 59).addBox(-2.5F, 11F, -5F, 1F, 1F, 1F)
                .texOffs(66, 59).addBox(-0.5F, 8F, 2F, 1F, 1F, 2F),
                PartPose.offsetAndRotation(2.6F, 12F, 0F, 0F, 0F, 0F));
        PartDefinition p_wing_r = p_body.addOrReplaceChild("wing_r", CubeListBuilder.create()
                .texOffs(0, 55).addBox(-14F, -1F, -1F, 14F, 2F, 2F)
                .texOffs(92, 0).addBox(-14F, 1F, 0F, 14F, 13F, 0F),
                PartPose.offsetAndRotation(-2.5F, 1.5F, 3F, 0F, 0.6F, 0.25F));
        PartDefinition p_wing_r2 = p_wing_r.addOrReplaceChild("wing_r2", CubeListBuilder.create()
                .texOffs(32, 55).addBox(-10F, -1F, -1F, 10F, 2F, 2F)
                .texOffs(106, 18).addBox(-10F, 1F, 0F, 10F, 10F, 0F)
                .texOffs(96, 59).addBox(-11F, -1F, -0.5F, 1F, 1F, 1F),
                PartPose.offsetAndRotation(-14F, 0F, 0F, 0F, 0.5F, 0F));
        PartDefinition p_wing_l = p_body.addOrReplaceChild("wing_l", CubeListBuilder.create()
                .texOffs(56, 55).addBox(0F, -1F, -1F, 14F, 2F, 2F)
                .texOffs(0, 18).addBox(0F, 1F, 0F, 14F, 13F, 0F),
                PartPose.offsetAndRotation(2.5F, 1.5F, 3F, 0F, -0.6F, -0.25F));
        PartDefinition p_wing_l2 = p_wing_l.addOrReplaceChild("wing_l2", CubeListBuilder.create()
                .texOffs(88, 55).addBox(0F, -1F, -1F, 10F, 2F, 2F)
                .texOffs(0, 31).addBox(0F, 1F, 0F, 10F, 10F, 0F)
                .texOffs(100, 59).addBox(10F, -1F, -0.5F, 1F, 1F, 1F),
                PartPose.offsetAndRotation(14F, 0F, 0F, 0F, -0.5F, 0F));
        PartDefinition p_tail1 = p_body.addOrReplaceChild("tail1", CubeListBuilder.create()
                .texOffs(84, 18).addBox(-2F, -2F, 0F, 4F, 4F, 7F)
                .texOffs(112, 55).addBox(-0.5F, -3.5F, 2F, 1F, 2F, 2F),
                PartPose.offsetAndRotation(0F, 10.5F, 2.5F, -0.55F, 0F, 0F));
        PartDefinition p_tail2 = p_tail1.addOrReplaceChild("tail2", CubeListBuilder.create()
                .texOffs(20, 31).addBox(-1.5F, -1.5F, 0F, 3F, 3F, 7F)
                .texOffs(118, 55).addBox(-0.5F, -2.8F, 2F, 1F, 2F, 2F),
                PartPose.offsetAndRotation(0F, 0F, 6.5F, 0.25F, 0F, 0F));
        PartDefinition p_tail3 = p_tail2.addOrReplaceChild("tail3", CubeListBuilder.create()
                .texOffs(62, 31).addBox(-1F, -1F, 0F, 2F, 2F, 7F)
                .texOffs(0, 59).addBox(-0.5F, -2F, 4F, 1F, 1F, 3F)
                .texOffs(8, 59).addBox(-2.5F, -0.5F, 4F, 5F, 1F, 3F),
                PartPose.offsetAndRotation(0F, 0F, 6.5F, 0.2F, 0F, 0F));
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public void setupAnim(XenoDragonEntity e, float limbSwing, float limbAmount, float age, float yaw, float pitch) {
        super.setupAnim(e, limbSwing, limbAmount, age, yaw, pitch);
        head.y = -1.5F;
        rightArm.x = -6F; leftArm.x = 6F; rightLeg.x = -2.6F; leftLeg.x = 2.6F;
        int act = e.act(), t = e.actTicks();
        float flap = Mth.sin(age * 0.09F) * 0.08F;
        // wings at rest: half folded behind, breathing slowly
        float wy = 0.75F, wz = 0.2F + flap, w2 = 0.9F;
        boolean flying = act == XenoDragonEntity.ACT_EMERGE || act == XenoDragonEntity.ACT_RISE || act == XenoDragonEntity.ACT_DIVE
                || act == XenoDragonEntity.ACT_BOMB || act == XenoDragonEntity.ACT_THROW || !e.onGround();
        if (flying) { float f = Mth.sin(age * 0.55F); wy = 0.25F; wz = 0.15F + f * 0.55F; w2 = 0.25F + f * 0.2F; }
        jaw.xRot = 0.08F + Mth.sin(age * 0.05F) * 0.03F;
        tail1.yRot = Mth.sin(age * 0.08F) * 0.22F; tail2.yRot = Mth.sin(age * 0.08F - 0.8F) * 0.28F; tail3.yRot = Mth.sin(age * 0.08F - 1.6F) * 0.32F;
        tail1.xRot = -0.55F + limbAmount * 0.15F;
        if (e.ko() > 0) {
            rightArm.xRot = -0.2F; leftArm.xRot = -0.1F; rightArm.zRot = 0.6F; leftArm.zRot = -0.5F;
            head.xRot = 0.3F; head.yRot = 0.5F; jaw.xRot = 0.3F; wy = 1.0F; wz = -0.3F; w2 = 1.2F;
        } else if (act == XenoDragonEntity.ACT_POWER || act == XenoDragonEntity.ACT_GOD || act == XenoDragonEntity.ACT_AWAKEN) {
            float s = Mth.sin(age * 2.5F) * 0.05F;                              // fists clenched, roaring at the sky, wings thrown wide
            rightArm.xRot = -0.25F + s; leftArm.xRot = -0.25F - s; rightArm.zRot = 0.55F; leftArm.zRot = -0.55F;
            rightArm.yRot = 0; leftArm.yRot = 0;
            head.xRot = -0.6F + s; jaw.xRot = 0.65F;
            wy = -0.1F; wz = 0.45F + s; w2 = 0.1F;
        } else if (act == XenoDragonEntity.ACT_BREATH) {
            boolean rear = t > XenoDragonEntity.BREATH_FIRE;                      // rears back, then breathes forward
            head.xRot = rear ? -0.7F : 0.25F; jaw.xRot = rear ? 0.35F : 0.8F;
            rightArm.xRot = -0.4F; leftArm.xRot = -0.4F;
        } else if (act == XenoDragonEntity.ACT_GUST) {
            boolean wind = t > XenoDragonEntity.GUST_FIRE;                         // wings pulled back, then slammed forward
            wy = wind ? 1.2F : -0.55F; wz = wind ? 0.6F : 0.05F; w2 = wind ? 0.6F : -0.1F;
            rightArm.zRot = 0.4F; leftArm.zRot = -0.4F;
        } else if (act == XenoDragonEntity.ACT_SHOOT) {
            head.xRot = Math.min(head.xRot, -0.1F); jaw.xRot = 0.7F;
            rightArm.xRot = -1.4F + head.xRot; rightArm.yRot = -0.3F;
        } else if (act == XenoDragonEntity.ACT_BOMB || act == XenoDragonEntity.ACT_CRYSTAL) {
            rightArm.xRot = -3.0F; leftArm.xRot = -3.0F; rightArm.zRot = 0.2F; leftArm.zRot = -0.2F;   // both hands up, holding it
            rightArm.yRot = 0; leftArm.yRot = 0; head.xRot = -0.5F;
        } else if (act == XenoDragonEntity.ACT_THROW) {
            rightArm.xRot = -1.6F; leftArm.xRot = -1.6F; rightArm.yRot = -0.2F; leftArm.yRot = 0.2F;
        } else if (act == XenoDragonEntity.ACT_DIVE) {
            head.xRot = 0.6F; rightArm.xRot = -2.6F; leftArm.xRot = -2.6F; rightLeg.xRot = 0.4F; leftLeg.xRot = 0.4F;
            wy = 1.3F; wz = 0.1F; w2 = 0.8F;
        } else if (act == XenoDragonEntity.ACT_STUN) {
            head.xRot = 0.5F; rightArm.xRot = 0.2F; leftArm.xRot = 0.2F; wy = 1.0F; wz = -0.2F;
        } else if (act == XenoDragonEntity.ACT_COMBO) {
            tail1.yRot = Mth.sin(t * 0.6F) * 0.9F;                                 // the tail whips round with the finisher
        }
        wingR.yRot = wy; wingR.zRot = wz; wingR2.yRot = w2;
        wingL.yRot = -wy; wingL.zRot = -wz; wingL2.yRot = -w2;
        hat.visible = false;
    }
}
