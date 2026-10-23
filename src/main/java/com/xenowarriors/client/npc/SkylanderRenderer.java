package com.xenowarriors.client.npc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.client.render.DetailModel;
import com.xenowarriors.npc.SkylanderEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;

/** Skylanders: wings beating in flight, folded on the ground - and gone for good on a Wingless Skylander, who looks it. */
public class SkylanderRenderer extends NpcRenderer<SkylanderEntity> {
    public SkylanderRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model(ctx.bakeLayer(ModelLayers.PLAYER)), SkylanderEntity::look, e -> false);
    }

    @Override
    protected void prepare(SkylanderEntity e, DetailModel m, float age) {
        m.setupWings(!e.wingless(), e.flying(), age, 0F);
    }

    @Override
    protected String faceSuffix(SkylanderEntity e) {
        return e.wingless() ? "_face_sad" : "_face";
    }

    @Override
    protected void setupRotations(SkylanderEntity e, PoseStack pose, float bob, float yaw, float partialTick) {
        super.setupRotations(e, pose, bob, yaw, partialTick);
        if (e.flying()) {
            // leaning into the flight, gently bobbing
            float speed = (float) Math.min(1.0, e.getDeltaMovement().horizontalDistance() * 5.0);
            pose.translate(0, Mth.sin(bob * 0.1F) * 0.06F, 0);
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(35F * speed));
        }
    }

    /** Flying: legs trail behind, arms out; wingless: head hung low, slow drooping walk. */
    static class Model extends PlayerModel<SkylanderEntity> {
        Model(net.minecraft.client.model.geom.ModelPart root) { super(root, false); }

        @Override
        public void setupAnim(SkylanderEntity e, float limbSwing, float limbSwingAmount, float age, float yaw, float pitch) {
            super.setupAnim(e, limbSwing, limbSwingAmount, age, yaw, pitch);
            if (e.flying()) {
                float s = Mth.sin(age * 0.12F);
                rightLeg.xRot = 0.25F + s * 0.08F;
                leftLeg.xRot = 0.15F - s * 0.08F;
                rightArm.xRot = 0.2F; leftArm.xRot = 0.2F;
                rightArm.zRot = 0.35F + s * 0.05F;
                leftArm.zRot = -0.35F - s * 0.05F;
                head.xRot -= 0.3F;
            } else if (e.wingless()) {
                head.xRot = 0.55F;
                rightArm.xRot *= 0.4F; leftArm.xRot *= 0.4F;
                rightArm.zRot = 0.05F; leftArm.zRot = -0.05F;
                body.xRot = 0.12F;
            }
            hat.copyFrom(head); jacket.copyFrom(body);
            rightSleeve.copyFrom(rightArm); leftSleeve.copyFrom(leftArm);
            rightPants.copyFrom(rightLeg); leftPants.copyFrom(leftLeg);
        }
    }
}
