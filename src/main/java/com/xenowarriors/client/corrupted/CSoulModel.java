package com.xenowarriors.client.corrupted;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.corrupted.CSoulBossEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;

/** The C-Soul: a horned skull-like head with a gaping jaw, two long wispy claws and a trailing tail. */
public class CSoulModel extends HierarchicalModel<CSoulBossEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(XenoWarriors.id("c_soul"), "main");
    private final ModelPart root, head, jaw, armL, armR, tail1, tail2, tail3;

    public CSoulModel(ModelPart root) {
        super(RenderType::entityTranslucent);
        this.root = root;
        this.head = root.getChild("head");
        this.jaw = head.getChild("jaw");
        this.armL = head.getChild("arm_l");
        this.armR = head.getChild("arm_r");
        this.tail1 = head.getChild("tail1");
        this.tail2 = tail1.getChild("tail2");
        this.tail3 = tail2.getChild("tail3");
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        PartDefinition head = r.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -4, -4, 8, 8, 8),
                PartPose.offset(0, 11, 0));
        head.addOrReplaceChild("horn_l", CubeListBuilder.create().texOffs(32, 0).addBox(-1, -5, -1, 2, 5, 2),
                PartPose.offsetAndRotation(2.5F, -3.5F, 0, 0, 0, 0.45F));
        head.addOrReplaceChild("horn_r", CubeListBuilder.create().texOffs(32, 0).mirror().addBox(-1, -5, -1, 2, 5, 2),
                PartPose.offsetAndRotation(-2.5F, -3.5F, 0, 0, 0, -0.45F));
        head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 16).addBox(-3.5F, 0, -6, 7, 2, 6),
                PartPose.offset(0, 4, 2));
        head.addOrReplaceChild("arm_l", CubeListBuilder.create().texOffs(48, 0).addBox(-1, 0, -1, 2, 8, 2),
                PartPose.offset(5, 1, -1));
        head.addOrReplaceChild("arm_r", CubeListBuilder.create().texOffs(48, 0).mirror().addBox(-1, 0, -1, 2, 8, 2),
                PartPose.offset(-5, 1, -1));
        PartDefinition t1 = head.addOrReplaceChild("tail1", CubeListBuilder.create().texOffs(0, 24).addBox(-3, -3, 0, 6, 6, 6),
                PartPose.offset(0, 1, 3));
        PartDefinition t2 = t1.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(24, 24).addBox(-2, -2, 0, 4, 4, 5),
                PartPose.offset(0, 0.5F, 5.5F));
        t2.addOrReplaceChild("tail3", CubeListBuilder.create().texOffs(42, 24).addBox(-1, -1, 0, 2, 2, 4),
                PartPose.offset(0, 0.5F, 4.5F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(CSoulBossEntity e, float limbSwing, float limbSwingAmount, float age, float yaw, float pitch) {
        head.yRot = yaw * Mth.DEG_TO_RAD;
        head.xRot = pitch * Mth.DEG_TO_RAD * 0.5F;
        head.y = 11 + Mth.sin(age * 0.08F) * 1.2F;
        jaw.xRot = 0.15F + Math.max(0, Mth.sin(age * 0.11F)) * 0.45F;
        armL.xRot = -0.3F + Mth.sin(age * 0.09F) * 0.35F;
        armR.xRot = -0.3F + Mth.sin(age * 0.09F + 1.6F) * 0.35F;
        armL.zRot = -0.25F - Mth.cos(age * 0.07F) * 0.12F;
        armR.zRot = 0.25F + Mth.cos(age * 0.07F) * 0.12F;
        tail1.yRot = Mth.sin(age * 0.12F) * 0.3F;
        tail2.yRot = Mth.sin(age * 0.12F - 0.8F) * 0.4F;
        tail3.yRot = Mth.sin(age * 0.12F - 1.6F) * 0.5F;
        tail1.xRot = 0.2F + Mth.cos(age * 0.1F) * 0.1F;
        tail2.xRot = Mth.cos(age * 0.1F - 0.8F) * 0.15F;
        tail3.xRot = Mth.cos(age * 0.1F - 1.6F) * 0.2F;
    }
}
