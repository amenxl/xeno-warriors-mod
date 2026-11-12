package com.xenowarriors.client.foes;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.xenowarriors.client.npc.NpcRenderer;
import com.xenowarriors.foes.SwordstressEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * The Ancient Swordstress: its giant sword is part of its right arm's model (tools/xw/npcs/foes.py), so its poses are
 * the arm's - held out in front, raised for the leap, driven down into the ground when stuck, straight up over its head
 * while it spins it like a helicopter's blades (the whole body turning with it).
 */
public class SwordstressRenderer extends NpcRenderer<SwordstressEntity> {
    public SwordstressRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Posed(ctx.bakeLayer(ModelLayers.PLAYER), false), new Posed(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true),
                SwordstressEntity::look, e -> false);
    }

    @Override protected boolean glow(SwordstressEntity e) { return true; }

    @Override
    protected void setupRotations(SwordstressEntity e, PoseStack pose, float age, float yaw, float pt) {
        super.setupRotations(e, pose, age, yaw, pt);
        int s = e.state();
        if (s == SwordstressEntity.SPIN || s == SwordstressEntity.CARRY) pose.mulPose(Axis.YP.rotationDegrees((age * 47F) % 360F));
    }

    /** (Tests, shots) the pose an arm takes in a state. */
    static class Posed extends PlayerModel<SwordstressEntity> {
        Posed(ModelPart root, boolean slim) { super(root, slim); }

        @Override
        public void setupAnim(SwordstressEntity e, float limb, float limbAmt, float age, float yawHead, float pitch) {
            super.setupAnim(e, limb, limbAmt, age, yawHead, pitch);
            float swing = attackTime;                                     // (the slow swing: overhead, then down)
            switch (e.state()) {
                case SwordstressEntity.LEAP -> { rightArm.xRot = -2.7F; leftArm.xRot = -2.5F; rightArm.zRot = 0; }
                case SwordstressEntity.STUCK -> {
                    rightArm.xRot = -0.55F; leftArm.xRot = -0.65F; rightArm.yRot = -0.3F; leftArm.yRot = 0.5F;
                    body.xRot = 0.35F; head.xRot += 0.3F;
                    rightLeg.z += 0F;
                }
                case SwordstressEntity.SPIN, SwordstressEntity.CARRY -> { rightArm.xRot = (float) -Math.PI; rightArm.zRot = 0; leftArm.xRot = -0.6F; }
                default -> {
                    if (swing > 0) rightArm.xRot = -2.6F + swing * 3.0F;
                    else rightArm.xRot = -0.35F + rightArm.xRot * 0.2F;     // held out in front
                }
            }
            hat.copyFrom(head); rightSleeve.copyFrom(rightArm); leftSleeve.copyFrom(leftArm); jacket.copyFrom(body);
        }
    }
}
