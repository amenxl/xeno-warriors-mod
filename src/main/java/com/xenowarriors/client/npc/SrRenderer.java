package com.xenowarriors.client.npc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.client.render.DetailModel;
import com.xenowarriors.sr.SrMemberEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/** SR members: their Skylander wings only show while they've summoned them to fly - dark, tattered wings, beating. */
public class SrRenderer extends NpcRenderer<SrMemberEntity> {
    private static final String STYLE = "tattered";

    public SrRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), SrMemberEntity::look, e -> false);
        addLayer(new Wings(this));
    }

    @Override
    protected void setupRotations(SrMemberEntity e, PoseStack pose, float bob, float yaw, float pt) {
        super.setupRotations(e, pose, bob, yaw, pt);
        if (!e.wings()) return;
        float sp = (float) Math.min(1.0, e.getDeltaMovement().horizontalDistance() / 0.3);
        pose.translate(0, net.minecraft.util.Mth.sin(bob * 0.45F) * 0.08F, 0);
        pose.translate(0, 0.9F, 0);
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(55F * sp));
        pose.translate(0, -0.9F, 0);
    }

    private static class Wings extends RenderLayer<SrMemberEntity, PlayerModel<SrMemberEntity>> {
        Wings(RenderLayerParent<SrMemberEntity, PlayerModel<SrMemberEntity>> p) { super(p); }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, SrMemberEntity e, float limb, float limbAmt, float pt, float age, float yaw, float pitch) {
            if (!e.wings() || e.isInvisible()) return;
            DetailModel m = NpcRenderer.model("wing_" + STYLE);
            if (m == null || m.rightWing == null || m.leftWing == null) return;
            m.setupWings(true, true, age * 1.3F, 0F);
            pose.pushPose();
            getParentModel().body.translateAndRotate(pose);
            var vc = buffers.getBuffer(RenderType.entityCutoutNoCull(NpcRenderer.tex("wing_" + STYLE + "_details")));
            int overlay = LivingEntityRenderer.getOverlayCoords(e, 0F);
            float r = e.demon() ? 0.55F : 0.3F, g = 0.26F, b = e.demon() ? 0.3F : 0.34F;     // dark wings (reddish once the demon's awake)
            m.rightWing.render(pose, vc, light, overlay, r, g, b, 1F);
            m.leftWing.render(pose, vc, light, overlay, r, g, b, 1F);
            pose.popPose();
        }
    }
}
