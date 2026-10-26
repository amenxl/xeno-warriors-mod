package com.xenowarriors.client.corrupted;

import com.xenowarriors.XenoWarriors;
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

/** A hollow-eyed head trailing a wispy three-part tail. */
public class CorruptedSoulModel<T extends net.minecraft.world.entity.Mob> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(XenoWarriors.id("corrupted_soul"), "main");
    private final ModelPart root, head, tail1, tail2, tail3;

    public CorruptedSoulModel(ModelPart root) {
        super(RenderType::entityTranslucent);
        this.root = root;
        this.head = root.getChild("head");
        this.tail1 = head.getChild("tail1");
        this.tail2 = tail1.getChild("tail2");
        this.tail3 = tail2.getChild("tail3");
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        PartDefinition head = r.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -4, -4, 8, 8, 8),
                PartPose.offset(0, 14, 0));
        PartDefinition t1 = head.addOrReplaceChild("tail1", CubeListBuilder.create().texOffs(0, 16).addBox(-3, -3, 0, 6, 6, 6),
                PartPose.offset(0, 1, 3));
        PartDefinition t2 = t1.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(24, 16).addBox(-2, -2, 0, 4, 4, 4),
                PartPose.offset(0, 0.5F, 5));
        t2.addOrReplaceChild("tail3", CubeListBuilder.create().texOffs(40, 16).addBox(-1, -1, 0, 2, 2, 2),
                PartPose.offset(0, 0.5F, 3.5F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(T e, float limbSwing, float limbSwingAmount, float age, float yaw, float pitch) {
        head.yRot = yaw * Mth.DEG_TO_RAD;
        head.xRot = pitch * Mth.DEG_TO_RAD;
        head.y = 14 + Mth.sin(age * 0.15F) * 1.5F;
        tail1.yRot = Mth.sin(age * 0.2F) * 0.35F;
        tail2.yRot = Mth.sin(age * 0.2F - 0.8F) * 0.45F;
        tail3.yRot = Mth.sin(age * 0.2F - 1.6F) * 0.55F;
        tail1.xRot = 0.15F + Mth.cos(age * 0.13F) * 0.1F;
        tail2.xRot = Mth.cos(age * 0.13F - 0.8F) * 0.15F;
        tail3.xRot = Mth.cos(age * 0.13F - 1.6F) * 0.2F;
        // leaning into the chase, tail streaming out behind
        float move = Math.min(1F, limbSwingAmount * 1.6F);
        head.xRot += 0.35F * move;
        tail1.xRot -= 0.25F * move; tail2.xRot -= 0.15F * move;
        tail1.yRot *= 1F + move; tail2.yRot *= 1F + move;
        // lunge: rears back then snaps forward
        if (attackTime > 0F) {
            float a = attackTime, s = Mth.sin(a * Mth.PI);
            head.xRot += a < 0.35F ? -0.6F * (a / 0.35F) : 0.9F * s;
            head.z = -3F * s;
        } else head.z = 0F;
        // shudders when hit
        if (e.hurtTime > 0) {
            float k = e.hurtTime / (float) Math.max(1, e.hurtDuration);
            head.zRot = Mth.sin(age * 3.5F) * 0.3F * k;
            head.xRot -= 0.4F * k;
        } else head.zRot = 0F;
        // casting an element: rears up, tail coiled
        int act = com.xenowarriors.client.EntityPoses.action(e);
        if (act >= 10 && act <= 16) {
            float t = com.xenowarriors.client.EntityPoses.actionTime(e, 0F), w = Mth.sin(Math.min(1F, t) * Mth.PI);
            head.xRot -= 0.7F * w;
            head.y -= 2.5F * w;
            tail1.xRot += 0.6F * w; tail2.xRot += 0.5F * w; tail3.xRot += 0.4F * w;
        }
    }
}
