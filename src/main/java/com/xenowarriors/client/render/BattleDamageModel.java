package com.xenowarriors.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xenowarriors.XenoWarriors;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * 3D battle damage on top of the character models: bandages wrapped around a forearm, the forehead
 * (with the loose ends hanging at the back) and a thigh, and ragged strips of torn clothing hanging off
 * the shirt hem, a sleeve, the back and a pant leg. Level 1 (hurt) shows some of it, level 2 (badly hurt) all.
 */
public final class BattleDamageModel {
    public static final ResourceLocation TEXTURE = XenoWarriors.id("textures/entity/character/battle_wraps.png");
    private static final String[] PARTS = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"};
    private final ModelPart root;
    private final ModelPart[] parts = new ModelPart[6];
    private final ModelPart[][] wraps = new ModelPart[6][2], cloth = new ModelPart[6][2];

    private static BattleDamageModel instance;

    public static BattleDamageModel get() {
        if (instance == null) instance = new BattleDamageModel();
        return instance;
    }

    private BattleDamageModel() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        CubeDeformation wrap = new CubeDeformation(0.32F);
        // head: forehead band (level 2) with a knot and two loose tails at the back
        PartDefinition head = r.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        head.addOrReplaceChild("w1", CubeListBuilder.create(), PartPose.ZERO);
        head.addOrReplaceChild("w2", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4F, -7.2F, -4F, 8F, 2F, 8F, new CubeDeformation(0.42F))
                .texOffs(32, 8).addBox(0.5F, -7.2F, 4.3F, 2F, 1.5F, 1F)
                .texOffs(40, 8).addBox(0.8F, -6F, 4.9F, 1F, 4F, 0F)
                .texOffs(44, 8).addBox(1.9F, -6.2F, 4.8F, 1F, 3F, 0F), PartPose.ZERO);
        head.addOrReplaceChild("c1", CubeListBuilder.create(), PartPose.ZERO);
        head.addOrReplaceChild("c2", CubeListBuilder.create(), PartPose.ZERO);
        // body: torn strips hanging off the hem (1), a shredded strip down the back (2)
        PartDefinition body = r.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        body.addOrReplaceChild("w1", CubeListBuilder.create(), PartPose.ZERO);
        body.addOrReplaceChild("w2", CubeListBuilder.create(), PartPose.ZERO);
        body.addOrReplaceChild("c1", CubeListBuilder.create()
                .texOffs(0, 16).addBox(-3.6F, 11.6F, -2.45F, 2F, 3F, 0F)
                .texOffs(8, 16).addBox(1.2F, 11.8F, -2.45F, 2F, 2F, 0F), PartPose.ZERO);
        body.addOrReplaceChild("c2", CubeListBuilder.create()
                .texOffs(24, 16).addBox(-1.5F, 4F, 2.45F, 3F, 8F, 0F)
                .texOffs(32, 16).addBox(2.5F, 11.5F, 2.45F, 1F, 3F, 0F)
                .texOffs(36, 16).addBox(-4.45F, 9F, -1F, 0F, 4F, 2F), PartPose.ZERO);
        // right arm: forearm bandage (1), torn sleeve flap (2)
        PartDefinition ra = r.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.ZERO);
        ra.addOrReplaceChild("w1", CubeListBuilder.create().texOffs(32, 0).addBox(-3F, 5F, -2F, 4F, 3F, 4F, wrap), PartPose.ZERO);
        ra.addOrReplaceChild("w2", CubeListBuilder.create(), PartPose.ZERO);
        ra.addOrReplaceChild("c1", CubeListBuilder.create(), PartPose.ZERO);
        ra.addOrReplaceChild("c2", CubeListBuilder.create().texOffs(44, 16).addBox(-3.3F, 1F, -2.3F, 0F, 4F, 3F), PartPose.ZERO);
        // left arm: a second wrap at the wrist (2)
        PartDefinition la = r.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.ZERO);
        la.addOrReplaceChild("w1", CubeListBuilder.create(), PartPose.ZERO);
        la.addOrReplaceChild("w2", CubeListBuilder.create().texOffs(32, 0).addBox(-1F, 8F, -2F, 4F, 2F, 4F, wrap), PartPose.ZERO);
        la.addOrReplaceChild("c1", CubeListBuilder.create(), PartPose.ZERO);
        la.addOrReplaceChild("c2", CubeListBuilder.create(), PartPose.ZERO);
        // right leg: thigh bandage (2); left leg: ripped pant cuff (1)
        PartDefinition rl = r.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.ZERO);
        rl.addOrReplaceChild("w1", CubeListBuilder.create(), PartPose.ZERO);
        rl.addOrReplaceChild("w2", CubeListBuilder.create().texOffs(48, 0).addBox(-2F, 2F, -2F, 4F, 2F, 4F, wrap), PartPose.ZERO);
        rl.addOrReplaceChild("c1", CubeListBuilder.create(), PartPose.ZERO);
        rl.addOrReplaceChild("c2", CubeListBuilder.create().texOffs(52, 16).addBox(-2.2F, 9F, -2.45F, 2F, 3F, 0F), PartPose.ZERO);
        PartDefinition ll = r.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.ZERO);
        ll.addOrReplaceChild("w1", CubeListBuilder.create(), PartPose.ZERO);
        ll.addOrReplaceChild("w2", CubeListBuilder.create(), PartPose.ZERO);
        ll.addOrReplaceChild("c1", CubeListBuilder.create().texOffs(58, 16).addBox(0.4F, 10.5F, -2.45F, 2F, 2F, 0F), PartPose.ZERO);
        ll.addOrReplaceChild("c2", CubeListBuilder.create(), PartPose.ZERO);

        root = LayerDefinition.create(mesh, 64, 32).bakeRoot();
        for (int i = 0; i < 6; i++) {
            parts[i] = root.getChild(PARTS[i]);
            wraps[i][0] = parts[i].getChild("w1");
            wraps[i][1] = parts[i].getChild("w2");
            cloth[i][0] = parts[i].getChild("c1");
            cloth[i][1] = parts[i].getChild("c2");
        }
    }

    /**
     * Draws the damage for this level, posed like the player model. Bandages are cream; the torn cloth is
     * tinted in the character's clothing color.
     */
    public void render(HumanoidModel<?> pose, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay,
                       int level, int clothColor) {
        if (level <= 0) return;
        ModelPart[] src = {pose.head, pose.body, pose.rightArm, pose.leftArm, pose.rightLeg, pose.leftLeg};
        for (int i = 0; i < 6; i++) parts[i].copyFrom(src[i]);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        // bandages
        show(level, true);
        root.render(poseStack, vc, light, overlay, 1F, 1F, 1F, 1F);
        // torn cloth
        show(level, false);
        float cr = (clothColor >> 16 & 255) / 255F, cg = (clothColor >> 8 & 255) / 255F, cb = (clothColor & 255) / 255F;
        root.render(poseStack, vc, light, overlay, cr, cg, cb, 1F);
    }

    /** Just the torn, hanging strips of cloth (ripped clothes), no bandages. */
    public void renderCloth(HumanoidModel<?> pose, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay,
                            int level, int clothColor) {
        if (level <= 0) return;
        ModelPart[] src = {pose.head, pose.body, pose.rightArm, pose.leftArm, pose.rightLeg, pose.leftLeg};
        for (int i = 0; i < 6; i++) parts[i].copyFrom(src[i]);
        show(level, false);
        float cr = (clothColor >> 16 & 255) / 255F, cg = (clothColor >> 8 & 255) / 255F, cb = (clothColor & 255) / 255F;
        root.render(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, overlay, cr, cg, cb, 1F);
    }

    private void show(int level, boolean bandages) {
        for (int i = 0; i < 6; i++) {
            for (int l = 0; l < 2; l++) {
                boolean on = level > l;
                wraps[i][l].visible = bandages && on;
                cloth[i][l].visible = !bandages && on;
            }
        }
    }
}
