package com.xenowarriors.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.XenoWarriors;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** A cigarette held between his fingers (white paper, a filter, a glowing tip). */
public final class Cigarette {
    private static final ResourceLocation TEXTURE = XenoWarriors.id("textures/entity/character/cigarette.png");
    private static ModelPart body, tip;

    private Cigarette() {}

    private static void build() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-1.3F, 9.4F, -3.6F, 0.6F, 0.6F, 2.6F)
                .texOffs(0, 4).addBox(-1.32F, 9.38F, -1.1F, 0.64F, 0.64F, 0.8F), PartPose.ZERO);
        mesh.getRoot().addOrReplaceChild("tip", CubeListBuilder.create()
                .texOffs(0, 8).addBox(-1.31F, 9.39F, -4.0F, 0.62F, 0.62F, 0.45F), PartPose.ZERO);
        ModelPart root = LayerDefinition.create(mesh, 16, 16).bakeRoot();
        body = root.getChild("body");
        tip = root.getChild("tip");
    }

    /** Between his lips, sticking out a little to one side (during a drag). */
    public static void renderInMouth(ModelPart head, PoseStack pose, MultiBufferSource buffers, int light) {
        if (body == null) build();
        pose.pushPose();
        head.translateAndRotate(pose);
        // move the hand-held cigarette (which sits around (-1, 9.7, -2.3) in arm space) to the mouth, angled slightly
        pose.translate(1.2F / 16F, -1.8F / 16F, -4.4F / 16F);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-12F));
        pose.translate(1F / 16F, -9.7F / 16F, 1.0F / 16F);
        var vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        body.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
        tip.render(pose, vc, 0xF000F0, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    public static void render(ModelPart arm, PoseStack pose, MultiBufferSource buffers, int light) {
        if (body == null) build();
        pose.pushPose();
        arm.translateAndRotate(pose);
        var vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        body.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
        tip.render(pose, vc, 0xF000F0, OverlayTexture.NO_OVERLAY);    // the ember glows
        pose.popPose();
    }
}
