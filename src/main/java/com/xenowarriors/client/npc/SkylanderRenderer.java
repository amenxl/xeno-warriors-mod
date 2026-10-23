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

    /** 0 = hovering, 1 = flying flat out. */
    static float speed(SkylanderEntity e) {
        return (float) Math.min(1.0, e.getDeltaMovement().horizontalDistance() / 0.25);
    }

    @Override
    protected void prepare(SkylanderEntity e, DetailModel m, float age) {
        if (!e.flying()) {
            m.setupWings(!e.wingless(), false, age, 0F);
            return;
        }
        double vy = e.getDeltaMovement().y;
        if (vy < -0.06) {
            m.setupWings(true, false, age, 1F);                                   // gliding down: wings spread wide and still
        } else {
            // beating: faster and harder climbing or hovering, slower cruising
            float rate = vy > 0.04 ? 1.6F : 1.25F - 0.35F * speed(e);
            m.setupWings(true, true, age * rate, 0F);
        }
    }

    /** No two flocks alike: every Skylander's wings differ in size, color and set (picked from its UUID). */
    private static final float[][] TINTS = {
            {1F, 1F, 1F}, {0.78F, 0.9F, 1F}, {1F, 0.93F, 0.66F}, {1F, 0.8F, 0.88F}, {0.72F, 0.74F, 0.8F},
            {0.84F, 0.74F, 1F}, {0.4F, 0.4F, 0.48F}, {0.8F, 1F, 0.86F}, {1F, 0.78F, 0.62F}};
    private static final float[] SIZES = {0.82F, 0.92F, 1F, 1.1F, 1.22F, 1.35F};

    @Override
    protected float[] wingLook(SkylanderEntity e) {
        if (e.wingless()) return null;
        long h = e.getUUID().getLeastSignificantBits() ^ (e.getUUID().getMostSignificantBits() * 31);
        int a = (int) Math.floorMod(h, 997L);
        float[] t = TINTS[a % TINTS.length];
        float size = SIZES[(a / 9) % SIZES.length];
        float spread = ((a / 54) % 5 - 2) * 0.06F;
        float sweep = ((a / 270) % 3 - 1) * 0.12F;
        // style: 1 in 9 keep the classic wings, the rest one of the styles (guardians: grand seraph wings)
        int style = (a / 811 + a * 7) % 9 - 1;
        if (e.guardian()) { size = Math.max(size, 1.15F); t = TINTS[2]; style = 6; }
        if (style == 6 || style == 0) size = Math.min(size, 1.15F);           // (the big styles don't need extra size)
        return new float[]{size, t[0], t[1], t[2], spread, sweep, style};
    }

    @Override
    protected String faceSuffix(SkylanderEntity e) {
        return e.wingless() ? "_face_sad" : "_face";
    }

    @Override
    protected void setupRotations(SkylanderEntity e, PoseStack pose, float bob, float yaw, float partialTick) {
        super.setupRotations(e, pose, bob, yaw, partialTick);
        if (!e.flying()) return;
        float sp = speed(e);
        double vy = e.getDeltaMovement().y;
        // rising and falling with each wingbeat while hovering; smoother when cruising
        pose.translate(0, Mth.sin(bob * 0.35F * 1.25F) * 0.09F * (1F - sp * 0.6F), 0);
        // pitched forward into the flight (flat out at speed, nose down when gliding down)
        float pitch = 70F * sp + (vy < -0.06 ? 15F : 0F) - (vy > 0.06 ? 10F : 0F);
        // banking into turns
        float turn = Mth.wrapDegrees(e.yBodyRot - e.yBodyRotO);
        float roll = Mth.clamp(turn * 4F, -30F, 30F) * sp;
        pose.translate(0, 0.9F, 0);
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(pitch));
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(roll));
        pose.translate(0, -0.9F, 0);
    }

    /** Flying: hovering upright with legs dangling, or stretched out flat with arms swept back; wingless: head hung. */
    static class Model extends PlayerModel<SkylanderEntity> {
        Model(net.minecraft.client.model.geom.ModelPart root) { super(root, false); }

        @Override
        public void setupAnim(SkylanderEntity e, float limbSwing, float limbSwingAmount, float age, float yaw, float pitch) {
            super.setupAnim(e, limbSwing, limbSwingAmount, age, yaw, pitch);
            if (e.flying()) {
                float sp = speed(e);
                float beat = Mth.sin(age * 0.35F * 1.25F);
                // hover: legs hanging and swaying a little behind the wingbeat, arms loose and out
                float hrl = 0.12F + beat * 0.08F, hll = -0.05F - beat * 0.08F;
                float hra = -0.15F + beat * 0.06F, hza = 0.45F + beat * 0.08F;
                // cruising: stretched out, legs together trailing, arms swept back along the body
                float flutter = Mth.sin(age * 0.5F) * 0.06F;
                rightLeg.xRot = Mth.lerp(sp, hrl, 0.15F + flutter);
                leftLeg.xRot = Mth.lerp(sp, hll, 0.15F - flutter);
                rightLeg.zRot = Mth.lerp(sp, 0.06F, 0.02F);
                leftLeg.zRot = Mth.lerp(sp, -0.06F, -0.02F);
                rightLeg.yRot = 0; leftLeg.yRot = 0;
                rightArm.xRot = Mth.lerp(sp, hra, 0.35F);
                leftArm.xRot = Mth.lerp(sp, hra, 0.35F);
                rightArm.zRot = Mth.lerp(sp, hza, 0.12F);
                leftArm.zRot = Mth.lerp(sp, -hza, -0.12F);
                rightArm.yRot = 0; leftArm.yRot = 0;
                head.xRot -= 1.05F * sp;                                  // still looking where they're going
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
